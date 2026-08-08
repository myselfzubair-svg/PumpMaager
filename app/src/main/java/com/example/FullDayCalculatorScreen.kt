package com.example

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
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
import com.example.database.GeneralNozzleReading
import com.example.database.MsNozzleReading
import com.example.database.HsdNozzleReading
import com.example.database.SavedAudit
import java.text.SimpleDateFormat
import java.util.*

data class NozzleStats(
    val label: String,
    val productName: String,
    val firstOpening: Double,
    val lastClosing: Double,
    val totalTesting: Double,
    val totalNetVolume: Double
)

data class DailyTotals(
    val sales: Double,
    val phonePe: Double,
    val cards: Double,
    val expenses: Double,
    val udhar: Double,
    val recoveries: Double,
    val cashSubmitted: Double,
    val actualCashCollected: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullDayCalculatorScreen(
    onBack: (() -> Unit)? = null,
    date: String = "",
    onLogout: () -> Unit = {},
    adminPhone: String = "",
    phone: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val historyViewModel: HistoryViewModel = viewModel()

    LaunchedEffect(adminPhone) {
        if (adminPhone.isNotBlank()) {
            historyViewModel.setAdminPhone(adminPhone)
        }
    }
    
    // 1. Selected date
    var selectedAuditDate by remember { mutableStateOf(date.ifEmpty { 
        val cal = Calendar.getInstance()
        val d = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))
        val m = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        val y = cal.get(Calendar.YEAR)
        "$d-$m-$y"
    }) }

    val stationProducts = remember { mutableStateListOf<String>() }
    val productConfigMap = remember { mutableStateMapOf<String, Map<String, String>>() }

    // 2. Fetch Data
    val allAudits: List<SavedAudit> by historyViewModel.allAudits.collectAsState(initial = emptyList())
    val allGeneralReadings: List<GeneralNozzleReading> by historyViewModel.allGeneralNozzleReadings.collectAsState(initial = emptyList())
    val allMsReadings: List<MsNozzleReading> by historyViewModel.allMsNozzleReadings.collectAsState(initial = emptyList())
    val allHsdReadings: List<HsdNozzleReading> by historyViewModel.allHsdNozzleReadings.collectAsState(initial = emptyList())

    LaunchedEffect(selectedAuditDate, adminPhone) {
        if (adminPhone.isNotBlank()) {
            val pumpInfo = com.example.database.FirestoreRepository.getPumpInfo(adminPhone)
            val products = pumpInfo?.productNames?.split(",")?.filter { it.isNotBlank() } ?: listOf("MS", "HSD")
            stationProducts.clear()
            stationProducts.addAll(products)

            val dailyData = com.example.database.FirestoreRepository.getDailyPumpData(adminPhone, selectedAuditDate)
            stationProducts.forEach { product ->
                val record = dailyData.find { isSameProduct(it.productName, product) }
                if (record != null) {
                    productConfigMap[product] = mapOf(
                        "density" to record.density.toString(),
                        "rate" to record.rate.toString(),
                        "stock" to record.openingStock.toString()
                    )
                } else {
                    // Try legacy daily_configs
                    val legacyConfig = com.example.database.FirestoreRepository.getDailyConfig(adminPhone, selectedAuditDate)
                    if (isSameProduct(product, "MS")) {
                        productConfigMap[product] = mapOf(
                            "density" to (legacyConfig["density_ms"] ?: ""),
                            "rate" to (legacyConfig["rate_ms"] ?: ""),
                            "stock" to (legacyConfig["stock_ms"] ?: "")
                        )
                    } else if (isSameProduct(product, "HSD")) {
                        productConfigMap[product] = mapOf(
                            "density" to (legacyConfig["density_hsd"] ?: ""),
                            "rate" to (legacyConfig["rate_hsd"] ?: ""),
                            "stock" to (legacyConfig["stock_hsd"] ?: "")
                        )
                    }
                }
            }
        }
    }

    // 3. Filter audits
    val filteredAudits = remember(allAudits, selectedAuditDate) {
        allAudits.filter { it.date == selectedAuditDate }.sortedBy { it.timestamp }
    }

    // 4. Consolidate Nozzles (Generic + Fallback to legacy MS/HSD)
    val consolidatedNozzles = remember(allGeneralReadings, allMsReadings, allHsdReadings, selectedAuditDate) {
        val statsMap = mutableMapOf<String, MutableList<Pair<Double, Double>>>() // label -> list of (opening, closing)
        val testingMap = mutableMapOf<String, Double>() // label -> total testing
        val productMap = mutableMapOf<String, String>() // label -> product name

        val genDay = allGeneralReadings.filter { it.date == selectedAuditDate }
        val msDay = allMsReadings.filter { it.date == selectedAuditDate }
        val hsdDay = allHsdReadings.filter { it.date == selectedAuditDate }
        
        // Merge all sources
        genDay.forEach { r ->
            statsMap.getOrPut(r.nozzleLabel) { mutableListOf() }.add(r.openingReading to r.closingReading)
            testingMap[r.nozzleLabel] = (testingMap[r.nozzleLabel] ?: 0.0) + r.testing
            productMap[r.nozzleLabel] = r.productName
        }
        msDay.forEach { r ->
            if (!productMap.containsKey(r.nozzleLabel)) {
                statsMap.getOrPut(r.nozzleLabel) { mutableListOf() }.add(r.openingReading to r.closingReading)
                testingMap[r.nozzleLabel] = (testingMap[r.nozzleLabel] ?: 0.0) + r.testing
                productMap[r.nozzleLabel] = "MS"
            }
        }
        hsdDay.forEach { r ->
            if (!productMap.containsKey(r.nozzleLabel)) {
                statsMap.getOrPut(r.nozzleLabel) { mutableListOf() }.add(r.openingReading to r.closingReading)
                testingMap[r.nozzleLabel] = (testingMap[r.nozzleLabel] ?: 0.0) + r.testing
                productMap[r.nozzleLabel] = "HSD"
            }
        }

        statsMap.map { (label, readings) ->
            val sortedByOpening = readings.sortedBy { it.first }
            val firstOp = sortedByOpening.firstOrNull()?.first ?: 0.0
            val lastCl = sortedByOpening.lastOrNull()?.second ?: 0.0
            NozzleStats(label, productMap[label] ?: "Unknown", firstOp, lastCl, testingMap[label] ?: 0.0, (lastCl - firstOp).coerceAtLeast(0.0))
        }.sortedBy { it.label }
    }

    // 5. Aggregate Financials (Using robust regex)
    val totals = remember(filteredAudits) {
        var totalSales = 0.0
        var totalPhonePe = 0.0
        var totalCards = 0.0
        var totalExpenses = 0.0
        var totalUdhar = 0.0
        var totalRecoveries = 0.0
        var totalCashSubmitted = 0.0
        var totalActualCashCollected = 0.0

        filteredAudits.forEach { audit ->
            val text = audit.summaryText
            // Robust parsing patterns for multiple generations of report formats
            totalSales += parseAmount(text, """(?:Total\s+Fuel\s+Sales|Total\s+Sales\s*\(MS\+HSD\)):\s*₹?\s*([\d,.]+)""")
            totalPhonePe += parseAmount(text, """PhonePe\s+Deduction:\s*-?₹?\s*([\d,.]+)""")
            totalCards += parseAmount(text, """Cards\s+Deduction:\s*-?₹?\s*([\d,.]+)""")
            totalExpenses += parseAmount(text, """Total\s+Expenses:\s*-?₹?\s*([\d,.]+)""")
            totalUdhar += parseAmount(text, """Total\s+Udhar:\s*-?₹?\s*([\d,.]+)""")
            totalRecoveries += parseAmount(text, """Total\s+Recoveries\s+Added:\s*\+?₹?\s*([\d,.]+)""")
            totalCashSubmitted += audit.cashSubmitted
            totalActualCashCollected += audit.actualCashCollected
        }
        
        DailyTotals(
            sales = totalSales,
            phonePe = totalPhonePe,
            cards = totalCards,
            expenses = totalExpenses,
            udhar = totalUdhar,
            recoveries = totalRecoveries,
            cashSubmitted = totalCashSubmitted,
            actualCashCollected = totalActualCashCollected
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(LanguageManager.translate("Daily Sales Audit", "दैनिक बिक्री ऑडिट"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = { IconButton(onClick = { onBack?.invoke() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Logout") } }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Date Selector
            item {
                Card(modifier = Modifier.fillMaxWidth().clickable {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(context, { _, y, m, d -> selectedAuditDate = String.format("%02d-%02d-%04d", d, m + 1, y) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(LanguageManager.translate("Active Audit Date", "सक्रिय ऑडिट तिथि"), style = MaterialTheme.typography.labelMedium)
                            Text(selectedAuditDate, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Text(LanguageManager.translate("Change", "बदलें"), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Print/Share Buttons
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { printDailySalesAuditPdf(context, selectedAuditDate, filteredAudits, consolidatedNozzles, stationProducts, productConfigMap, totals, false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Print, null); Spacer(Modifier.width(6.dp)); Text("PRINT")
                    }
                    Button(onClick = { printDailySalesAuditPdf(context, selectedAuditDate, filteredAudits, consolidatedNozzles, stationProducts, productConfigMap, totals, true) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                        Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("SHARE")
                    }
                }
            }

            // Financial Summary
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Consolidated Ledger Tally", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        FinancialFactorRow("Total Fuel Sales", totals.sales, MaterialTheme.colorScheme.onSurface)
                        FinancialFactorRow("Recoveries (+)", totals.recoveries, Color(0xFF2E7D32))
                        FinancialFactorRow("PhonePe (-)", totals.phonePe, Color(0xFFC62828))
                        FinancialFactorRow("Cards (-)", totals.cards, Color(0xFFC62828))
                        FinancialFactorRow("Expenses (-)", totals.expenses, Color(0xFFC62828))
                        FinancialFactorRow("Credit Sales (-)", totals.udhar, Color(0xFFC62828))
                        HorizontalDivider()
                        val diff = (totals.cashSubmitted + totals.actualCashCollected) - (totals.sales + totals.recoveries - totals.phonePe - totals.cards - totals.expenses - totals.udhar)
                        FinancialFactorRow("Final Tally Difference", diff, if (Math.abs(diff) < 0.1) Color(0xFF2E7D32) else Color(0xFFC62828))
                    }
                }
            }

            // Product-Wise Density & Stock loop
            items(stationProducts) { product ->
                val config = productConfigMap[product] ?: emptyMap()
                val isMs = isSameProduct(product, "MS")
                val isHsd = isSameProduct(product, "HSD")
                val badgeColor = when {
                    isMs -> MaterialTheme.colorScheme.primary
                    isHsd -> MaterialTheme.colorScheme.tertiary
                    else -> Color(0xFF8B5CF6)
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = product,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
                        
                        // systematic info
                        val productNozzles = consolidatedNozzles.filter { n -> 
                            isSameProduct(n.productName, product)
                        }
                        
                        val prodOpening = productNozzles.sumOf { it.firstOpening }
                        val prodClosing = productNozzles.sumOf { it.lastClosing }
                        val prodGross = productNozzles.sumOf { it.totalNetVolume }
                        val prodTesting = productNozzles.sumOf { it.totalTesting }
                        val prodNet = (prodGross - prodTesting).coerceAtLeast(0.0)
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total Opening", style = MaterialTheme.typography.labelSmall)
                                Text(formatDouble(prodOpening), style = MaterialTheme.typography.bodySmall)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total Closing", style = MaterialTheme.typography.labelSmall)
                                Text(formatDouble(prodClosing), style = MaterialTheme.typography.bodySmall)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sales (L)", style = MaterialTheme.typography.labelSmall)
                                Text(formatDouble(prodGross) + " L", fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Density", style = MaterialTheme.typography.labelSmall)
                                Text(config["density"]?.ifEmpty { "--" } ?: "--", fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Rate", style = MaterialTheme.typography.labelSmall)
                                Text("₹${config["rate"]?.ifEmpty { "--" } ?: "--"}", fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Op Stock", style = MaterialTheme.typography.labelSmall)
                                Text("${config["stock"]?.ifEmpty { "--" } ?: "--"} L", fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Testing", style = MaterialTheme.typography.labelSmall)
                                Text("-${formatDouble(prodTesting)} L", color = Color(0xFFC62828))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Net Qty", style = MaterialTheme.typography.labelSmall)
                                Text("${formatDouble(prodNet)} L", fontWeight = FontWeight.Bold, color = badgeColor)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                val prodRate = config["rate"]?.toDoubleOrNull() ?: 0.0
                                Text("Net Value", style = MaterialTheme.typography.labelSmall)
                                Text("₹${formatDouble(prodNet * prodRate)}", fontWeight = FontWeight.Black, color = badgeColor)
                            }
                        }
                    }
                }
            }

            // Shifts Log
            item { Text("Saved Shifts Log", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) }
            if (filteredAudits.isEmpty()) {
                item { Text("No shifts found for this date.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            } else {
                items(filteredAudits) { audit -> ShiftAuditMiniCard(audit) }
            }
        }
    }
}

@Composable
fun FinancialFactorRow(label: String, amount: Double, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("₹${String.format(Locale.getDefault(), "%,.2f", amount)}", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
fun ShiftAuditMiniCard(audit: SavedAudit) {
    Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(audit.caName.ifBlank { "Shift CA" }, fontWeight = FontWeight.Bold)
            val resultLine = audit.summaryText.lines().firstOrNull { it.contains("TALLY RESULT:") }
            Text(resultLine?.substringAfter("RESULT:") ?: "Pending", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun parseAmount(text: String, pattern: String): Double {
    return Regex(pattern, RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
}

private fun printDailySalesAuditPdf(
    context: Context,
    date: String,
    audits: List<SavedAudit>,
    nozzleStatsList: List<NozzleStats>,
    products: List<String>,
    productConfigMap: Map<String, Map<String, String>>,
    totals: DailyTotals,
    isShare: Boolean
) {
    val webView = WebView(context)

    // Systematic Product Summary for PDF
    val productSummaryHtml = products.map { product ->
        val config = productConfigMap[product] ?: emptyMap()
        
        val productNozzles = nozzleStatsList.filter { n -> 
            isSameProduct(n.productName, product)
        }
        
        val prodOpening = productNozzles.sumOf { it.firstOpening }
        val prodClosing = productNozzles.sumOf { it.lastClosing }
        val prodGross = productNozzles.sumOf { it.totalNetVolume }
        val prodTesting = productNozzles.sumOf { it.totalTesting }
        val prodNet = (prodGross - prodTesting).coerceAtLeast(0.0)
        val prodRate = config["rate"]?.toDoubleOrNull() ?: 0.0
        val prodValue = prodNet * prodRate

        """
        <div style="margin-bottom: 15px; border: 1px solid #ccc; padding: 10px; border-radius: 5px;">
            <h4 style="margin-top: 0; color: #1a73e8;">$product Summary</h4>
            <table style="margin-bottom: 5px;">
                <tr>
                    <td>Total Nozzles: ${productNozzles.size}</td>
                    <td>Opening: ${formatDouble(prodOpening)}</td>
                    <td>Closing: ${formatDouble(prodClosing)}</td>
                </tr>
                <tr>
                    <td>Gross Sales: <b>${formatDouble(prodGross)} L</b></td>
                    <td>Testing: <span style="color:red;">-${formatDouble(prodTesting)} L</span></td>
                    <td>Net Sales: <b>${formatDouble(prodNet)} L</b></td>
                </tr>
                <tr>
                    <td>Morning Density: ${config["density"] ?: "N/A"}</td>
                    <td>Rate: ₹${formatDouble(prodRate)}/L</td>
                    <td>Net Value: <b style="color: #1a73e8;">₹${formatDouble(prodValue)}</b></td>
                </tr>
            </table>
        </div>
        """.trimIndent()
    }.joinToString("")

    val htmlContent = """
        <html>
        <head>
            <style>
                body { font-family: Arial; font-size: 10px; }
                table { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
                th, td { border: 1px solid #ddd; padding: 5px; }
                th { background-color: #f2f2f2; }
                .num { text-align: right; }
                h1, h2 { text-align: center; }
            </style>
        </head>
        <body>
            <h1>D R INAMDAR PETROLEUM</h1>
            <h2>Daily Sales Audit Report - $date</h2>
            
            <h3>1. Systematic Product Reconciliation</h3>
            $productSummaryHtml

            <h3>2. Individual Nozzle Consumption</h3>
            <table>
                <thead><tr><th>Nozzle</th><th>Opening</th><th>Closing</th><th>Net Sales</th></tr></thead>
                <tbody>
                    ${nozzleStatsList.joinToString("") { stat ->
                        "<tr><td>${stat.label}</td><td class='num'>${stat.firstOpening}</td><td class='num'>${stat.lastClosing}</td><td class='num'><b>${stat.totalNetVolume} L</b></td></tr>"
                    }}
                </tbody>
            </table>
            
            <h3>3. Consolidated Financial Ledger</h3>
            <table>
                <tr><td>Total Fuel Sales Revenue</td><td class="num">₹${formatDouble(totals.sales)}</td></tr>
                <tr><td>Total Recoveries (Jama)</td><td class="num">₹${formatDouble(totals.recoveries)}</td></tr>
                <tr><td>Total PhonePe Deductions</td><td class="num">-₹${formatDouble(totals.phonePe)}</td></tr>
                <tr><td>Total Card Deductions</td><td class="num">-₹${formatDouble(totals.cards)}</td></tr>
                <tr><td>Total Expenses (Kharch)</td><td class="num">-₹${formatDouble(totals.expenses)}</td></tr>
                <tr><td>Total Credit Sales (Udhar)</td><td class="num">-₹${formatDouble(totals.udhar)}</td></tr>
                <tr style="background-color:#eee; font-weight:bold;">
                    <td>Net Station Revenue</td>
                    <td class="num">₹${formatDouble(totals.sales + totals.recoveries - totals.phonePe - totals.cards - totals.expenses - totals.udhar)}</td>
                </tr>
            </table>

            <h3>4. Shift Logs Detail</h3>
            <table>
                <thead><tr><th>Time</th><th>CA Name</th><th>Summary Result</th></tr></thead>
                <tbody>
                    ${audits.joinToString("") { audit ->
                        val result = audit.summaryText.lines().firstOrNull { it.contains("TALLY RESULT:") }?.substringAfter("RESULT:") ?: ""
                        "<tr><td>${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(audit.timestamp))}</td><td>${audit.caName}</td><td>$result</td></tr>"
                    }}
                </tbody>
            </table>
        </body>
        </html>
    """.trimIndent()

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "Audit_$date"
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            if (isShare) {
                // Shared implementation omitted for brevity, usually save to file then share
            } else {
                printManager.print(jobName, printAdapter, null)
            }
        }
    }
    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
}
