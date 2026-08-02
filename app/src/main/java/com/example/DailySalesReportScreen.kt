package com.example

import android.content.Context
import android.content.Intent
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Logout
import androidx.core.content.FileProvider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.database.HistoryViewModel
import com.example.database.HsdNozzleReading
import com.example.database.MsNozzleReading
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySalesReportScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    historyViewModel: HistoryViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("pump_manager_prefs", Context.MODE_PRIVATE) }

    // Selected Month & Year (0-based for Month, e.g. 0 = January, 6 = July)
    val calendar = Calendar.getInstance()
    var selectedMonth by rememberSaveable { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var selectedYear by rememberSaveable { mutableStateOf(calendar.get(Calendar.YEAR)) }

    // Fetch all nozzle readings from DB to aggregate dynamically
    val allMsReadings by historyViewModel.allMsNozzleReadings.collectAsState(initial = emptyList())
    val allHsdReadings by historyViewModel.allHsdNozzleReadings.collectAsState(initial = emptyList())
    val allAudits by historyViewModel.allAudits.collectAsState(initial = emptyList())

    // Generate days list for the selected month
    val daysInMonthList = remember(selectedMonth, selectedYear) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, selectedYear)
        cal.set(Calendar.MONTH, selectedMonth)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        (1..maxDays).map { day ->
            String.format(Locale.getDefault(), "%02d-%02d-%04d", day, selectedMonth + 1, selectedYear)
        }
    }

    // Build dataset for each day of the month
    val dailyDataList = remember(daysInMonthList, allMsReadings, allHsdReadings, allAudits) {
        daysInMonthList.map { dateStr ->
            // Filter nozzle readings for this specific day
            val msDayReadings = allMsReadings.filter { it.date == dateStr }
            val hsdDayReadings = allHsdReadings.filter { it.date == dateStr }

            // Filter saved audits for this specific day to extract cash totals
            val dayAudits = allAudits.filter { it.date == dateStr }
            val dayCashSubmitted = dayAudits.sumOf { it.cashSubmitted }
            val dayActualCashCollected = dayAudits.sumOf { it.actualCashCollected }
            val dayTotalCashCollected = dayCashSubmitted + dayActualCashCollected

            // Aggregate MS sales & testing
            var msSalesLitres = 0.0
            var msTestingLitres = 0.0
            val msNozzleDetailsList = mutableListOf<NozzleDailyDetail>()

            if (msDayReadings.isNotEmpty()) {
                msDayReadings.groupBy { it.nozzleLabel }.forEach { (label, readings) ->
                    val sorted = readings.sortedBy { it.timestamp }
                    val firstOp = sorted.firstOrNull()?.openingReading ?: 0.0
                    val lastCl = sorted.lastOrNull()?.closingReading ?: 0.0
                    val nozzleTesting = sorted.sumOf { it.testing }
                    val netSales = lastCl - firstOp
                    msSalesLitres += netSales
                    msTestingLitres += nozzleTesting
                    msNozzleDetailsList.add(NozzleDailyDetail(label, "MS", firstOp, lastCl, nozzleTesting, netSales))
                }
            }

            // Aggregate HSD sales & testing
            var hsdSalesLitres = 0.0
            var hsdTestingLitres = 0.0
            val hsdNozzleDetailsList = mutableListOf<NozzleDailyDetail>()

            if (hsdDayReadings.isNotEmpty()) {
                hsdDayReadings.groupBy { it.nozzleLabel }.forEach { (label, readings) ->
                    val sorted = readings.sortedBy { it.timestamp }
                    val firstOp = sorted.firstOrNull()?.openingReading ?: 0.0
                    val lastCl = sorted.lastOrNull()?.closingReading ?: 0.0
                    val nozzleTesting = sorted.sumOf { it.testing }
                    val netSales = lastCl - firstOp
                    hsdSalesLitres += netSales
                    hsdTestingLitres += nozzleTesting
                    hsdNozzleDetailsList.add(NozzleDailyDetail(label, "HSD", firstOp, lastCl, nozzleTesting, netSales))
                }
            }

            // Retrieve Manager configurations from shared preferences
            val rateMsStr = sharedPrefs.getString("rate_ms_$dateStr", "") ?: ""
            val rateHsdStr = sharedPrefs.getString("rate_hsd_$dateStr", "") ?: ""
            val stockMsStr = sharedPrefs.getString("stock_ms_$dateStr", "") ?: ""
            val stockHsdStr = sharedPrefs.getString("stock_hsd_$dateStr", "") ?: ""
            val receiptMsStr = sharedPrefs.getString("receipt_ms_$dateStr", "") ?: ""
            val receiptHsdStr = sharedPrefs.getString("receipt_hsd_$dateStr", "") ?: ""

            val rateMs = rateMsStr.toDoubleOrNull() ?: 0.0
            val rateHsd = rateHsdStr.toDoubleOrNull() ?: 0.0
            val stockMs = stockMsStr.toDoubleOrNull() ?: 0.0
            val stockHsd = stockHsdStr.toDoubleOrNull() ?: 0.0
            val receiptMs = receiptMsStr.toDoubleOrNull() ?: 0.0
            val receiptHsd = receiptHsdStr.toDoubleOrNull() ?: 0.0

            val totalStockMs = stockMs + receiptMs
            val totalStockHsd = stockHsd + receiptHsd

            val totalMsSalesAmount = msSalesLitres * rateMs
            val totalHsdSalesAmount = hsdSalesLitres * rateHsd

            DailySalesReportData(
                date = dateStr,
                msRate = rateMs,
                hsdRate = rateHsd,
                msOpeningStock = stockMs,
                hsdOpeningStock = stockHsd,
                msNewInventory = receiptMs,
                hsdNewInventory = receiptHsd,
                msTotalStock = totalStockMs,
                hsdTotalStock = totalStockHsd,
                msSalesLitres = msSalesLitres,
                hsdSalesLitres = hsdSalesLitres,
                msTestingLitres = msTestingLitres,
                hsdTestingLitres = hsdTestingLitres,
                msSalesAmount = totalMsSalesAmount,
                hsdSalesAmount = totalHsdSalesAmount,
                nozzlesList = (msNozzleDetailsList + hsdNozzleDetailsList).sortedBy { it.label },
                cashSubmitted = dayCashSubmitted,
                actualCashCollected = dayActualCashCollected,
                totalCashCollected = dayTotalCashCollected
            )
        }
    }

    // Monthly summation metrics
    val monthlyMsSalesLitres = dailyDataList.sumOf { it.msSalesLitres }
    val monthlyHsdSalesLitres = dailyDataList.sumOf { it.hsdSalesLitres }
    val monthlyMsSalesAmount = dailyDataList.sumOf { it.msSalesAmount }
    val monthlyHsdSalesAmount = dailyDataList.sumOf { it.hsdSalesAmount }
    val monthlyTotalSalesAmount = monthlyMsSalesAmount + monthlyHsdSalesAmount
    val monthlyCashSubmitted = dailyDataList.sumOf { it.cashSubmitted }
    val monthlyActualCashCollected = dailyDataList.sumOf { it.actualCashCollected }
    val monthlyTotalCashCollected = dailyDataList.sumOf { it.totalCashCollected }

    // Expandable item map for days
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

    val monthNamesEng = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val monthNamesHin = listOf("जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")

    val selectedMonthName = LanguageManager.translate(
        monthNamesEng[selectedMonth],
        monthNamesHin[selectedMonth]
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = LanguageManager.translate("Monthly Daily Sales Report", "मासिक दैनिक बिक्री रिपोर्ट"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "D R INAMDAR PETROLEUM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("sales_report_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            printMonthlySalesReportPdf(
                                context,
                                selectedMonthName,
                                selectedYear,
                                dailyDataList,
                                monthlyMsSalesLitres,
                                monthlyHsdSalesLitres,
                                monthlyMsSalesAmount,
                                monthlyHsdSalesAmount,
                                monthlyTotalSalesAmount,
                                totalCashSubmitted = monthlyCashSubmitted,
                                totalActualCashCollected = monthlyActualCashCollected,
                                totalCashCollected = monthlyTotalCashCollected,
                                isShare = true
                            )
                        },
                        modifier = Modifier.testTag("share_monthly_report")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Report",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // MONTH & YEAR SELECTOR ROW
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedMonth == 0) {
                                    selectedMonth = 11
                                    selectedYear -= 1
                                } else {
                                    selectedMonth -= 1
                                }
                            }
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Month")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Month",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$selectedMonthName $selectedYear",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                if (selectedMonth == 11) {
                                    selectedMonth = 0
                                    selectedYear += 1
                                } else {
                                    selectedMonth += 1
                                }
                            }
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                        }
                    }
                }
            }

            // MONTHLY CUMULATIVE SUMMARY CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = LanguageManager.translate("Monthly Summary Totals", "मासिक कुल सारांश विवरण"),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // MS Summary Column
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = LanguageManager.translate("MS (Petrol)", "एमएस (पेट्रोल)"),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%,.1f", monthlyMsSalesLitres)} L",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "₹${String.format(Locale.getDefault(), "%,.2f", monthlyMsSalesAmount)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // HSD Summary Column
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = LanguageManager.translate("HSD (Diesel)", "एचएसडी (डीजल)"),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF2E7D32)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%,.1f", monthlyHsdSalesLitres)} L",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "₹${String.format(Locale.getDefault(), "%,.2f", monthlyHsdSalesAmount)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = LanguageManager.translate("Grand Total Business Amount", "कुल संचित व्यवसाय मूल्य"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${String.format(Locale.getDefault(), "%,.2f", monthlyTotalSalesAmount)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = LanguageManager.translate("Grand Total Cash Collected", "कुल संचित नकद संग्रह"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${String.format(Locale.getDefault(), "%,.2f", monthlyTotalCashCollected)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // DAYS DAILY RECORDS HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalGasStation,
                        contentDescription = "Fuel Icon",
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = LanguageManager.translate("Daily Ledger Log Book", "दैनिक खाता लॉग बुक"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // INDIVIDUAL DAYS EXPANDABLE CARDS LIST
            items(dailyDataList) { dailyData ->
                val isExpanded = expandedStates[dailyData.date] ?: false

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedStates[dailyData.date] = !isExpanded },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 3.dp else 1.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Days Header (Condensed State)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dailyData.date,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "MS: ${String.format(Locale.getDefault(), "%,.1f", dailyData.msSalesLitres)} L",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "|",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "HSD: ${String.format(Locale.getDefault(), "%,.1f", dailyData.hsdSalesLitres)} L",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₹${String.format(Locale.getDefault(), "%,.2f", dailyData.msSalesAmount + dailyData.hsdSalesAmount)}",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand details",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // Expanded State Details
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                                // Dual Pane (MS on Left, HSD on Right)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // MS Details Panel
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = LanguageManager.translate("MS Petrol (Petrol)", "एमएस पेट्रोल (पेट्रोल)"),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )

                                        DetailRow(LanguageManager.translate("Opening Stock", "प्रारंभिक स्टॉक"), "${String.format(Locale.getDefault(), "%,.1f", dailyData.msOpeningStock)} L")
                                        DetailRow(LanguageManager.translate("New Inventory", "नई इन्वेंट्री"), "${String.format(Locale.getDefault(), "%,.1f", dailyData.msNewInventory)} L")
                                        DetailRow(LanguageManager.translate("Total Stock", "कुल स्टॉक"), "${String.format(Locale.getDefault(), "%,.1f", dailyData.msTotalStock)} L", isBold = true)
                                        DetailRow(LanguageManager.translate("Rate/Litre", "दर/लीटर"), "₹${String.format(Locale.getDefault(), "%,.2f", dailyData.msRate)}")
                                        DetailRow(LanguageManager.translate("Net Sales", "कुल बिक्री"), "${String.format(Locale.getDefault(), "%,.1f", dailyData.msSalesLitres)} L")
                                        DetailRow(LanguageManager.translate("Testing Total", "परीक्षण कुल"), "${String.format(Locale.getDefault(), "%,.1f", dailyData.msTestingLitres)} L", color = Color(0xFFC62828))
                                        DetailRow(LanguageManager.translate("Sales Amount", "बिक्री मूल्य"), "₹${String.format(Locale.getDefault(), "%,.2f", dailyData.msSalesAmount)}", isPrimary = true)
                                    }

                                    // HSD Details Panel
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = LanguageManager.translate("HSD Diesel (Diesel)", "एचएसडी डीजल (डीजल)"),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF2E7D32),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )

                                        DetailRow(LanguageManager.translate("Opening Stock", "प्रारंभिक स्टॉक"), "${String.format(Locale.getDefault(), "%,.1f", dailyData.hsdOpeningStock)} L")
                                        DetailRow(LanguageManager.translate("New Inventory", "नई इन्वेंट्री"), "${String.format(Locale.getDefault(), "%,.1f", dailyData.hsdNewInventory)} L")
                                        DetailRow(LanguageManager.translate("Total Stock", "कुल स्टॉक"), "${String.format(Locale.getDefault(), "%,.1f", dailyData.hsdTotalStock)} L", isBold = true)
                                        DetailRow(LanguageManager.translate("Rate/Litre", "दर/लीटर"), "₹${String.format(Locale.getDefault(), "%,.2f", dailyData.hsdRate)}")
                                        DetailRow(LanguageManager.translate("Net Sales", "कुल बिक्री"), "${String.format(Locale.getDefault(), "%,.1f", dailyData.hsdSalesLitres)} L")
                                        DetailRow(LanguageManager.translate("Testing Total", "परीक्षण कुल"), "${String.format(Locale.getDefault(), "%,.1f", dailyData.hsdTestingLitres)} L", color = Color(0xFFC62828))
                                        DetailRow(LanguageManager.translate("Sales Amount", "बिक्री मूल्य"), "₹${String.format(Locale.getDefault(), "%,.2f", dailyData.hsdSalesAmount)}", isPrimary = true)
                                    }
                                }

                                // Daily Cash Collection Summary
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = LanguageManager.translate("Cash Collection Summary", "नकद संग्रह विवरण"),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        DetailRow(
                                            label = LanguageManager.translate("Cash Submitted (₹)", "जमा किया गया नकद (₹)"),
                                            value = "₹${String.format(Locale.getDefault(), "%,.2f", dailyData.cashSubmitted)}"
                                        )
                                        DetailRow(
                                            label = LanguageManager.translate("Actual Cash Collected (₹)", "वास्तविक नकद संग्रह (₹)"),
                                            value = "₹${String.format(Locale.getDefault(), "%,.2f", dailyData.actualCashCollected)}"
                                        )
                                        Divider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = LanguageManager.translate("Total Cash Collected (₹)", "कुल नकद संग्रह (₹)"),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Text(
                                                text = "₹${String.format(Locale.getDefault(), "%,.2f", dailyData.totalCashCollected)}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                // Nozzle Readings Breakdown (Table style)
                                if (dailyData.nozzlesList.isNotEmpty()) {
                                    Column {
                                        Text(
                                            text = LanguageManager.translate("Nozzle Readings Breakdown", "नोजल रीडिंग विश्लेषण"),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                // Header
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(LanguageManager.translate("Nozzle", "नोजल"), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                                    Text(LanguageManager.translate("Op Reading", "प्रथम"), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                                    Text(LanguageManager.translate("Cl Reading", "अंतिम"), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                                    Text(LanguageManager.translate("Test", "जांच"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                                    Text(LanguageManager.translate("Net L", "नेट"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                                                Spacer(modifier = Modifier.height(4.dp))

                                                dailyData.nozzlesList.forEach { nozzle ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 3.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${nozzle.label} (${nozzle.type})",
                                                            modifier = Modifier.weight(1.5f),
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                            color = if (nozzle.type == "MS") MaterialTheme.colorScheme.primary else Color(0xFF2E7D32)
                                                        )
                                                        Text(
                                                            text = String.format(Locale.getDefault(), "%,.1f", nozzle.opening),
                                                            modifier = Modifier.weight(1.5f),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            textAlign = TextAlign.End
                                                        )
                                                        Text(
                                                            text = String.format(Locale.getDefault(), "%,.1f", nozzle.closing),
                                                            modifier = Modifier.weight(1.5f),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            textAlign = TextAlign.End
                                                        )
                                                        Text(
                                                            text = if (nozzle.testing > 0.0) "${String.format(Locale.getDefault(), "%,.1f", nozzle.testing)}" else "0",
                                                            modifier = Modifier.weight(1f),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = if (nozzle.testing > 0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface,
                                                            textAlign = TextAlign.End
                                                        )
                                                        Text(
                                                            text = "${String.format(Locale.getDefault(), "%,.1f", nozzle.netSales)} L",
                                                            modifier = Modifier.weight(1f),
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                            textAlign = TextAlign.End
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    isPrimary: Boolean = false,
    color: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isBold || isPrimary) FontWeight.Bold else FontWeight.Medium
            ),
            color = color ?: if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

// Data models used specifically for daily report
data class DailySalesReportData(
    val date: String,
    val msRate: Double,
    val hsdRate: Double,
    val msOpeningStock: Double,
    val hsdOpeningStock: Double,
    val msNewInventory: Double,
    val hsdNewInventory: Double,
    val msTotalStock: Double,
    val hsdTotalStock: Double,
    val msSalesLitres: Double,
    val hsdSalesLitres: Double,
    val msTestingLitres: Double,
    val hsdTestingLitres: Double,
    val msSalesAmount: Double,
    val hsdSalesAmount: Double,
    val nozzlesList: List<NozzleDailyDetail>,
    val cashSubmitted: Double = 0.0,
    val actualCashCollected: Double = 0.0,
    val totalCashCollected: Double = 0.0
)

data class NozzleDailyDetail(
    val label: String,
    val type: String, // "MS" or "HSD"
    val opening: Double,
    val closing: Double,
    val testing: Double,
    val netSales: Double
)

private fun printMonthlySalesReportPdf(
    context: Context,
    monthName: String,
    year: Int,
    dailyRecords: List<DailySalesReportData>,
    totalMsLitres: Double,
    totalHsdLitres: Double,
    totalMsAmt: Double,
    totalHsdAmt: Double,
    totalAmt: Double,
    totalCashSubmitted: Double = 0.0,
    totalActualCashCollected: Double = 0.0,
    totalCashCollected: Double = 0.0,
    isShare: Boolean = false
) {
    val msNozzleLabels = dailyRecords.flatMap { rec ->
        rec.nozzlesList.filter { it.type == "MS" }.map { it.label }
    }.distinct().sorted()

    val hsdNozzleLabels = dailyRecords.flatMap { rec ->
        rec.nozzlesList.filter { it.type == "HSD" }.map { it.label }
    }.distinct().sorted()

    val webView = WebView(context)
    val htmlBuilder = StringBuilder()

    val msNozzleHeaderHtml = if (msNozzleLabels.isNotEmpty()) {
        val widthPerNozzle = 25.0 / msNozzleLabels.size
        msNozzleLabels.joinToString("") { label ->
            """<th style="width: ${String.format(Locale.US, "%.2f", widthPerNozzle)}%; text-align: right;">$label (Op)</th>"""
        }
    } else {
        """<th style="width: 25%;">Nozzle Opening Readings</th>"""
    }

    val hsdNozzleHeaderHtml = if (hsdNozzleLabels.isNotEmpty()) {
        val widthPerNozzle = 25.0 / hsdNozzleLabels.size
        hsdNozzleLabels.joinToString("") { label ->
            """<th style="width: ${String.format(Locale.US, "%.2f", widthPerNozzle)}%; text-align: right;">$label (Op)</th>"""
        }
    } else {
        """<th style="width: 25%;">Nozzle Opening Readings</th>"""
    }

    htmlBuilder.append("""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                @page {
                    size: landscape;
                    margin: 4mm 6mm;
                }
                body {
                    font-family: Arial, sans-serif;
                    margin: 0;
                    color: #222;
                    font-size: 8px;
                    line-height: 1.15;
                }
                .page {
                    page-break-after: always;
                }
                .page:last-child {
                    page-break-after: avoid;
                }
                .header {
                    text-align: center;
                    border-bottom: 2px solid #333;
                    padding-bottom: 3px;
                    margin-bottom: 6px;
                }
                .header h1 {
                    margin: 0 0 1px 0;
                    font-size: 11px;
                    text-transform: uppercase;
                    color: #111;
                }
                .header p {
                    margin: 0px 0;
                    font-size: 8px;
                    color: #555;
                }
                .title-sect {
                    font-size: 9px;
                    font-weight: bold;
                    text-transform: uppercase;
                    margin: 3px 0 5px 0;
                    color: #1a5f7a;
                    border-bottom: 2px solid #1a5f7a;
                    padding-bottom: 1px;
                }
                .hsd-title-sect {
                    color: #2d6a4f;
                    border-bottom: 2px solid #2d6a4f;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-bottom: 0px;
                }
                th, td {
                    border: 1px solid #bbb;
                    padding: 2px 3px;
                    text-align: left;
                    vertical-align: middle;
                }
                th {
                    background-color: #f5f5f5;
                    font-weight: bold;
                    color: #111;
                    font-size: 8px;
                }
                .num {
                    text-align: right;
                    font-family: 'Courier New', Courier, monospace;
                    font-size: 8px;
                }
                .ms-col {
                    color: #1a5f7a;
                    background-color: #f7fcfe;
                }
                .hsd-col {
                    color: #2d6a4f;
                    background-color: #f5faf6;
                }
                .nozzle-cell {
                    font-size: 7px;
                    line-height: 1.1;
                }
                .nozzle-item {
                    border-bottom: 1px dashed #ddd;
                    padding: 1px 0;
                }
                .nozzle-item:last-child {
                    border-bottom: none;
                }
                .total-row {
                    font-weight: bold;
                    background-color: #eee;
                }
            </style>
        </head>
        <body>
            <!-- SHEET 1: MS PETROL -->
            <div class="page">
                <div class="header">
                    <h1>D R INAMDAR PETROLEUM</h1>
                    <p>Monthly Daily Sales & Stock Report &mdash; <strong>$monthName $year</strong></p>
                    <p>Generated on: ${SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date())}</p>
                </div>
                
                <div class="title-sect">MS PETROL (PETROL) &mdash; DAILY SALES & STOCK SHEET</div>
                
                <table>
                    <thead>
                        <tr>
                            <th style="width: 8%;">Date</th>
                            <th style="width: 10%; text-align: right;">Op Stock (L)</th>
                            <th style="width: 10%; text-align: right;">Receipts (L)</th>
                            <th style="width: 10%; text-align: right;">Total Stock (L)</th>
                            $msNozzleHeaderHtml
                            <th style="width: 10%; text-align: right;">Sales (L)</th>
                            <th style="width: 8%; text-align: right;">Test (L)</th>
                            <th style="width: 8%; text-align: right;">Rate (₹)</th>
                            <th style="width: 11%; text-align: right;">Amount (₹)</th>
                        </tr>
                    </thead>
                    <tbody>
    """.trimIndent())

    dailyRecords.forEach { rec ->
        val msNozzles = rec.nozzlesList.filter { it.type == "MS" }
        val nozzleCellsHtml = if (msNozzleLabels.isNotEmpty()) {
            msNozzleLabels.joinToString("") { label ->
                val noz = msNozzles.firstOrNull { it.label == label }
                val valueStr = if (noz != null) String.format(Locale.getDefault(), "%,.1f", noz.opening) else "--"
                """<td class="num">$valueStr</td>"""
            }
        } else {
            """<td class="nozzle-cell">--</td>"""
        }

        htmlBuilder.append("""
            <tr>
                <td><strong>${rec.date}</strong></td>
                <td class="num">${if (rec.msOpeningStock > 0) String.format(Locale.getDefault(), "%,.1f", rec.msOpeningStock) else "--"}</td>
                <td class="num">${if (rec.msNewInventory > 0) String.format(Locale.getDefault(), "%,.1f", rec.msNewInventory) else "--"}</td>
                <td class="num">${if (rec.msTotalStock > 0) String.format(Locale.getDefault(), "%,.1f", rec.msTotalStock) else "--"}</td>
                $nozzleCellsHtml
                <td class="num ms-col" style="font-weight:bold;">${if (rec.msSalesLitres > 0) String.format(Locale.getDefault(), "%,.1f", rec.msSalesLitres) else "--"}</td>
                <td class="num" style="color:#c62828;">${if (rec.msTestingLitres > 0) String.format(Locale.getDefault(), "%,.1f", rec.msTestingLitres) else "--"}</td>
                <td class="num">${if (rec.msRate > 0) String.format(Locale.getDefault(), "%,.2f", rec.msRate) else "--"}</td>
                <td class="num ms-col" style="font-weight:bold;">${if (rec.msSalesAmount > 0) "₹" + String.format(Locale.getDefault(), "%,.2f", rec.msSalesAmount) else "--"}</td>
            </tr>
        """.trimIndent())
    }

    val msColSpan = if (msNozzleLabels.isNotEmpty()) 3 + msNozzleLabels.size else 4
    htmlBuilder.append("""
                        <tr class="total-row">
                            <td>TOTALS</td>
                            <td colspan="$msColSpan">&mdash;</td>
                            <td class="num ms-col" style="font-size: 10px;">${String.format(Locale.getDefault(), "%,.1f", totalMsLitres)} L</td>
                            <td>&mdash;</td>
                            <td>&mdash;</td>
                            <td class="num ms-col" style="font-size: 10px; font-weight: bold;">₹${String.format(Locale.getDefault(), "%,.2f", totalMsAmt)}</td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <!-- SHEET 2: HSD DIESEL -->
            <div class="page">
                <div class="header">
                    <h1>D R INAMDAR PETROLEUM</h1>
                    <p>Monthly Daily Sales & Stock Report &mdash; <strong>$monthName $year</strong></p>
                    <p>Generated on: ${SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date())}</p>
                </div>
                
                <div class="title-sect hsd-title-sect">HSD DIESEL (DIESEL) &mdash; DAILY SALES & STOCK SHEET</div>
                
                <table>
                    <thead>
                        <tr>
                            <th style="width: 8%;">Date</th>
                            <th style="width: 10%; text-align: right;">Op Stock (L)</th>
                            <th style="width: 10%; text-align: right;">Receipts (L)</th>
                            <th style="width: 10%; text-align: right;">Total Stock (L)</th>
                            $hsdNozzleHeaderHtml
                            <th style="width: 10%; text-align: right;">Sales (L)</th>
                            <th style="width: 8%; text-align: right;">Test (L)</th>
                            <th style="width: 8%; text-align: right;">Rate (₹)</th>
                            <th style="width: 11%; text-align: right;">Amount (₹)</th>
                        </tr>
                    </thead>
                    <tbody>
    """.trimIndent())

    dailyRecords.forEach { rec ->
        val hsdNozzles = rec.nozzlesList.filter { it.type == "HSD" }
        val nozzleCellsHtml = if (hsdNozzleLabels.isNotEmpty()) {
            hsdNozzleLabels.joinToString("") { label ->
                val noz = hsdNozzles.firstOrNull { it.label == label }
                val valueStr = if (noz != null) String.format(Locale.getDefault(), "%,.1f", noz.opening) else "--"
                """<td class="num">$valueStr</td>"""
            }
        } else {
            """<td class="nozzle-cell">--</td>"""
        }

        htmlBuilder.append("""
            <tr>
                <td><strong>${rec.date}</strong></td>
                <td class="num">${if (rec.hsdOpeningStock > 0) String.format(Locale.getDefault(), "%,.1f", rec.hsdOpeningStock) else "--"}</td>
                <td class="num">${if (rec.hsdNewInventory > 0) String.format(Locale.getDefault(), "%,.1f", rec.hsdNewInventory) else "--"}</td>
                <td class="num">${if (rec.hsdTotalStock > 0) String.format(Locale.getDefault(), "%,.1f", rec.hsdTotalStock) else "--"}</td>
                $nozzleCellsHtml
                <td class="num hsd-col" style="font-weight:bold;">${if (rec.hsdSalesLitres > 0) String.format(Locale.getDefault(), "%,.1f", rec.hsdSalesLitres) else "--"}</td>
                <td class="num" style="color:#c62828;">${if (rec.hsdTestingLitres > 0) String.format(Locale.getDefault(), "%,.1f", rec.hsdTestingLitres) else "--"}</td>
                <td class="num">${if (rec.hsdRate > 0) String.format(Locale.getDefault(), "%,.2f", rec.hsdRate) else "--"}</td>
                <td class="num hsd-col" style="font-weight:bold;">${if (rec.hsdSalesAmount > 0) "₹" + String.format(Locale.getDefault(), "%,.2f", rec.hsdSalesAmount) else "--"}</td>
            </tr>
        """.trimIndent())
    }

    val hsdColSpan = if (hsdNozzleLabels.isNotEmpty()) 3 + hsdNozzleLabels.size else 4
    htmlBuilder.append("""
                        <tr class="total-row">
                            <td>TOTALS</td>
                            <td colspan="$hsdColSpan">&mdash;</td>
                            <td class="num hsd-col" style="font-size: 10px;">${String.format(Locale.getDefault(), "%,.1f", totalHsdLitres)} L</td>
                            <td>&mdash;</td>
                            <td>&mdash;</td>
                            <td class="num hsd-col" style="font-size: 10px; font-weight: bold;">₹${String.format(Locale.getDefault(), "%,.2f", totalHsdAmt)}</td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <!-- SHEET 3: CASH FLOW & COLLECTION SUMMARY -->
            <div class="page">
                <div class="header">
                    <h1>D R INAMDAR PETROLEUM</h1>
                    <p>Monthly Daily Sales & Stock Report &mdash; <strong>$monthName $year</strong></p>
                    <p>Generated on: ${SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date())}</p>
                </div>
                
                <div class="title-sect" style="color: #7209b7; border-bottom: 2px solid #7209b7;">CASH FLOW & COLLECTION SUMMARY SHEET</div>
                
                <table>
                    <thead>
                        <tr>
                            <th style="width: 25%;">Date</th>
                            <th style="width: 25%; text-align: right;">Cash Submitted (₹)</th>
                            <th style="width: 25%; text-align: right;">Actual Cash Collected (₹)</th>
                            <th style="width: 25%; text-align: right; background-color: #faf5ff; color: #7209b7;">Total Cash Collected (₹)</th>
                        </tr>
                    </thead>
                    <tbody>
    """.trimIndent())

    dailyRecords.forEach { rec ->
        htmlBuilder.append("""
            <tr>
                <td><strong>${rec.date}</strong></td>
                <td class="num">${if (rec.cashSubmitted > 0) "₹" + String.format(Locale.getDefault(), "%,.2f", rec.cashSubmitted) else "--"}</td>
                <td class="num">${if (rec.actualCashCollected > 0) "₹" + String.format(Locale.getDefault(), "%,.2f", rec.actualCashCollected) else "--"}</td>
                <td class="num" style="font-weight: bold; background-color: #faf5ff; color: #7209b7;">${if (rec.totalCashCollected > 0) "₹" + String.format(Locale.getDefault(), "%,.2f", rec.totalCashCollected) else "--"}</td>
            </tr>
        """.trimIndent())
    }

    htmlBuilder.append("""
                        <tr class="total-row">
                            <td>TOTALS</td>
                            <td class="num" style="font-size: 10px; font-weight: bold;">₹${String.format(Locale.getDefault(), "%,.2f", totalCashSubmitted)}</td>
                            <td class="num" style="font-size: 10px; font-weight: bold;">₹${String.format(Locale.getDefault(), "%,.2f", totalActualCashCollected)}</td>
                            <td class="num" style="font-size: 10px; font-weight: bold; background-color: #faf5ff; color: #7209b7;">₹${String.format(Locale.getDefault(), "%,.2f", totalCashCollected)}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </body>
        </html>
    """.trimIndent())

    val htmlContent = htmlBuilder.toString()
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            if (isShare) {
                try {
                    val cacheDir = context.cacheDir
                    val pdfFile = java.io.File(cacheDir, "monthly daily sales report $monthName $year.pdf")
                    val printAdapter = webView.createPrintDocumentAdapter("monthly daily sales report $monthName $year")
                    
                    android.print.PrintHelper.savePdf(printAdapter, pdfFile, object : android.print.PrintHelper.PrintCallback {
                        override fun onSuccess() {
                            try {
                                val uri = FileProvider.getUriForFile(context, "com.mypump.cal.fileprovider", pdfFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Monthly Daily Sales Report"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Sharing failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onFailure(error: String?) {
                            Toast.makeText(context, "PDF generation failed: $error", Toast.LENGTH_SHORT).show()
                        }
                    })
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to initialize PDF sharing: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } else {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager != null) {
                    val jobName = "Monthly_Sales_Report_${monthName}_${year}_${System.currentTimeMillis()}"
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    printManager.print("Monthly Sales and Stock Report", printAdapter, null)
                } else {
                    Toast.makeText(context, "Printing not supported on this device", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
}
