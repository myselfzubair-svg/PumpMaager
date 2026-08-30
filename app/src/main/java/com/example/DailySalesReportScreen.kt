package com.example

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Share
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySalesReportScreen(
    onBack: () -> Unit,
    adminPhone: String = "",
    onLogout: () -> Unit = {},
    historyViewModel: HistoryViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(adminPhone) {
        if (adminPhone.isNotBlank()) {
            historyViewModel.setAdminPhone(adminPhone)
        }
    }

    // Selected Month & Year
    val calendar = Calendar.getInstance()
    var selectedMonth by rememberSaveable { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var selectedYear by rememberSaveable { mutableStateOf(calendar.get(Calendar.YEAR)) }

    // Fetch all daily configs from Cloud
    var allDailyConfigs by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
    LaunchedEffect(adminPhone) {
        if (adminPhone.isNotBlank()) {
            allDailyConfigs = com.example.database.SupabaseRepository.getAllDailyConfigs(adminPhone)
        }
    }

    // Fetch all nozzle readings from DB to aggregate dynamically
    val allGeneralReadings: List<com.example.database.GeneralNozzleReading> by historyViewModel.allGeneralNozzleReadings.collectAsState(initial = emptyList())
    val allAudits: List<com.example.database.SavedAudit> by historyViewModel.allAudits.collectAsState(initial = emptyList())

    // Products to display
    var stationProducts by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(adminPhone) {
        if (adminPhone.isNotBlank()) {
            val pumpInfo = com.example.database.SupabaseRepository.getPumpInfo(adminPhone)
            stationProducts = pumpInfo?.productNames?.split(",")?.filter { it.isNotBlank() } ?: listOf("MS", "HSD")
        }
    }

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
    val dailyDataList = remember(daysInMonthList, allGeneralReadings, allAudits, allDailyConfigs, stationProducts) {
        daysInMonthList.map { dateStr ->
            val dayAudits = allAudits.filter { it.date == dateStr }
            val dayCashSubmitted = dayAudits.sumOf { it.cashSubmitted }
            val dayActualCashCollected = dayAudits.sumOf { it.actualCashCollected }
            val dayTotalCashCollected = dayCashSubmitted + dayActualCashCollected
            val dayTotalKharch = dayAudits.sumOf { it.totalKharch }
            val dayTotalUdhar = dayAudits.sumOf { it.totalUdhar }

            val productDataList = stationProducts.map { product ->
                val genReadings = allGeneralReadings.filter { it.date == dateStr && isSameProduct(it.productName, product) }
                
                // Aggregate sales & testing
                val nozzleAggregator = mutableMapOf<String, MutableList<Pair<Double, Double>>>() // label -> list of (opening, closing)
                val testingMap = mutableMapOf<String, Double>() // label -> total testing

                // Process General Readings (Unified source)
                genReadings.groupBy { it.nozzleLabel }.forEach { (label, readings) ->
                    nozzleAggregator.getOrPut(label) { mutableListOf() }.addAll(readings.map { it.openingReading to it.closingReading })
                    testingMap[label] = (testingMap[label] ?: 0.0) + readings.sumOf { it.testing }
                }

                var productSalesLitres = 0.0
                var productTestingLitres = 0.0
                var productTotalOpening = 0.0
                var productTotalClosing = 0.0
                val productNozzleDetailsList = mutableListOf<NozzleDailyDetail>()

                nozzleAggregator.forEach { (label, readings) ->
                    val sorted = readings.sortedBy { it.first }
                    val firstOp = sorted.firstOrNull()?.first ?: 0.0
                    val lastCl = sorted.lastOrNull()?.second ?: 0.0
                    val nozzleTesting = testingMap[label] ?: 0.0
                    val netSales = (lastCl - firstOp).coerceAtLeast(0.0)
                    productSalesLitres += netSales
                    productTestingLitres += nozzleTesting
                    productTotalOpening += firstOp
                    productTotalClosing += lastCl
                    productNozzleDetailsList.add(NozzleDailyDetail(label, product, firstOp, lastCl, nozzleTesting, netSales))
                }

                val dayConfig = allDailyConfigs[dateStr] ?: emptyMap()
                val rate = dayConfig["rate_${product.lowercase().replace(" ", "_")}"]?.toDoubleOrNull() 
                    ?: if (isSameProduct(product, "MS")) dayConfig["rate_ms"]?.toDoubleOrNull() ?: 0.0
                       else if (isSameProduct(product, "HSD")) dayConfig["rate_hsd"]?.toDoubleOrNull() ?: 0.0
                       else 0.0
                val stock = dayConfig["stock_${product.lowercase().replace(" ", "_")}"]?.toDoubleOrNull()
                    ?: if (isSameProduct(product, "MS")) dayConfig["stock_ms"]?.toDoubleOrNull() ?: 0.0
                       else if (isSameProduct(product, "HSD")) dayConfig["stock_hsd"]?.toDoubleOrNull() ?: 0.0
                       else 0.0

                ProductDayData(
                    productName = product,
                    rate = rate,
                    openingStock = stock,
                    totalOpening = productTotalOpening,
                    totalClosing = productTotalClosing,
                    salesLitres = productSalesLitres,
                    testingLitres = productTestingLitres,
                    salesAmount = productSalesLitres * rate,
                    nozzleDetails = productNozzleDetailsList
                )
            }

            DailySalesReportData(
                date = dateStr,
                products = productDataList,
                cashSubmitted = dayCashSubmitted,
                actualCashCollected = dayActualCashCollected,
                totalCashCollected = dayTotalCashCollected,
                totalKharch = dayTotalKharch,
                totalUdhar = dayTotalUdhar
            )
        }
    }

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

                        // Grid of products
                        stationProducts.chunked(2).forEach { rowProducts ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowProducts.forEach { product ->
                                    val totalLitres = dailyDataList.sumOf { day -> day.products.find { it.productName == product }?.salesLitres ?: 0.0 }
                                    val totalAmount = dailyDataList.sumOf { day -> day.products.find { it.productName == product }?.salesAmount ?: 0.0 }
                                    
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = product,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${String.format(Locale.getDefault(), "%,.1f", totalLitres)} L",
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "₹${String.format(Locale.getDefault(), "%,.2f", totalAmount)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                if (rowProducts.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
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
                            val totalSalesVal = dailyDataList.sumOf { day -> day.products.sumOf { it.salesAmount } }
                            Text(
                                text = "₹${String.format(Locale.getDefault(), "%,.2f", totalSalesVal)}",
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
                            val totalCashVal = dailyDataList.sumOf { it.totalCashCollected }
                            Text(
                                text = "₹${String.format(Locale.getDefault(), "%,.2f", totalCashVal)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Total Expenses (Kharch)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val totalKharchVal = dailyDataList.sumOf { it.totalKharch }
                            Text(
                                text = "₹${String.format(Locale.getDefault(), "%,.2f", totalKharchVal)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFC62828)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Total Credits (Udhar)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val totalUdharVal = dailyDataList.sumOf { it.totalUdhar }
                            Text(
                                text = "₹${String.format(Locale.getDefault(), "%,.2f", totalUdharVal)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFE65100)
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
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    dailyData.products.take(3).forEachIndexed { index, product ->
                                        Text(
                                            text = "${product.productName.take(3)}: ${String.format(Locale.getDefault(), "%,.1f", product.salesLitres)} L",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (index < dailyData.products.size - 1 && index < 2) {
                                            Text(
                                                text = "|",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₹${String.format(Locale.getDefault(), "%,.2f", dailyData.products.sumOf { it.salesAmount })}",
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
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                                // Grid of Product Details
                                dailyData.products.chunked(2).forEach { productPair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        productPair.forEach { product ->
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = product.productName,
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(bottom = 6.dp)
                                                )

                                                DetailRow("Total Nozzles", "${product.nozzleDetails.size}", isBold = true)
                                                DetailRow("Opening Reading", String.format(Locale.getDefault(), "%,.1f", product.totalOpening))
                                                DetailRow("Closing Reading", String.format(Locale.getDefault(), "%,.1f", product.totalClosing))
                                                DetailRow("Net Sales Qty", "${String.format(Locale.getDefault(), "%,.1f", product.salesLitres)} L")
                                                DetailRow("Rate/Litre", "₹${String.format(Locale.getDefault(), "%,.2f", product.rate)}")
                                                DetailRow("Sales Amount", "₹${String.format(Locale.getDefault(), "%,.2f", product.salesAmount)}", isPrimary = true)
                                            }
                                        }
                                        if (productPair.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
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
                                        DetailRow(
                                            label = "Daily Expenses (Kharch) (₹)",
                                            value = "₹${String.format(Locale.getDefault(), "%,.2f", dailyData.totalKharch)}",
                                            color = Color(0xFFC62828)
                                        )
                                        DetailRow(
                                            label = "Credits Given (Udhar) (₹)",
                                            value = "₹${String.format(Locale.getDefault(), "%,.2f", dailyData.totalUdhar)}",
                                            color = Color(0xFFE65100)
                                        )
                                        HorizontalDivider(
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
                                val allNozzles = dailyData.products.flatMap { it.nozzleDetails }
                                if (allNozzles.isNotEmpty()) {
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
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                                                Spacer(modifier = Modifier.height(4.dp))

                                                allNozzles.forEach { nozzle ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 3.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${nozzle.label} (${nozzle.type.take(3)})",
                                                            modifier = Modifier.weight(1.5f),
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                            color = MaterialTheme.colorScheme.primary
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
    val products: List<ProductDayData>,
    val cashSubmitted: Double = 0.0,
    val actualCashCollected: Double = 0.0,
    val totalCashCollected: Double = 0.0,
    val totalKharch: Double = 0.0,
    val totalUdhar: Double = 0.0
)

data class ProductDayData(
    val productName: String,
    val rate: Double,
    val openingStock: Double,
    val totalOpening: Double = 0.0,
    val totalClosing: Double = 0.0,
    val salesLitres: Double,
    val testingLitres: Double,
    val salesAmount: Double,
    val nozzleDetails: List<NozzleDailyDetail>
)

data class NozzleDailyDetail(
    val label: String,
    val type: String, // Product name
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
    isShare: Boolean = false
) {
    val webView = WebView(context)
    val htmlBuilder = StringBuilder()

    // Get all unique products present in the records
    val allProducts = dailyRecords.flatMap { day -> day.products.map { it.productName } }.distinct()
    val monthlyTotalCashSubmitted = dailyRecords.sumOf { it.cashSubmitted }
    val monthlyTotalActualCashCollected = dailyRecords.sumOf { it.actualCashCollected }
    val monthlyTotalCashCollected = dailyRecords.sumOf { it.totalCashCollected }

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
                .total-row {
                    font-weight: bold;
                    background-color: #eee;
                }
            </style>
        </head>
        <body>
    """.trimIndent())

    // Generate a sheet for each product
    allProducts.forEach { product ->
        val productNozzleLabels = dailyRecords.flatMap { rec ->
            rec.products.find { it.productName == product }?.nozzleDetails?.map { it.label } ?: emptyList()
        }.distinct().sorted()

        val nozzleHeaderHtml = if (productNozzleLabels.isNotEmpty()) {
            val widthPerNozzle = 25.0 / productNozzleLabels.size
            productNozzleLabels.joinToString("") { label ->
                """<th style="width: ${String.format(Locale.US, "%.2f", widthPerNozzle)}%; text-align: right;">$label (Op)</th>"""
            }
        } else {
            """<th style="width: 25%;">Nozzle Opening Readings</th>"""
        }

        htmlBuilder.append("""
            <div class="page">
                <div class="header">
                    <h1>D R INAMDAR PETROLEUM</h1>
                    <p>Monthly Daily Sales & Stock Report &mdash; <strong>$monthName $year</strong></p>
                    <p>Product: <strong>$product</strong> | Generated on: ${SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date())}</p>
                </div>
                
                <div class="title-sect">$product &mdash; DAILY SALES & STOCK SHEET</div>
                
                <table>
                    <thead>
                        <tr>
                            <th style="width: 10%;">Date</th>
                            <th style="width: 10%; text-align: right;">Total Opening</th>
                            <th style="width: 10%; text-align: right;">Total Closing</th>
                            $nozzleHeaderHtml
                            <th style="width: 10%; text-align: right;">Sales (L)</th>
                            <th style="width: 8%; text-align: right;">Test (L)</th>
                            <th style="width: 8%; text-align: right;">Rate (₹)</th>
                            <th style="width: 12%; text-align: right;">Amount (₹)</th>
                        </tr>
                    </thead>
                    <tbody>
        """.trimIndent())

        var totalLitres = 0.0
        var totalAmt = 0.0

        dailyRecords.forEach { rec ->
            val pData = rec.products.find { it.productName == product }
            val nozzleCellsHtml = if (productNozzleLabels.isNotEmpty()) {
                productNozzleLabels.joinToString("") { label ->
                    val noz = pData?.nozzleDetails?.find { it.label == label }
                    val valueStr = if (noz != null) String.format(Locale.getDefault(), "%,.1f", noz.opening) else "--"
                    """<td class="num">$valueStr</td>"""
                }
            } else {
                """<td class="nozzle-cell">--</td>"""
            }

            if (pData != null) {
                totalLitres += pData.salesLitres
                totalAmt += pData.salesAmount
                htmlBuilder.append("""
                    <tr>
                        <td><strong>${rec.date}</strong></td>
                        <td class="num">${formatDouble(pData.totalOpening)}</td>
                        <td class="num">${formatDouble(pData.totalClosing)}</td>
                        $nozzleCellsHtml
                        <td class="num" style="font-weight:bold;">${if (pData.salesLitres > 0) String.format(Locale.getDefault(), "%,.1f", pData.salesLitres) else "--"}</td>
                        <td class="num" style="color:#c62828;">${if (pData.testingLitres > 0) String.format(Locale.getDefault(), "%,.1f", pData.testingLitres) else "--"}</td>
                        <td class="num">${if (pData.rate > 0) String.format(Locale.getDefault(), "%,.2f", pData.rate) else "--"}</td>
                        <td class="num" style="font-weight:bold;">${if (pData.salesAmount > 0) "₹" + String.format(Locale.getDefault(), "%,.2f", pData.salesAmount) else "--"}</td>
                    </tr>
                """.trimIndent())
            } else {
                val emptyNozzleCells = "<td>--</td>".repeat(productNozzleLabels.size.coerceAtLeast(1))
                htmlBuilder.append("""
                    <tr>
                        <td><strong>${rec.date}</strong></td>
                        <td>--</td>
                        $emptyNozzleCells
                        <td>--</td>
                        <td>--</td>
                        <td>--</td>
                        <td>--</td>
                    </tr>
                """.trimIndent())
            }
        }

        val colSpan = if (productNozzleLabels.isNotEmpty()) 3 + productNozzleLabels.size else 4
        htmlBuilder.append("""
                        <tr class="total-row">
                            <td>TOTALS</td>
                            <td colspan="$colSpan">&mdash;</td>
                            <td class="num" style="font-size: 10px;">${String.format(Locale.getDefault(), "%,.1f", totalLitres)} L</td>
                            <td>&mdash;</td>
                            <td>&mdash;</td>
                            <td class="num" style="font-size: 10px; font-weight: bold;">₹${String.format(Locale.getDefault(), "%,.2f", totalAmt)}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        """.trimIndent())
    }

    // SHEET: STATION CONSOLIDATED FUEL SUMMARY
    htmlBuilder.append("""
        <div class="page">
            <div class="header">
                <h1>D R INAMDAR PETROLEUM</h1>
                <p>Monthly Station Consolidated Fuel Summary &mdash; <strong>$monthName $year</strong></p>
            </div>
            <div class="title-sect">FUEL-WISE SALES RECONCILIATION TOTALS</div>
            <table>
                <thead>
                    <tr>
                        <th>Product Identity</th>
                        <th style="text-align: right;">Total Quantity Sold (L)</th>
                        <th style="text-align: right;">Total Revenue Earned (₹)</th>
                    </tr>
                </thead>
                <tbody>
    """.trimIndent())

    var grandStationLitres = 0.0
    var grandStationRevenue = 0.0

    allProducts.forEach { product ->
        val prodTotalLitres = dailyRecords.sumOf { day -> day.products.find { it.productName == product }?.salesLitres ?: 0.0 }
        val prodTotalRevenue = dailyRecords.sumOf { day -> day.products.find { it.productName == product }?.salesAmount ?: 0.0 }
        grandStationLitres += prodTotalLitres
        grandStationRevenue += prodTotalRevenue

        htmlBuilder.append("""
            <tr>
                <td><strong>$product</strong></td>
                <td class="num">${String.format(Locale.getDefault(), "%,.1f", prodTotalLitres)} L</td>
                <td class="num">₹${String.format(Locale.getDefault(), "%,.2f", prodTotalRevenue)}</td>
            </tr>
        """.trimIndent())
    }

    htmlBuilder.append("""
            <tr class="total-row" style="background-color: #f8fafc;">
                <td><strong>STATION TOTALS (ALL PRODUCTS)</strong></td>
                <td class="num" style="font-size: 11px;">${String.format(Locale.getDefault(), "%,.1f", grandStationLitres)} L</td>
                <td class="num" style="font-size: 11px; color: #1a5f7a;">₹${String.format(Locale.getDefault(), "%,.2f", grandStationRevenue)}</td>
            </tr>
        </tbody>
    </table>
    </div>
    """.trimIndent())

    // Cash Sheet
    val monthlyTotalKharch = dailyRecords.sumOf { it.totalKharch }
    val monthlyTotalUdhar = dailyRecords.sumOf { it.totalUdhar }

    htmlBuilder.append("""
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
                            <th style="width: 20%;">Date</th>
                            <th style="width: 15%; text-align: right;">Cash Sub. (₹)</th>
                            <th style="width: 15%; text-align: right;">Actual Cash (₹)</th>
                            <th style="width: 15%; text-align: right;">Kharch (₹)</th>
                            <th style="width: 15%; text-align: right;">Udhar (₹)</th>
                            <th style="width: 20%; text-align: right; background-color: #faf5ff; color: #7209b7;">Total Cash (₹)</th>
                        </tr>
                    </thead>
                    <tbody>
    """.trimIndent())

    dailyRecords.forEach { rec ->
        htmlBuilder.append("""
            <tr>
                <td><strong>${rec.date}</strong></td>
                <td class="num">${if (rec.cashSubmitted > 0) String.format(Locale.getDefault(), "%,.2f", rec.cashSubmitted) else "--"}</td>
                <td class="num">${if (rec.actualCashCollected > 0) String.format(Locale.getDefault(), "%,.2f", rec.actualCashCollected) else "--"}</td>
                <td class="num" style="color:#c62828;">${if (rec.totalKharch > 0) String.format(Locale.getDefault(), "%,.2f", rec.totalKharch) else "--"}</td>
                <td class="num" style="color:#e65100;">${if (rec.totalUdhar > 0) String.format(Locale.getDefault(), "%,.2f", rec.totalUdhar) else "--"}</td>
                <td class="num" style="font-weight: bold; background-color: #faf5ff; color: #7209b7;">${if (rec.totalCashCollected > 0) String.format(Locale.getDefault(), "%,.2f", rec.totalCashCollected) else "--"}</td>
            </tr>
        """.trimIndent())
    }

    htmlBuilder.append("""
                        <tr class="total-row">
                            <td>TOTALS</td>
                            <td class="num" style="font-size: 9px; font-weight: bold;">₹${String.format(Locale.getDefault(), "%,.2f", monthlyTotalCashSubmitted)}</td>
                            <td class="num" style="font-size: 9px; font-weight: bold;">₹${String.format(Locale.getDefault(), "%,.2f", monthlyTotalActualCashCollected)}</td>
                            <td class="num" style="font-size: 9px; font-weight: bold; color:#c62828;">₹${String.format(Locale.getDefault(), "%,.2f", monthlyTotalKharch)}</td>
                            <td class="num" style="font-size: 9px; font-weight: bold; color:#e65100;">₹${String.format(Locale.getDefault(), "%,.2f", monthlyTotalUdhar)}</td>
                            <td class="num" style="font-size: 9px; font-weight: bold; background-color: #faf5ff; color: #7209b7;">₹${String.format(Locale.getDefault(), "%,.2f", monthlyTotalCashCollected)}</td>
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
