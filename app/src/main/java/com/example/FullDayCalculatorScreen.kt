package com.example

import android.app.DatePickerDialog
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Logout
import androidx.core.content.FileProvider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.database.HistoryViewModel
import com.example.database.HsdNozzleReading
import com.example.database.MsNozzleReading
import com.example.database.SavedAudit
import java.text.SimpleDateFormat
import java.util.*

data class NozzleStats(
    val label: String,
    val firstOpening: Double,
    val lastClosing: Double,
    val totalTesting: Double,
    val totalNetVolume: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullDayCalculatorScreen(
    onBack: (() -> Unit)? = null,
    date: String = "",
    onDateClick: (() -> Unit)? = null,
    msNozzleCount: Int = 4,
    hsdNozzleCount: Int = 4,
    msNozzleLabels: List<String> = emptyList(),
    hsdNozzleLabels: List<String> = emptyList(),
    phone: String = "",
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val historyViewModel: HistoryViewModel = viewModel()

    // 1. Selected date for daily sales audit
    var selectedAuditDate by remember { mutableStateOf(date.ifEmpty { 
        val cal = Calendar.getInstance()
        val d = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))
        val m = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        val y = cal.get(Calendar.YEAR)
        "$d-$m-$y"
    }) }

    val sharedPrefs = remember(selectedAuditDate) { context.getSharedPreferences("pump_manager_prefs", Context.MODE_PRIVATE) }
    val densityMs = remember(selectedAuditDate) { sharedPrefs.getString("density_ms_$selectedAuditDate", "") ?: "" }
    val densityHsd = remember(selectedAuditDate) { sharedPrefs.getString("density_hsd_$selectedAuditDate", "") ?: "" }
    val stockMs = remember(selectedAuditDate) { sharedPrefs.getString("stock_ms_$selectedAuditDate", "") ?: "" }
    val stockHsd = remember(selectedAuditDate) { sharedPrefs.getString("stock_hsd_$selectedAuditDate", "") ?: "" }
    val receiptMs = remember(selectedAuditDate) { sharedPrefs.getString("receipt_ms_$selectedAuditDate", "") ?: "" }
    val receiptHsd = remember(selectedAuditDate) { sharedPrefs.getString("receipt_hsd_$selectedAuditDate", "") ?: "" }

    val dStockMs = remember(stockMs) { stockMs.toDoubleOrNull() ?: 0.0 }
    val dStockHsd = remember(stockHsd) { stockHsd.toDoubleOrNull() ?: 0.0 }
    val dReceiptMs = remember(receiptMs) { receiptMs.toDoubleOrNull() ?: 0.0 }
    val dReceiptHsd = remember(receiptHsd) { receiptHsd.toDoubleOrNull() ?: 0.0 }

    // 2. Fetch all saved audits, and nozzle readings for the selected date
    val allAudits by historyViewModel.allAudits.collectAsState(initial = emptyList())
    val msReadings by historyViewModel.getMsReadingsByDate(selectedAuditDate).collectAsState(initial = emptyList())
    val hsdReadings by historyViewModel.getHsdReadingsByDate(selectedAuditDate).collectAsState(initial = emptyList())

    val allMsNozzleReadings by historyViewModel.allMsNozzleReadings.collectAsState(initial = emptyList())
    val allHsdNozzleReadings by historyViewModel.allHsdNozzleReadings.collectAsState(initial = emptyList())

    val sdf = remember { java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()) }
    val selectedDateObj = remember(selectedAuditDate) {
        try { sdf.parse(selectedAuditDate) } catch (e: Exception) { null }
    }

    // 3. Filter audits for the selected date and sort them chronologically
    val filteredAudits = remember(allAudits, selectedAuditDate) {
        allAudits.filter { it.date == selectedAuditDate }
            .sortedBy { it.timestamp }
    }

    // 4. Calculate first opening and last closing for each nozzle
    val consolidatedNozzles = remember(msReadings, hsdReadings, allMsNozzleReadings, allHsdNozzleReadings, selectedDateObj) {
        fun isBeforeSelectedDate(readingDateStr: String): Boolean {
            val sel = selectedDateObj ?: return false
            val rDate = try { sdf.parse(readingDateStr) } catch (e: Exception) { null } ?: return false
            return rDate.before(sel)
        }

        val msStats = if (msReadings.isNotEmpty()) {
            msReadings.groupBy { it.nozzleLabel }.map { (label, readings) ->
                val sorted = readings.sortedBy { it.timestamp }
                val firstOp = sorted.firstOrNull()?.openingReading ?: 0.0
                val lastCl = sorted.lastOrNull()?.closingReading ?: 0.0
                val totalTest = sorted.sumOf { it.testing }
                val netVol = lastCl - firstOp
                NozzleStats(label, firstOp, lastCl, totalTest, netVol)
            }
        } else {
            val allLabels = allMsNozzleReadings.map { it.nozzleLabel }.distinct()
            allLabels.map { label ->
                val prevReadingsForLabel = allMsNozzleReadings.filter {
                    it.nozzleLabel == label && isBeforeSelectedDate(it.date)
                }.sortedByDescending { it.timestamp }
                val prevClosing = prevReadingsForLabel.firstOrNull()?.closingReading ?: 0.0
                NozzleStats(label, prevClosing, prevClosing, 0.0, 0.0)
            }
        }

        val hsdStats = if (hsdReadings.isNotEmpty()) {
            hsdReadings.groupBy { it.nozzleLabel }.map { (label, readings) ->
                val sorted = readings.sortedBy { it.timestamp }
                val firstOp = sorted.firstOrNull()?.openingReading ?: 0.0
                val lastCl = sorted.lastOrNull()?.closingReading ?: 0.0
                val totalTest = sorted.sumOf { it.testing }
                val netVol = lastCl - firstOp
                NozzleStats(label, firstOp, lastCl, totalTest, netVol)
            }
        } else {
            val allLabels = allHsdNozzleReadings.map { it.nozzleLabel }.distinct()
            allLabels.map { label ->
                val prevReadingsForLabel = allHsdNozzleReadings.filter {
                    it.nozzleLabel == label && isBeforeSelectedDate(it.date)
                }.sortedByDescending { it.timestamp }
                val prevClosing = prevReadingsForLabel.firstOrNull()?.closingReading ?: 0.0
                NozzleStats(label, prevClosing, prevClosing, 0.0, 0.0)
            }
        }

        (msStats + hsdStats).sortedBy { it.label }
    }

    val msSalesBeforeTesting = remember(consolidatedNozzles) {
        consolidatedNozzles.filter { it.label.contains("MS", ignoreCase = true) }.sumOf { it.totalNetVolume }
    }

    val hsdSalesBeforeTesting = remember(consolidatedNozzles) {
        consolidatedNozzles.filter { it.label.contains("HSD", ignoreCase = true) }.sumOf { it.totalNetVolume }
    }

    val msTesting = remember(filteredAudits) {
        filteredAudits.sumOf { parseMsTestingLitres(it.summaryText) }
    }

    val hsdTesting = remember(filteredAudits) {
        filteredAudits.sumOf { parseHsdTestingLitres(it.summaryText) }
    }

    val msNetSales = remember(msSalesBeforeTesting, msTesting) {
        msSalesBeforeTesting - msTesting
    }

    val hsdNetSales = remember(hsdSalesBeforeTesting, hsdTesting) {
        hsdSalesBeforeTesting - hsdTesting
    }

    val closingStockMs = remember(dStockMs, dReceiptMs, msNetSales) { dStockMs + dReceiptMs - msNetSales }
    val closingStockHsd = remember(dStockHsd, dReceiptHsd, hsdNetSales) { dStockHsd + dReceiptHsd - hsdNetSales }

    // 5. Aggregate all factors parsed from the saved audit summary texts
    val aggregatedFinancials = remember(filteredAudits) {
        var totalSalesValue = 0.0
        var totalPhonePe = 0.0
        var totalCards = 0.0
        var totalCashSubmitted = 0.0
        var totalActualCashCollected = 0.0
        var totalExpenses = 0.0
        var totalUdhar = 0.0
        var totalRecoveries = 0.0

        filteredAudits.forEach { audit ->
            val text = audit.summaryText
            totalSalesValue += parseAmount(text, """Total\s+Sales\s*\(MS\+HSD\):\s*₹?\s*([\d,.]+)""")
            totalPhonePe += parseAmount(text, """PhonePe\s+Deduction:\s*-?₹?\s*([\d,.]+)""")
            totalCards += parseAmount(text, """Cards\s+Deduction:\s*-?₹?\s*([\d,.]+)""")
            totalCashSubmitted += parseAmount(text, """Cash\s+Submitted:\s*-?₹?\s*([\d,.]+)""")
            totalActualCashCollected += if (audit.actualCashCollected > 0.0) {
                audit.actualCashCollected
            } else {
                parseAmount(text, """ACTUAL\s+CASH\s+COLLECTED:\s*₹?\s*([\d,.]+)""")
            }
            totalExpenses += parseAmount(text, """Total\s+Expenses:\s*-?₹?\s*([\d,.]+)""")
            totalUdhar += parseAmount(text, """Total\s+Udhar:\s*-?₹?\s*([\d,.]+)""")
            totalRecoveries += parseAmount(text, """Total\s+Recoveries\s+Added:\s*\+?₹?\s*([\d,.]+)""")
        }

        object {
            val sales = totalSalesValue
            val phonePe = totalPhonePe
            val cards = totalCards
            val cashSubmitted = totalCashSubmitted
            val actualCashCollected = totalActualCashCollected
            val expenses = totalExpenses
            val udhar = totalUdhar
            val recoveries = totalRecoveries
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = LanguageManager.translate("Managers Daily Sales Audit", "मैनेजर्स दैनिक बिक्री ऑडिट"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBack?.invoke() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
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
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // DATE SELECTOR CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val calendar = Calendar.getInstance()
                                if (selectedAuditDate.isNotBlank()) {
                                    try {
                                        val parts = selectedAuditDate.split("-")
                                        if (parts.size == 3) {
                                            calendar.set(Calendar.DAY_OF_MONTH, parts[0].toInt())
                                            calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                                            calendar.set(Calendar.YEAR, parts[2].toInt())
                                        }
                                    } catch (e: Exception) {
                                    }
                                }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val formattedDay = String.format("%02d", dayOfMonth)
                                        val formattedMonth = String.format("%02d", month + 1)
                                        selectedAuditDate = "$formattedDay-$formattedMonth-$year"
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Date Range",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = LanguageManager.translate("Active Audit Date", "सक्रिय ऑडिट तिथि"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = selectedAuditDate,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = LanguageManager.translate("Change", "बदलें"),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // DAILY SALES AUDIT ACTION BUTTONS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            printDailySalesAuditPdf(
                                context = context,
                                date = selectedAuditDate,
                                audits = filteredAudits,
                                nozzleStatsList = consolidatedNozzles,
                                totals = aggregatedFinancials,
                                isShare = false
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("daily_sales_audit_print_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print Icon",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = LanguageManager.translate("PRINT", "प्रिंट"),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Button(
                        onClick = {
                            printDailySalesAuditPdf(
                                context = context,
                                date = selectedAuditDate,
                                audits = filteredAudits,
                                nozzleStatsList = consolidatedNozzles,
                                totals = aggregatedFinancials,
                                isShare = true
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("daily_sales_audit_share_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Icon",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = LanguageManager.translate("SHARE PDF", "साझा करें"),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // FINANCIAL AGGREGATIONS CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = LanguageManager.translate("Consolidated Ledger Tally", "समेकित खाता मिलान"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        FinancialFactorRow(
                            label = LanguageManager.translate("Total Sales Value (MS+HSD)", "कुल बिक्री मूल्य (MS+HSD)"),
                            amount = aggregatedFinancials.sales,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FinancialFactorRow(
                            label = LanguageManager.translate("Udhari Recoveries (+)", "उधारी वसूली (+)"),
                            amount = aggregatedFinancials.recoveries,
                            color = Color(0xFF2E7D32)
                        )
                        FinancialFactorRow(
                            label = LanguageManager.translate("PhonePe UPI Deductions (-)", "फोनपे यूपीआई कटौती (-)"),
                            amount = aggregatedFinancials.phonePe,
                            color = Color(0xFFC62828)
                        )
                        FinancialFactorRow(
                            label = LanguageManager.translate("Card Payments Deduction (-)", "कार्ड भुगतान कटौती (-)"),
                            amount = aggregatedFinancials.cards,
                            color = Color(0xFFC62828)
                        )
                        FinancialFactorRow(
                            label = LanguageManager.translate("Shift Expenses (Kharch) (-)", "शिफ्ट खर्च (खर्च) (-)"),
                            amount = aggregatedFinancials.expenses,
                            color = Color(0xFFC62828)
                        )
                        FinancialFactorRow(
                            label = LanguageManager.translate("Credit Sales (Udhar) (-)", "उधार बिक्री (उधार) (-)"),
                            amount = aggregatedFinancials.udhar,
                            color = Color(0xFFC62828)
                        )
                        FinancialFactorRow(
                            label = LanguageManager.translate("Total Cash Collected", "कुल नकद संग्रह"),
                            amount = aggregatedFinancials.cashSubmitted + aggregatedFinancials.actualCashCollected,
                            color = Color(0xFF2E7D32)
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        val totalTallyDiscrepancy = filteredAudits.sumOf { parseTallyDiscrepancy(it.summaryText) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = LanguageManager.translate("Tally Result Total", "कुल मिलान परिणाम"),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val tallyResultText = if (totalTallyDiscrepancy == 0.0) {
                                LanguageManager.translate("SUCCESS (Perfect Match)", "सफल (पूर्ण मिलान)")
                            } else if (totalTallyDiscrepancy < 0.0) {
                                LanguageManager.translate("SHORTAGE -₹${String.format(Locale.getDefault(), "%,.2f", -totalTallyDiscrepancy)}", "कमी -₹${String.format(Locale.getDefault(), "%,.2f", -totalTallyDiscrepancy)}")
                            } else {
                                LanguageManager.translate("EXTRA CASH +₹${String.format(Locale.getDefault(), "%,.2f", totalTallyDiscrepancy)}", "अतिरिक्त नकद +₹${String.format(Locale.getDefault(), "%,.2f", totalTallyDiscrepancy)}")
                            }
                            val tallyResultColor = if (totalTallyDiscrepancy == 0.0) {
                                Color(0xFF2E7D32)
                            } else if (totalTallyDiscrepancy < 0.0) {
                                Color(0xFFC62828)
                            } else {
                                Color(0xFF1565C0)
                            }
                            Text(
                                text = tallyResultText,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = tallyResultColor
                            )
                        }
                    }
                }
            }

            // DENSITY & INVENTORY STOCK AUDIT CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = LanguageManager.translate("Density & Inventory Stock Audit", "डेंसिटी और इन्वेंट्री स्टॉक ऑडिट"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        // MS Petrol Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = LanguageManager.translate("MS Petrol (Petrol)", "एमएस पेट्रोल (पेट्रोल)"),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = LanguageManager.translate("Morning Density", "मॉर्निंग डेंसिटी"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = densityMs.ifBlank { "--" },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Column(modifier = Modifier.weight(1.3f)) {
                                    Text(
                                        text = LanguageManager.translate("Sales before Testing", "परीक्षण से पहले बिक्री"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%,.1f", msSalesBeforeTesting)} L",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Column(modifier = Modifier.weight(1.3f)) {
                                    Text(
                                        text = LanguageManager.translate("Testing Deduction", "परीक्षण कटौती"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%,.1f", msTesting)} L",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Column(modifier = Modifier.weight(1.3f)) {
                                    Text(
                                        text = LanguageManager.translate("Net Sales", "कुल बिक्री"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%,.1f", msNetSales)} L",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))

                        // HSD Diesel Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = LanguageManager.translate("HSD Diesel (Diesel)", "एचएसडी डीजल (डीजल)"),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = LanguageManager.translate("Morning Density", "मॉर्निंग डेंसिटी"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = densityHsd.ifBlank { "--" },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Column(modifier = Modifier.weight(1.3f)) {
                                    Text(
                                        text = LanguageManager.translate("Sales before Testing", "परीक्षण से पहले बिक्री"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%,.1f", hsdSalesBeforeTesting)} L",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Column(modifier = Modifier.weight(1.3f)) {
                                    Text(
                                        text = LanguageManager.translate("Testing Deduction", "परीक्षण कटौती"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%,.1f", hsdTesting)} L",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Column(modifier = Modifier.weight(1.3f)) {
                                    Text(
                                        text = LanguageManager.translate("Net Sales", "कुल बिक्री"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%,.1f", hsdNetSales)} L",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // CONSOLIDATED NOZZLE READINGS CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = LanguageManager.translate("Consolidated Nozzle Readings", "समेकित नोजल रीडिंग"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (consolidatedNozzles.isEmpty()) {
                            Text(
                                text = LanguageManager.translate("No nozzle readings saved for this date.", "इस तारीख के लिए कोई नोजल रीडिंग सहेज नहीं की गई।"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = LanguageManager.translate("Nozzle", "नोजल"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1.5f)
                                )
                                Text(
                                    text = LanguageManager.translate("First Op", "प्रथम"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1.2f),
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    text = LanguageManager.translate("Last Cl", "अंतिम"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1.2f),
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    text = LanguageManager.translate("Net L", "नेट ली"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1.1f),
                                    textAlign = TextAlign.End
                                )
                            }

                            consolidatedNozzles.forEach { stat ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stat.label,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        modifier = Modifier.weight(1.5f)
                                    )
                                    Text(
                                        text = String.format(Locale.getDefault(), "%.2f", stat.firstOpening),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.weight(1.2f),
                                        textAlign = TextAlign.End
                                    )
                                    Text(
                                        text = String.format(Locale.getDefault(), "%.2f", stat.lastClosing),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.weight(1.2f),
                                        textAlign = TextAlign.End
                                    )
                                    Text(
                                        text = String.format(Locale.getDefault(), "%.1f L", stat.totalNetVolume),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1.1f),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // CHRONOLOGICAL SHIFT LOGS SECTION
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = LanguageManager.translate("Saved Shifts Log", "सहेजे गए शिफ्ट्स लॉग"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = "${filteredAudits.size} Shifts",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (filteredAudits.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "No reports found",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = LanguageManager.translate("No Shifts Found", "कोई शिफ्ट नहीं मिली"),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            } else {
                items(filteredAudits, key = { it.id }) { audit ->
                    ShiftAuditMiniCard(audit = audit)
                }
            }
        }
    }
}

@Composable
fun FinancialFactorRow(
    label: String,
    amount: Double,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "₹${String.format(Locale.getDefault(), "%,.2f", amount)}",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}

@Composable
fun ShiftAuditMiniCard(audit: SavedAudit) {
    val formatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeStr = remember(audit.timestamp) {
        try {
            formatter.format(Date(audit.timestamp))
        } catch (e: Exception) {
            ""
        }
    }

    val totalSales = remember(audit.summaryText) { parseTotalSales(audit.summaryText) }
    val tallyResult = remember(audit.summaryText) { parseTallyResult(audit.summaryText) }
    val msLitres = remember(audit.summaryText) { parseMsNetSalesLitres(audit.summaryText) }
    val hsdLitres = remember(audit.summaryText) { parseHsdNetSalesLitres(audit.summaryText) }
    val activeNozzles = remember(audit.summaryText) { parseActiveNozzles(audit.summaryText) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (audit.caName.isNotBlank()) audit.caName else "Shift CA",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${audit.auditType} | Meter: ${audit.meterNo.ifBlank { "N/A" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (activeNozzles.isNotEmpty()) {
                        Text(
                            text = LanguageManager.translate("Active Nozzles: ", "सक्रिय नोजल: ") + activeNozzles.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = LanguageManager.translate("Shift Sales", "शिफ्ट बिक्री"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹${String.format(Locale.getDefault(), "%,.2f", totalSales)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "MS: ${String.format(Locale.getDefault(), "%,.1f", msLitres)} L | HSD: ${String.format(Locale.getDefault(), "%,.1f", hsdLitres)} L",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val msTesting = remember(audit.summaryText) { parseMsTestingLitres(audit.summaryText) }
                    val hsdTesting = remember(audit.summaryText) { parseHsdTestingLitres(audit.summaryText) }
                    var testingText = ""
                    if (msTesting > 0.0) testingText += "MS Test: ${String.format(Locale.getDefault(), "%,.1f", msTesting)} L"
                    if (hsdTesting > 0.0) {
                        if (testingText.isNotEmpty()) testingText += " | "
                        testingText += "HSD Test: ${String.format(Locale.getDefault(), "%,.1f", hsdTesting)} L"
                    }
                    if (testingText.isNotEmpty()) {
                        Text(
                            text = testingText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFC62828)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = LanguageManager.translate("Tally Result", "मिलान परिणाम"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val tallyColor = when {
                        tallyResult.contains("SUCCESS", ignoreCase = true) -> Color(0xFF2E7D32)
                        tallyResult.contains("SHORTAGE", ignoreCase = true) -> Color(0xFFC62828)
                        tallyResult.contains("EXTRA", ignoreCase = true) -> Color(0xFF1565C0)
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Text(
                        text = tallyResult,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = tallyColor,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

private fun parseTotalSales(summaryText: String): Double {
    return parseAmount(summaryText, """Total\s+Sales\s*\(MS\+HSD\):\s*₹?\s*([\d,.]+)""")
}

private fun parseTallyResult(summaryText: String): String {
    try {
        val lines = summaryText.lines()
        val tallyLine = lines.firstOrNull { it.contains("TALLY RESULT:", ignoreCase = true) }
        if (tallyLine != null) {
            return tallyLine.substringAfter("TALLY RESULT:").trim()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return "N/A"
}

private fun parseTallyDiscrepancy(summaryText: String): Double {
    try {
        val lines = summaryText.lines()
        val tallyLine = lines.firstOrNull { it.contains("TALLY RESULT:", ignoreCase = true) } ?: return 0.0
        val text = tallyLine.substringAfter("TALLY RESULT:").trim()
        if (text.contains("SUCCESS", ignoreCase = true)) {
            return 0.0
        }
        val isShortage = text.contains("SHORTAGE", ignoreCase = true)
        val numStr = text.replace(Regex("[^0-9.-]"), "")
        val rawValue = numStr.toDoubleOrNull() ?: 0.0
        return if (isShortage) {
            if (rawValue > 0.0) -rawValue else rawValue
        } else {
            if (rawValue < 0.0) -rawValue else rawValue
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return 0.0
}

private fun parseAmount(text: String, pattern: String): Double {
    try {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        if (match != null) {
            val rawNum = match.groupValues[1].replace(",", "").trim()
            return rawNum.toDoubleOrNull() ?: 0.0
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return 0.0
}

private fun parseMsNetSalesLitres(summaryText: String): Double {
    return parseAmount(summaryText, """MS\s+Net\s+Sales:\s*([\d,.]+)\s*L""")
}

private fun parseHsdNetSalesLitres(summaryText: String): Double {
    return parseAmount(summaryText, """HSD\s+Net\s+Sales:\s*([\d,.]+)\s*L""")
}

private fun parseMsTestingLitres(summaryText: String): Double {
    val msMatch = Regex("""MS\s+Testing\s+Deduction:\s*-\s*([\d,.]+)""").find(summaryText)
    if (msMatch != null) {
        return msMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
    }
    val lines = summaryText.lines()
    var msTesting = 0.0
    var insideNoz1 = false
    for (line in lines) {
        if (line.contains("Noz 1", ignoreCase = true) && line.contains("MS", ignoreCase = true)) {
            insideNoz1 = true
            continue
        }
        if (line.contains("Noz 2", ignoreCase = true)) {
            insideNoz1 = false
        }
        if (insideNoz1 && line.contains("Testing Deduction:", ignoreCase = true)) {
            val amt = parseAmount(line, """Testing\s+Deduction:\s*-\s*([\d,.]+)""")
            if (amt > 0.0) {
                msTesting = amt
            }
        }
    }
    return msTesting
}

private fun parseHsdTestingLitres(summaryText: String): Double {
    val hsdMatch = Regex("""HSD\s+Testing\s+Deduction:\s*-\s*([\d,.]+)""").find(summaryText)
    if (hsdMatch != null) {
        return hsdMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
    }
    val lines = summaryText.lines()
    var hsdTesting = 0.0
    var insideNoz2 = false
    for (line in lines) {
        if (line.contains("Noz 2", ignoreCase = true) && (line.contains("HSD", ignoreCase = true) || line.contains("DSL", ignoreCase = true))) {
            insideNoz2 = true
            continue
        }
        if (line.contains("Grand Total", ignoreCase = true) || line.contains("Total Testing", ignoreCase = true)) {
            insideNoz2 = false
        }
        if (insideNoz2 && line.contains("Testing Deduction:", ignoreCase = true)) {
            val amt = parseAmount(line, """Testing\s+Deduction:\s*-\s*([\d,.]+)""")
            if (amt > 0.0) {
                hsdTesting = amt
            }
        }
    }
    return hsdTesting
}

private fun parseActiveNozzles(summaryText: String): List<String> {
    val activeList = mutableListOf<String>()
    summaryText.lines().forEach { line ->
        if (line.contains("Op:") && line.contains("Cl:") && (line.contains("MS", ignoreCase = true) || line.contains("HSD", ignoreCase = true))) {
            val label = line.substringBefore(":").trim()
            activeList.add(label)
        }
    }
    return activeList
}

private fun parseDetailedExpenses(summaryText: String): List<Pair<String, Double>> {
    val list = mutableListOf<Pair<String, Double>>()
    val lines = summaryText.lines()
    var insideExpenses = false
    for (line in lines) {
        if (line.contains("Expenses (Kharch):", ignoreCase = true)) {
            insideExpenses = true
            continue
        }
        if (insideExpenses) {
            val trimmed = line.trim()
            if (trimmed.contains("Total Expenses:", ignoreCase = true) || 
                trimmed.contains("Credit (Udhar):", ignoreCase = true) || 
                trimmed.startsWith("---") || 
                trimmed.contains("EXPECTED NET CASH BAL", ignoreCase = true) ||
                trimmed.contains("TALLY RESULT:", ignoreCase = true)) {
                insideExpenses = false
                continue
            }
            if (trimmed.startsWith("-")) {
                try {
                    val parts = trimmed.substring(1).split(":")
                    if (parts.size >= 2) {
                        val desc = parts[0].trim()
                        val amtStr = parts[1].replace("-", "").replace("₹", "").replace(",", "").trim()
                        val amt = amtStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            list.add(Pair(desc, amt))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    return list
}

private fun parseDetailedUdhar(summaryText: String): List<Pair<String, Double>> {
    val list = mutableListOf<Pair<String, Double>>()
    val lines = summaryText.lines()
    var insideUdhar = false
    for (line in lines) {
        if (line.contains("Credit (Udhar):", ignoreCase = true)) {
            insideUdhar = true
            continue
        }
        if (insideUdhar) {
            val trimmed = line.trim()
            if (trimmed.contains("Total Udhar:", ignoreCase = true) || 
                trimmed.contains("Expenses (Kharch):", ignoreCase = true) || 
                trimmed.startsWith("---") || 
                trimmed.contains("EXPECTED NET CASH BAL", ignoreCase = true) ||
                trimmed.contains("TALLY RESULT:", ignoreCase = true)) {
                insideUdhar = false
                continue
            }
            if (trimmed.startsWith("-")) {
                try {
                    val parts = trimmed.substring(1).split(":")
                    if (parts.size >= 2) {
                        val rawDesc = parts[0].trim()
                        val desc = if (rawDesc.contains("(")) rawDesc.substringBefore("(").trim() else rawDesc
                        val amtStr = parts[1].replace("-", "").replace("₹", "").replace(",", "").trim()
                        val amt = amtStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            list.add(Pair(desc, amt))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    return list
}

private fun parseDetailedRecoveries(summaryText: String): List<Pair<String, Double>> {
    val list = mutableListOf<Pair<String, Double>>()
    val lines = summaryText.lines()
    var insideRecoveries = false
    for (line in lines) {
        if (line.contains("Udhari Jama (Recoveries):", ignoreCase = true)) {
            insideRecoveries = true
            continue
        }
        if (insideRecoveries) {
            val trimmed = line.trim()
            if (trimmed.contains("Total Recoveries Added:", ignoreCase = true) || 
                trimmed.contains("GRAND TOTAL:", ignoreCase = true) || 
                trimmed.startsWith("---") || 
                trimmed.contains("EXPECTED NET CASH BAL", ignoreCase = true) ||
                trimmed.contains("TALLY RESULT:", ignoreCase = true)) {
                insideRecoveries = false
                continue
            }
            if (trimmed.startsWith("+")) {
                try {
                    val parts = trimmed.substring(1).split(":")
                    if (parts.size >= 2) {
                        val rawDesc = parts[0].trim()
                        val desc = if (rawDesc.contains("(")) rawDesc.substringBefore("(").trim() else rawDesc
                        val amtStr = parts[1].replace("+", "").replace("₹", "").replace(",", "").trim()
                        val amt = amtStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            list.add(Pair(desc, amt))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    return list
}

private fun printDailySalesAuditPdf(
    context: Context,
    date: String,
    audits: List<SavedAudit>,
    nozzleStatsList: List<NozzleStats>,
    totals: Any,
    isShare: Boolean = false
) {
    val webView = WebView(context)
    
    // Load Stock and Density info from Shared Preferences
    val sharedPrefs = context.getSharedPreferences("pump_manager_prefs", Context.MODE_PRIVATE)
    val densityMs = sharedPrefs.getString("density_ms_$date", "") ?: ""
    val densityHsd = sharedPrefs.getString("density_hsd_$date", "") ?: ""
    val stockMs = sharedPrefs.getString("stock_ms_$date", "") ?: ""
    val stockHsd = sharedPrefs.getString("stock_hsd_$date", "") ?: ""
    val receiptMs = sharedPrefs.getString("receipt_ms_$date", "") ?: ""
    val receiptHsd = sharedPrefs.getString("receipt_hsd_$date", "") ?: ""
    val rateMs = sharedPrefs.getString("rate_ms_$date", "") ?: ""
    val rateHsd = sharedPrefs.getString("rate_hsd_$date", "") ?: ""

    val dStockMs = stockMs.toDoubleOrNull() ?: 0.0
    val dStockHsd = stockHsd.toDoubleOrNull() ?: 0.0
    val dReceiptMs = receiptMs.toDoubleOrNull() ?: 0.0
    val dReceiptHsd = receiptHsd.toDoubleOrNull() ?: 0.0

    val totalMsSalesBeforeTesting = nozzleStatsList.filter { it.label.contains("MS", ignoreCase = true) }.sumOf { it.totalNetVolume }
    val totalHsdSalesBeforeTesting = nozzleStatsList.filter { it.label.contains("HSD", ignoreCase = true) }.sumOf { it.totalNetVolume }

    val totalMsTesting = audits.sumOf { parseMsTestingLitres(it.summaryText) }
    val totalHsdTesting = audits.sumOf { parseHsdTestingLitres(it.summaryText) }

    val totalMsNetSales = totalMsSalesBeforeTesting - totalMsTesting
    val totalHsdNetSales = totalHsdSalesBeforeTesting - totalHsdTesting

    val closingStockMs = dStockMs + dReceiptMs - totalMsNetSales
    val closingStockHsd = dStockHsd + dReceiptHsd - totalHsdNetSales

    // Parse individual totals reflectively or cast safely
    val totalSalesValue = parseAmountFromObj(totals, "sales")
    val totalPhonePe = parseAmountFromObj(totals, "phonePe")
    val totalCards = parseAmountFromObj(totals, "cards")
    val totalCashSubmitted = parseAmountFromObj(totals, "cashSubmitted")
    val totalActualCashCollected = parseAmountFromObj(totals, "actualCashCollected")
    val totalExpenses = parseAmountFromObj(totals, "expenses")
    val totalUdhar = parseAmountFromObj(totals, "udhar")
    val totalRecoveries = parseAmountFromObj(totals, "recoveries")

    val totalTallyDiscrepancy = audits.sumOf { parseTallyDiscrepancy(it.summaryText) }

    // 1. Build Nozzles Rows
    val nozzlesRows = StringBuilder()
    if (nozzleStatsList.isEmpty()) {
        nozzlesRows.append("<tr><td colspan='4' style='text-align:center;'>No nozzle readings saved for this date.</td></tr>")
    } else {
        nozzleStatsList.forEach { stat ->
            val sales = stat.totalNetVolume
            nozzlesRows.append("""
                <tr>
                    <td>${stat.label}</td>
                    <td class="num">${String.format(Locale.getDefault(), "%,.2f", stat.firstOpening)}</td>
                    <td class="num">${String.format(Locale.getDefault(), "%,.2f", stat.lastClosing)}</td>
                    <td class="num" style="font-weight: bold; color: #1a73e8;">${String.format(Locale.getDefault(), "%,.1f", sales)} L</td>
                </tr>
            """.trimIndent())
        }
    }

    // 2. Build detailed transactions lists across all shifts
    val allDetailedExpenses = mutableListOf<Triple<String, Double, String>>() // desc, amount, caName
    val allDetailedUdhar = mutableListOf<Triple<String, Double, String>>()
    val allDetailedRecoveries = mutableListOf<Triple<String, Double, String>>()

    // 3. Build Shifts list with MS+HSD in litres and Active Nozzles
    val shiftsRows = StringBuilder()
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    audits.forEach { audit ->
        val ca = if (audit.caName.isNotBlank()) audit.caName else "CA"
        
        // Parse details for detailed sections
        parseDetailedExpenses(audit.summaryText).forEach { (desc, amt) ->
            allDetailedExpenses.add(Triple(desc, amt, ca))
        }
        parseDetailedUdhar(audit.summaryText).forEach { (desc, amt) ->
            allDetailedUdhar.add(Triple(desc, amt, ca))
        }
        parseDetailedRecoveries(audit.summaryText).forEach { (desc, amt) ->
            allDetailedRecoveries.add(Triple(desc, amt, ca))
        }

        val timeStr = try {
            timeFormat.format(Date(audit.timestamp))
        } catch (e: Exception) {
            ""
        }
        val msLitres = parseMsNetSalesLitres(audit.summaryText)
        val hsdLitres = parseHsdNetSalesLitres(audit.summaryText)
        val msTesting = parseMsTestingLitres(audit.summaryText)
        val hsdTesting = parseHsdTestingLitres(audit.summaryText)
        val activeNozzles = parseActiveNozzles(audit.summaryText).joinToString(", ")
        val tallyResult = parseTallyResult(audit.summaryText)
        
        val tallyStyle = when {
            tallyResult.contains("SUCCESS", ignoreCase = true) -> "color: #2e7d32; font-weight: bold;"
            tallyResult.contains("SHORTAGE", ignoreCase = true) -> "color: #c62828; font-weight: bold;"
            tallyResult.contains("EXTRA", ignoreCase = true) -> "color: #1565c0; font-weight: bold;"
            else -> ""
        }
        
        val msSalesCell = if (msTesting > 0.0) {
            "${String.format(Locale.getDefault(), "%,.1f", msLitres)} L<br/><small style='color:#c62828; font-size: 7.5px; font-weight: normal;'>Test: ${String.format(Locale.getDefault(), "%,.1f", msTesting)} L</small>"
        } else {
            "${String.format(Locale.getDefault(), "%,.1f", msLitres)} L"
        }
        
        val hsdSalesCell = if (hsdTesting > 0.0) {
            "${String.format(Locale.getDefault(), "%,.1f", hsdLitres)} L<br/><small style='color:#c62828; font-size: 7.5px; font-weight: normal;'>Test: ${String.format(Locale.getDefault(), "%,.1f", hsdTesting)} L</small>"
        } else {
            "${String.format(Locale.getDefault(), "%,.1f", hsdLitres)} L"
        }
        
        shiftsRows.append("""
            <tr>
                <td>$timeStr</td>
                <td><strong>$ca</strong></td>
                <td>${audit.meterNo.ifBlank { "N/A" }}</td>
                <td>${if (activeNozzles.isNotBlank()) activeNozzles else "N/A"}</td>
                <td class="num" style="font-weight: bold; vertical-align: top;">$msSalesCell</td>
                <td class="num" style="font-weight: bold; vertical-align: top;">$hsdSalesCell</td>
                <td class="num" style="$tallyStyle; vertical-align: top;">$tallyResult</td>
            </tr>
        """.trimIndent())
    }

    // Build HTML rows for detailed tables
    val expensesTableRows = StringBuilder()
    if (allDetailedExpenses.isEmpty()) {
        expensesTableRows.append("<tr><td colspan='2' style='text-align:center; color:#888;'>No expenses</td></tr>")
    } else {
        allDetailedExpenses.forEach { (desc, amt, ca) ->
            expensesTableRows.append("""
                <tr>
                    <td>$desc <small style="color:#666; float:right;">($ca)</small></td>
                    <td class="num">₹${String.format(Locale.getDefault(), "%,.2f", amt)}</td>
                </tr>
            """.trimIndent())
        }
        expensesTableRows.append("""
            <tr style="font-weight:bold; background-color:#fafafa;">
                <td>Total</td>
                <td class="num">₹${String.format(Locale.getDefault(), "%,.2f", totalExpenses)}</td>
            </tr>
        """.trimIndent())
    }

    val udharTableRows = StringBuilder()
    if (allDetailedUdhar.isEmpty()) {
        udharTableRows.append("<tr><td colspan='2' style='text-align:center; color:#888;'>No credit sales</td></tr>")
    } else {
        allDetailedUdhar.forEach { (desc, amt, ca) ->
            udharTableRows.append("""
                <tr>
                    <td>$desc <small style="color:#666; float:right;">($ca)</small></td>
                    <td class="num">₹${String.format(Locale.getDefault(), "%,.2f", amt)}</td>
                </tr>
            """.trimIndent())
        }
        udharTableRows.append("""
            <tr style="font-weight:bold; background-color:#fafafa;">
                <td>Total</td>
                <td class="num">₹${String.format(Locale.getDefault(), "%,.2f", totalUdhar)}</td>
            </tr>
        """.trimIndent())
    }

    val recoveriesTableRows = StringBuilder()
    if (allDetailedRecoveries.isEmpty()) {
        recoveriesTableRows.append("<tr><td colspan='2' style='text-align:center; color:#888;'>No recoveries</td></tr>")
    } else {
        allDetailedRecoveries.forEach { (desc, amt, ca) ->
            recoveriesTableRows.append("""
                <tr>
                    <td>$desc <small style="color:#666; float:right;">($ca)</small></td>
                    <td class="num">₹${String.format(Locale.getDefault(), "%,.2f", amt)}</td>
                </tr>
            """.trimIndent())
        }
        recoveriesTableRows.append("""
            <tr style="font-weight:bold; background-color:#fafafa;">
                <td>Total</td>
                <td class="num">₹${String.format(Locale.getDefault(), "%,.2f", totalRecoveries)}</td>
            </tr>
        """.trimIndent())
    }

    val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                @page {
                    size: portrait;
                    margin: 8mm;
                }
                body {
                    font-family: Arial, sans-serif;
                    padding: 0;
                    margin: 0;
                    color: #333;
                    font-size: 10px;
                    line-height: 1.3;
                }
                .header {
                    text-align: center;
                    border-bottom: 2px solid #333;
                    padding-bottom: 6px;
                    margin-bottom: 10px;
                }
                .header h1 {
                    margin: 0 0 2px 0;
                    font-size: 15px;
                    text-transform: uppercase;
                    color: #111;
                }
                .header p {
                    margin: 1px 0;
                    font-size: 9px;
                    color: #556;
                }
                h2 {
                    font-size: 11px;
                    text-transform: uppercase;
                    margin: 10px 0 4px 0;
                    border-bottom: 1px solid #666;
                    padding-bottom: 1px;
                    color: #111;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-bottom: 8px;
                }
                th, td {
                    border: 1px solid #ddd;
                    padding: 4px 5px;
                    text-align: left;
                }
                th {
                    background-color: #f5f5f5;
                    font-weight: bold;
                }
                .num {
                    text-align: right;
                    font-family: 'Courier New', Courier, monospace;
                }
                .ledger-table td {
                    padding: 4px 6px;
                }
                .positive {
                    color: #2e7d32;
                    font-weight: bold;
                }
                .negative {
                    color: #c62828;
                }
                .variance-box {
                    background-color: #fcfcfc;
                    border: 1px dashed #444;
                    padding: 6px;
                    text-align: right;
                    font-weight: bold;
                    margin-top: 6px;
                    font-size: 10px;
                }
                .split-layout {
                    display: flex;
                    justify-content: space-between;
                    gap: 12px;
                    margin-top: 6px;
                    margin-bottom: 6px;
                    page-break-inside: avoid;
                }
                .left-pane {
                    flex: 4;
                }
                .right-pane {
                    flex: 6;
                }
                .row-container {
                    display: flex;
                    justify-content: space-between;
                    gap: 6px;
                }
                .column-3 {
                    flex: 1;
                    font-size: 8.5px;
                }
                .column-3 h3 {
                    font-size: 8.5px;
                    margin: 0 0 3px 0;
                    text-transform: uppercase;
                    border-bottom: 1.5px solid #444;
                    padding-bottom: 1px;
                    color: #111;
                }
                .detail-table th, .detail-table td {
                    padding: 2px 3px;
                    font-size: 8px;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>D R INAMDAR PETROLEUM</h1>
                <p>Consolidated Daily Sales Audit Report</p>
                <p><strong>Date:</strong> $date &nbsp;|&nbsp; <strong>Generated At:</strong> ${SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date())}</p>
            </div>

            <h2>1. Nozzles Consumption Summary</h2>
            <table>
                <thead>
                    <tr>
                        <th>Nozzle Label</th>
                        <th style="text-align:right;">First Opening</th>
                        <th style="text-align:right;">Last Closing</th>
                        <th style="text-align:right;">Sales (L)</th>
                    </tr>
                </thead>
                <tbody>
                    $nozzlesRows
                </tbody>
            </table>

            <h2>2. Density & Inventory Stock Audit</h2>
            <table>
                <thead>
                    <tr>
                        <th>Fuel Type</th>
                        <th style="text-align:right;">Rate</th>
                        <th style="text-align:right;">Morning Density</th>
                        <th style="text-align:right;">Sales before Testing</th>
                        <th style="text-align:right;">Testing Deduction</th>
                        <th style="text-align:right;">Net Sales</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td><strong>MS Petrol</strong></td>
                        <td class="num">${if (rateMs.isNotBlank()) "₹$rateMs" else "N/A"}</td>
                        <td class="num">${if (densityMs.isNotBlank()) densityMs else "N/A"}</td>
                        <td class="num">${String.format(Locale.getDefault(), "%,.1f L", totalMsSalesBeforeTesting)}</td>
                        <td class="num">${String.format(Locale.getDefault(), "%,.1f L", totalMsTesting)}</td>
                        <td class="num" style="font-weight:bold; color:#1a73e8;">${String.format(Locale.getDefault(), "%,.1f L", totalMsNetSales)}</td>
                    </tr>
                    <tr>
                        <td><strong>HSD Diesel</strong></td>
                        <td class="num">${if (rateHsd.isNotBlank()) "₹$rateHsd" else "N/A"}</td>
                        <td class="num">${if (densityHsd.isNotBlank()) densityHsd else "N/A"}</td>
                        <td class="num">${String.format(Locale.getDefault(), "%,.1f L", totalHsdSalesBeforeTesting)}</td>
                        <td class="num">${String.format(Locale.getDefault(), "%,.1f L", totalHsdTesting)}</td>
                        <td class="num" style="font-weight:bold; color:#1a73e8;">${String.format(Locale.getDefault(), "%,.1f L", totalHsdNetSales)}</td>
                    </tr>
                </tbody>
            </table>

            <div class="split-layout">
                <div class="left-pane">
                    <h2>3. Financial Ledger Tally</h2>
                    <table class="ledger-table">
                        <thead>
                            <tr>
                                <th>Financial Factor</th>
                                <th style="text-align:right;">Amount (₹)</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td>Total Sales Value (MS+HSD)</td>
                                <td class="num" style="font-weight:bold;">₹${String.format(Locale.getDefault(), "%,.2f", totalSalesValue)}</td>
                            </tr>
                            <tr>
                                <td class="positive">Udhari Recoveries Added (+)</td>
                                <td class="num positive">₹${String.format(Locale.getDefault(), "%,.2f", totalRecoveries)}</td>
                            </tr>
                            <tr>
                                <td class="negative">PhonePe UPI Deductions (-)</td>
                                <td class="num negative">₹${String.format(Locale.getDefault(), "%,.2f", totalPhonePe)}</td>
                            </tr>
                            <tr>
                                <td class="negative">Card Payments Deduction (-)</td>
                                <td class="num negative">₹${String.format(Locale.getDefault(), "%,.2f", totalCards)}</td>
                            </tr>
                            <tr>
                                <td class="negative">Shift Expenses (Kharch) (-)</td>
                                <td class="num negative">₹${String.format(Locale.getDefault(), "%,.2f", totalExpenses)}</td>
                            </tr>
                            <tr>
                                <td class="negative">Credit Sales (Udhar) (-)</td>
                                <td class="num negative">₹${String.format(Locale.getDefault(), "%,.2f", totalUdhar)}</td>
                            </tr>
                            <tr>
                                <td style="color:#2e7d32; font-weight:bold;">Total Cash Collected</td>
                                <td class="num" style="color:#2e7d32; font-weight:bold;">₹${String.format(Locale.getDefault(), "%,.2f", totalCashSubmitted + totalActualCashCollected)}</td>
                            </tr>
                        </tbody>
                    </table>

                    <div class="variance-box">
                        Tally Result Total: &nbsp; 
                        <span style="${if (totalTallyDiscrepancy == 0.0) "color: #2e7d32; font-weight: bold;" else if (totalTallyDiscrepancy < 0.0) "color: #c62828; font-weight: bold;" else "color: #1565c0; font-weight: bold;"}">
                            ${if (totalTallyDiscrepancy == 0.0) {
                                "SUCCESS (Perfect matching)"
                            } else if (totalTallyDiscrepancy < 0.0) {
                                "SHORTAGE -₹${String.format(Locale.getDefault(), "%,.2f", -totalTallyDiscrepancy)}"
                            } else {
                                "EXTRA CASH +₹${String.format(Locale.getDefault(), "%,.2f", totalTallyDiscrepancy)}"
                            }}
                        </span>
                    </div>
                </div>

                <div class="right-pane">
                    <h2>5. Detailed Shift Transactions</h2>
                    <div class="row-container">
                        <div class="column-3">
                            <h3>Expenses</h3>
                            <table class="detail-table">
                                <thead>
                                    <tr>
                                        <th>Item</th>
                                        <th style="text-align:right;">Amt</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    $expensesTableRows
                                </tbody>
                            </table>
                        </div>
                        <div class="column-3">
                            <h3>Credit (Udhar)</h3>
                            <table class="detail-table">
                                <thead>
                                    <tr>
                                        <th>Name</th>
                                        <th style="text-align:right;">Amt</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    $udharTableRows
                                </tbody>
                            </table>
                        </div>
                        <div class="column-3">
                            <h3>Recovered (Jama)</h3>
                            <table class="detail-table">
                                <thead>
                                    <tr>
                                        <th>Name</th>
                                        <th style="text-align:right;">Amt</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    $recoveriesTableRows
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>

            <h2>4. Saved Shift Logs (Shift Management)</h2>
            <table>
                <thead>
                    <tr>
                        <th>Time</th>
                        <th>CA Name</th>
                        <th>Meter No</th>
                        <th>Active Nozzles</th>
                        <th style="text-align:right;">MS Sales (L)</th>
                        <th style="text-align:right;">HSD Sales (L)</th>
                        <th style="text-align:right;">Tally Result</th>
                    </tr>
                </thead>
                <tbody>
                    $shiftsRows
                </tbody>
            </table>
        </body>
        </html>
    """.trimIndent()

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            if (isShare) {
                try {
                    val cacheDir = context.cacheDir
                    val sanitizedDate = date.replace("/", "-").replace("\\", "-").trim()
                    val pdfFile = java.io.File(cacheDir, "daily sales audit $sanitizedDate.pdf")
                    val printAdapter = webView.createPrintDocumentAdapter("daily sales audit $sanitizedDate")
                    
                    android.print.PrintHelper.savePdf(printAdapter, pdfFile, object : android.print.PrintHelper.PrintCallback {
                        override fun onSuccess() {
                            try {
                                val uri = FileProvider.getUriForFile(context, "com.mypump.cal.fileprovider", pdfFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Daily Sales Audit Report"))
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
                    val jobName = "Daily_Sales_Audit_${date}_${System.currentTimeMillis()}"
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    printManager.print("Daily Sales Audit Report", printAdapter, null)
                } else {
                    Toast.makeText(context, "Printing not supported on this device", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
}

private fun parseAmountFromObj(obj: Any, field: String): Double {
    try {
        val f = obj.javaClass.getDeclaredField(field)
        f.isAccessible = true
        return f.get(obj) as? Double ?: 0.0
    } catch (e: Exception) {
        return 0.0
    }
}
