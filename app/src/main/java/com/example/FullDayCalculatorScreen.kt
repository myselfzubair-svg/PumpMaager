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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.database.HistoryViewModel
import com.example.database.GeneralNozzleReading
import com.example.database.MsNozzleReading
import com.example.database.HsdNozzleReading
import com.example.database.SavedAudit
import com.example.database.TtReceiptEntry
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

data class CaShiftStats(
    val caName: String,
    val productVolumes: Map<String, Double>,
    val shortage: Double,
    val timestamp: Long,
    val udhar: Double = 0.0,
    val kharch: Double = 0.0
)

data class DailyAuditSummary(
    val sales: Double,
    val productSales: List<Triple<String, Double, Double>>,
    val recoveries: Double,
    val phonePe: Double,
    val cards: Double,
    val expenses: Double,
    val udhar: Double,
    val totalShortage: Double = 0.0,
    val recoveriesList: List<Pair<String, Double>>,
    val expensesList: List<Pair<String, Double>>,
    val udharList: List<Pair<String, Double>>,
    val shortages: List<CaShiftStats>,
    val cashSubmitted: Double,
    val actualCashCollected: Double,
    val lineItems: List<com.example.database.AuditLineItem> = emptyList(),
    // Integrated Manager Report Fields
    val managerReport: SavedAudit? = null,
    val managerTransactions: List<com.example.database.ManagerTransaction> = emptyList(),
    val reconciledDates: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullDayCalculatorScreen(
    onBack: (() -> Unit)? = null,
    date: String = "",
    onLogout: () -> Unit = {},
    adminPhone: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val historyViewModel: HistoryViewModel = viewModel()
    
    // 1. Selected date
    var selectedAuditDate by remember { mutableStateOf(date.ifEmpty { 
        val cal = Calendar.getInstance()
        val d = String.format(Locale.US, "%02d", cal.get(Calendar.DAY_OF_MONTH))
        val m = String.format(Locale.US, "%02d", cal.get(Calendar.MONTH) + 1)
        val y = cal.get(Calendar.YEAR)
        "$d-$m-$y"
    }) }

    val stationProducts = remember { mutableStateListOf<String>() }
    val productConfigMap = remember { mutableStateMapOf<String, Map<String, String>>() }
    var pumpName by remember { mutableStateOf("D R INAMDAR PETROLEUM") }
    var ttEntries by remember { mutableStateOf<List<TtReceiptEntry>>(emptyList()) }
    var allRegisteredNozzles by remember { mutableStateOf<List<com.example.database.RegisteredNozzle>>(emptyList()) }
    var managerTransactionsForAudit by remember { mutableStateOf<List<com.example.database.ManagerTransaction>>(emptyList()) }
    var managerReportForAudit by remember { mutableStateOf<SavedAudit?>(null) }

    // 2. Fetch Data
    val allAudits: List<SavedAudit> by historyViewModel.allAudits.collectAsState(initial = emptyList())
    val allGeneralReadings: List<GeneralNozzleReading> by historyViewModel.allGeneralNozzleReadings.collectAsState(initial = emptyList())
    val allMsReadings: List<MsNozzleReading> by historyViewModel.allMsNozzleReadings.collectAsState(initial = emptyList())
    val allHsdReadings: List<HsdNozzleReading> by historyViewModel.allHsdNozzleReadings.collectAsState(initial = emptyList())
    val allAuditLineItems by historyViewModel.allAuditLineItems.collectAsState(initial = emptyList())

    LaunchedEffect(adminPhone) {
        if (adminPhone.isNotBlank()) {
            historyViewModel.setAdminPhone(adminPhone)
        }
    }

    val filteredAudits = remember(allAudits, selectedAuditDate) {
        allAudits.filter { it.date == selectedAuditDate }.sortedBy { it.timestamp }
    }

    LaunchedEffect(selectedAuditDate, adminPhone, allAudits) {
        if (adminPhone.isNotBlank()) {
            val pumpInfo = com.example.database.SupabaseRepository.getPumpInfo(adminPhone)
            pumpName = pumpInfo?.pumpName ?: "D R INAMDAR PETROLEUM"
            val products = pumpInfo?.productNames?.split(",")?.filter { it.isNotBlank() } ?: listOf("MS", "HSD")
            stationProducts.clear()
            stationProducts.addAll(products)

            val dailyData = com.example.database.SupabaseRepository.getDailyPumpData(adminPhone, selectedAuditDate)
            stationProducts.forEach { product ->
                val record = dailyData.find { isSameProduct(it.productName, product) }
                if (record != null) {
                    productConfigMap[product] = mapOf(
                        "density" to record.density.toString(),
                        "rate" to record.rate.toString(),
                        "stock" to record.openingStock.toString()
                    )
                } else {
                    productConfigMap[product] = mapOf("density" to "0.0", "rate" to "0.0", "stock" to "0.0")
                }
            }

            ttEntries = com.example.database.SupabaseRepository.getTtEntriesByDate(adminPhone, selectedAuditDate)
            allRegisteredNozzles = com.example.database.SupabaseRepository.getRegisteredNozzles(adminPhone)

            // Finding the Manager's Report:
            // 1. By exact date match
            // 2. By reconciliation link from any CA shift of this day
            val reportByDate = allAudits.find { it.date.trim() == selectedAuditDate.trim() && it.auditType == "Manager's Report" }
            val reportByLink = if (reportByDate == null) {
                val linkedId = filteredAudits.firstOrNull { it.reconciledInManagerReportId != null }?.reconciledInManagerReportId
                if (linkedId != null) allAudits.find { it.reportId == linkedId } else null
            } else null
            
            val foundReport = reportByDate ?: reportByLink
            
            if (foundReport != null) {
                val txs = com.example.database.SupabaseRepository.getManagerTransactionsByReportId(adminPhone, foundReport.reportId)
                // Update both atomically to avoid inconsistent aggregateData
                managerTransactionsForAudit = txs
                managerReportForAudit = foundReport
            } else {
                managerReportForAudit = null
                managerTransactionsForAudit = emptyList()
            }
        }
    }

    // 4. Consolidate Nozzles
    val consolidatedNozzles = remember(allGeneralReadings, allMsReadings, allHsdReadings, selectedAuditDate, allRegisteredNozzles) {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val selectedDateObj = try { sdf.parse(selectedAuditDate) } catch (e: Exception) { null }
        
        val allReadings = mutableListOf<GeneralNozzleReading>()
        allGeneralReadings.forEach { allReadings.add(it) }
        allMsReadings.forEach { 
            allReadings.add(GeneralNozzleReading(0, it.ownerAdminPhone, "MS", it.nozzleLabel, it.openingReading, it.closingReading, it.testing, it.caName, it.phone, it.udhar, it.kharch, it.udhariJama, it.msSales, it.timestamp, it.date, it.reportId))
        }
        allHsdReadings.forEach {
            allReadings.add(GeneralNozzleReading(0, it.ownerAdminPhone, "HSD", it.nozzleLabel, it.openingReading, it.closingReading, it.testing, it.caName, it.phone, it.udhar, it.kharch, it.udhariJama, it.hsdSales, it.timestamp, it.date, it.reportId))
        }

        val todayLabels = allReadings.filter { it.date == selectedAuditDate }.map { it.nozzleLabel.trim() }.distinct()
        
        todayLabels.map { label ->
            val todayShifts = allReadings.filter { it.date == selectedAuditDate && it.nozzleLabel.trim() == label }
            val productName = todayShifts.firstOrNull()?.productName ?: "Unknown"
            val lastCl = todayShifts.maxOfOrNull { it.closingReading } ?: 0.0
            val beforeToday = allReadings.filter { r ->
                val rDate = try { sdf.parse(r.date) } catch (e: Exception) { null }
                rDate != null && selectedDateObj != null && rDate.before(selectedDateObj) && r.nozzleLabel.trim() == label
            }
            val firstOp = if (beforeToday.isNotEmpty()) {
                beforeToday.maxBy { it.timestamp }.closingReading
            } else {
                todayShifts.minOfOrNull { it.openingReading } ?: 0.0
            }
            val totalTesting = todayShifts.sumOf { it.testing }
            val dayGrossSales = (lastCl - firstOp).coerceAtLeast(0.0)
            NozzleStats(label, productName, firstOp, lastCl, totalTesting, dayGrossSales)
        }.sortedBy { extractNozzleNumber(it.label) }
    }

    val aggregateData = remember(filteredAudits, allAuditLineItems, selectedAuditDate, productConfigMap.toMap(), consolidatedNozzles, managerReportForAudit, managerTransactionsForAudit) {
        var totalPhonePe = 0.0
        var totalCards = 0.0
        var totalCashSubmitted = 0.0
        var totalActualCashCollected = 0.0
        val legacyLineItems = mutableListOf<com.example.database.AuditLineItem>()
        val caAudits = filteredAudits.filter { it.auditType != "Manager's Report" }

        caAudits.forEach { audit ->
            val text = audit.summaryText
            totalPhonePe += parseAmount(text, """PhonePe\s+Deduction:\s*-?₹?\s*([\d,.]+)""")
            totalCards += parseAmount(text, """Cards\s+Deduction:\s*-?₹?\s*([\d,.]+)""")
            
            // Cash aggregation with legacy fallback
            var submitted = audit.cashSubmitted
            var collected = audit.actualCashCollected
            if (submitted <= 0.0) {
                submitted = parseAmount(text, """Cash Submitted:\s*-?₹?\s*([\d,.]+)""")
            }
            if (collected <= 0.0) {
                collected = parseAmount(text, """CASH BALANCE:\s*-?₹?\s*([\d,.]+)""")
                if (collected <= 0.0) {
                    collected = parseAmount(text, """ACTUAL CASH COLLECTED:\s*-?₹?\s*([\d,.]+)""")
                }
            }
            totalCashSubmitted += submitted
            totalActualCashCollected += collected

            if (audit.reportId.isEmpty()) {
                parseListItems(text, "Expenses (Kharch):", "Total Expenses:").forEach { (desc, amt) -> legacyLineItems.add(com.example.database.AuditLineItem(0, "", audit.ownerAdminPhone, com.example.database.AuditLineItemType.EXPENSE, "General", desc, amt, audit.date, audit.timestamp, audit.caName)) }
                parseListItems(text, "Credit (Udhar):", "Total Udhar:").forEach { (desc, amt) -> legacyLineItems.add(com.example.database.AuditLineItem(0, "", audit.ownerAdminPhone, com.example.database.AuditLineItemType.CREDIT, desc, "Credit Sale", amt, audit.date, audit.timestamp, audit.caName)) }
                parseListItems(text, "Udhari Jama (Recoveries):", "Total Recoveries Added:").forEach { (desc, amt) -> legacyLineItems.add(com.example.database.AuditLineItem(0, "", audit.ownerAdminPhone, com.example.database.AuditLineItemType.RECOVERY, desc, "Recovery", amt, audit.date, audit.timestamp, audit.caName)) }
            }
        }

        val lineItemsToday = allAuditLineItems.filter { it.date == selectedAuditDate } + legacyLineItems
        val totalShortageAmount = caAudits.sumOf { audit ->
            val expected = parseAmount(audit.summaryText, """EXPECTED NET CASH BAL:\s*₹?\s*([\d,.]+)""")
            val diff = audit.actualCashCollected - expected
            if (diff < 0) -diff else 0.0
        }
        val totalRecoveries = lineItemsToday.filter { it.type == com.example.database.AuditLineItemType.RECOVERY }.sumOf { it.amount }
        val totalExpenses = lineItemsToday.filter { it.type == com.example.database.AuditLineItemType.EXPENSE }.sumOf { it.amount }
        val totalUdharSum = lineItemsToday.filter { it.type == com.example.database.AuditLineItemType.CREDIT }.sumOf { it.amount }

        val productSalesDetails = consolidatedNozzles.groupBy { it.productName }.map { (product, nozzles) ->
            val qty = nozzles.sumOf { (it.totalNetVolume - it.totalTesting).coerceAtLeast(0.0) }
            val rate = productConfigMap[productConfigMap.keys.find { isSameProduct(it, product) }]?.get("rate")?.toDoubleOrNull() ?: 0.0
            Triple(product, qty, rate)
        }

        val caShortages = caAudits.map { audit ->
            val expected = parseAmount(audit.summaryText, """EXPECTED NET CASH BAL:\s*₹?\s*([\d,.]+)""")
            val shiftLineItems = lineItemsToday.filter { it.reportId == audit.reportId || (it.reportId.isEmpty() && it.timestamp == audit.timestamp) }
            CaShiftStats(audit.caName, emptyMap(), audit.actualCashCollected - expected, audit.timestamp, shiftLineItems.filter { it.type == com.example.database.AuditLineItemType.CREDIT }.sumOf { it.amount }, shiftLineItems.filter { it.type == com.example.database.AuditLineItemType.EXPENSE }.sumOf { it.amount })
        }
        
        DailyAuditSummary(
            sales = productSalesDetails.sumOf { it.second * it.third },
            productSales = productSalesDetails,
            recoveries = totalRecoveries,
            phonePe = totalPhonePe,
            cards = totalCards,
            expenses = totalExpenses,
            udhar = totalUdharSum,
            totalShortage = totalShortageAmount,
            recoveriesList = lineItemsToday.filter { it.type == com.example.database.AuditLineItemType.RECOVERY }.map { it.category to it.amount },
            expensesList = lineItemsToday.filter { it.type == com.example.database.AuditLineItemType.EXPENSE }.map { it.description to it.amount },
            udharList = lineItemsToday.filter { it.type == com.example.database.AuditLineItemType.CREDIT }.map { it.category to it.amount },
            shortages = caShortages,
            cashSubmitted = totalCashSubmitted,
            actualCashCollected = totalActualCashCollected,
            lineItems = lineItemsToday,
            managerReport = managerReportForAudit,
            managerTransactions = managerTransactionsForAudit
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(pumpName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                        Text(LanguageManager.translate("Daily Sales Audit", "दैनिक बिक्री ऑडिट"), style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { IconButton(onClick = { onBack?.invoke() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { 
                    IconButton(onClick = { 
                        printDetailedReportPdf(
                            context = context,
                            date = selectedAuditDate,
                            nozzleStats = consolidatedNozzles,
                            products = stationProducts,
                            config = productConfigMap.toMap(),
                            totals = aggregateData,
                            isShare = true,
                            pumpName = pumpName,
                            ttEntries = ttEntries
                        )
                    }) {
                        Icon(Icons.Default.Share, "Share Report")
                    }
                    IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Logout") } 
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Date Selector
            item {
                Card(modifier = Modifier.fillMaxWidth().clickable {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(context, { _, y, m, d -> selectedAuditDate = String.format("%02d-%02d-%04d", d, m + 1, y) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Audit Date", style = MaterialTheme.typography.labelSmall)
                                Text(selectedAuditDate, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                        Text("CHANGE", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // 2. Header Section: Stock (Left) | Name (Center) | Inventory (Right)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Opening Stock
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("STOCK (OP)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            stationProducts.forEach { product ->
                                val stock = productConfigMap[product]?.get("stock") ?: "0.0"
                                Text("$product: $stock L", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        
                        // Center: Pump Name
                        Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = pumpName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("Audit Summary", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }

                        // Right: New Inventory
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("INVENTORY (IN)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF2E7D32))
                            stationProducts.forEach { product ->
                                val receipt = ttEntries.sumOf { entry ->
                                    val isMs = isSameProduct(product, "MS") || isSameProduct(product, "Petrol")
                                    val isHsd = isSameProduct(product, "HSD") || isSameProduct(product, "Diesel")
                                    
                                    if (isMs) entry.msInvoiceQuantity
                                    else if (isHsd) entry.hsdInvoiceQuantity
                                    else if (entry.extraProductName != null && isSameProduct(entry.extraProductName, product)) entry.extraInvoiceQuantity
                                    else 0.0
                                }
                                Text("$product: ${formatDouble(receipt)} L", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }

            // 3. Nozzle Table
            item {
                Text("NOZZLE SALES REPORT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp))
                val groupedNozzles = consolidatedNozzles.groupBy { 
                    if (isSameProduct(it.productName, "MS")) "MS" else if (isSameProduct(it.productName, "HSD")) "HSD" else "OTHER"
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    groupedNozzles.forEach { (prod, nozzles) ->
                        val badgeColor = if (prod == "MS") MaterialTheme.colorScheme.primary else if (prod == "HSD") MaterialTheme.colorScheme.tertiary else Color.Gray
                        Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(text = prod, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, color = badgeColor), modifier = Modifier.padding(8.dp))
                                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(8.dp)) {
                                    Text("Nozzle", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Op.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                    Text("Cl.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                    Text("Test", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                    Text("Sales", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                }
                                nozzles.forEach { n ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = n.label, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(formatDouble(n.firstOpening), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                                        Text(formatDouble(n.lastClosing), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                                        Text(formatDouble(n.totalTesting), modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, color = Color.Red)
                                        Text(formatDouble(n.totalNetVolume - n.totalTesting) + "L", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                    }
                                }
                                // Simplified Product Total Row (Litre Only)
                                val prodQty = nozzles.sumOf { (it.totalNetVolume - it.totalTesting).coerceAtLeast(0.0) }
                                HorizontalDivider(color = badgeColor.copy(alpha = 0.2f))
                                Row(modifier = Modifier.fillMaxWidth().background(badgeColor.copy(alpha = 0.05f)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TOTAL $prod", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black))
                                    Text("${formatDouble(prodQty)} L", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = badgeColor)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Financial Layout: Revenues (Left) | Deductions (Right)
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("REVENUES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black))
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                aggregateData.productSales.forEach { (prod, qty, rate) ->
                                    Column {
                                        Text("$prod Total Sales", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF166534))
                                        Text("${formatDouble(qty)} L @ ₹${formatDouble(rate)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text("₹${formatDouble(qty * rate)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                
                                val totalProductSalesAmt = aggregateData.productSales.sumOf { it.second * it.third }
                                HorizontalDivider(color = Color.Green.copy(alpha = 0.2f))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TOTAL PRODUCT SALES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = Color(0xFF166534))
                                    Text("₹${formatDouble(totalProductSalesAmt)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = Color(0xFF166534))
                                }
                                
                                if (aggregateData.recoveriesList.isNotEmpty()) {
                                    HorizontalDivider(color = Color.Green.copy(alpha = 0.2f))
                                    Text("Recoveries (Jama)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Gray)
                                    aggregateData.recoveriesList.forEach { (desc, amt) ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("• $desc", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                            Text("₹${formatDouble(amt)}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                
                                HorizontalDivider(color = Color.Green.copy(alpha = 0.3f))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("GRAND TOTAL REVENUE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black))
                                    Text("₹${formatDouble(aggregateData.sales + aggregateData.recoveries)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black))
                                }
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DEDUCTIONS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black))
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (aggregateData.phonePe > 0) DeductionRow("PhonePe", aggregateData.phonePe)
                                if (aggregateData.cards > 0) DeductionRow("Cards", aggregateData.cards)
                                
                                val shiftExpenses = aggregateData.lineItems.filter { it.type == com.example.database.AuditLineItemType.EXPENSE }
                                if (shiftExpenses.isNotEmpty()) {
                                    HorizontalDivider(color = Color.Red.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                                    Text("EXPENSES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Gray)
                                    shiftExpenses.forEach { item ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(Modifier.weight(1f)) {
                                                Text("• ${item.description}", style = MaterialTheme.typography.bodySmall)
                                                Text("By: ${item.caName}", style = MaterialTheme.typography.labelSmall, color = Color.Gray.copy(alpha = 0.7f))
                                            }
                                            Text("-₹${formatDouble(item.amount)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
                                        }
                                    }
                                    DeductionRow("Total Expenses", aggregateData.expenses, isBold = true)
                                }

                                val shiftCredits = aggregateData.lineItems.filter { it.type == com.example.database.AuditLineItemType.CREDIT }
                                if (shiftCredits.isNotEmpty()) {
                                    HorizontalDivider(color = Color.Red.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                                    Text("UDHARI (CREDIT)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Gray)
                                    shiftCredits.forEach { item ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(Modifier.weight(1f)) {
                                                Text("• ${item.category}: ${item.description}", style = MaterialTheme.typography.bodySmall)
                                                Text("Added by: ${item.caName}", style = MaterialTheme.typography.labelSmall, color = Color.Gray.copy(alpha = 0.7f))
                                            }
                                            Text("-₹${formatDouble(item.amount)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
                                        }
                                    }
                                    DeductionRow("Total Credits", aggregateData.udhar, isBold = true)
                                }

                                if (aggregateData.totalShortage > 0) {
                                    HorizontalDivider(color = Color.Red.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                                    DeductionRow("Total Shortage", aggregateData.totalShortage, isBold = true)
                                }

                                HorizontalDivider(color = Color.Red.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    DeductionRow("Cash Submitted", aggregateData.cashSubmitted)
                                    DeductionRow("Cash Balance", aggregateData.actualCashCollected)
                                    DeductionRow("TOTAL CASH", aggregateData.cashSubmitted + aggregateData.actualCashCollected, isBold = true)
                                }
                                
                                HorizontalDivider(color = Color.Red.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 6.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TOTAL OUT", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black))
                                    Text("₹${formatDouble(aggregateData.phonePe + aggregateData.cards + aggregateData.expenses + aggregateData.udhar + aggregateData.totalShortage + aggregateData.cashSubmitted + aggregateData.actualCashCollected)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black), color = Color.Red)
                                }
                            }
                        }
                    }
                }
            }

            // 4.6 Audit Tally Summary
            item {
                val totalRevenue = aggregateData.sales + aggregateData.recoveries
                val totalOut = aggregateData.phonePe + aggregateData.cards + aggregateData.expenses + aggregateData.udhar + aggregateData.totalShortage + aggregateData.cashSubmitted + aggregateData.actualCashCollected
                val tallyDiff = totalOut - totalRevenue

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (Math.abs(tallyDiff) < 1.0) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                    ),
                    border = BorderStroke(1.dp, if (Math.abs(tallyDiff) < 1.0) Color(0xFF22C55E) else Color(0xFFEF4444))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("STATION AUDIT TALLY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Revenue (Sales + Jama)", style = MaterialTheme.typography.bodyMedium)
                            Text("₹${formatDouble(totalRevenue)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Accounted (Deductions + Cash)", style = MaterialTheme.typography.bodyMedium)
                            Text("₹${formatDouble(totalOut)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Station Balance Difference", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            val label = if (Math.abs(tallyDiff) < 1.0) "PERFECT" else if (tallyDiff < 0) "SHORTAGE" else "SURPLUS"
                            Text("$label: ₹${formatDouble(tallyDiff)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = if (Math.abs(tallyDiff) < 1.0) Color(0xFF166534) else Color(0xFFB91C1C))
                        }
                    }
                }
            }

            // 5. CA Performance
            item {
                Text("INDIVIDUAL CA PERFORMANCE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        aggregateData.shortages.forEach { stats ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stats.caName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                val color = if (Math.abs(stats.shortage) < 1.0) Color(0xFF2E7D32) else if (stats.shortage < 0) Color(0xFFC62828) else Color(0xFFF59E0B)
                                Text(if (Math.abs(stats.shortage) < 1.0) "Perfect" else "₹${formatDouble(stats.shortage)}", color = color, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.3f))
                        }
                        
                        // Total CA Difference Row
                        val totalCaDiff = aggregateData.shortages.sumOf { it.shortage }
                        Row(modifier = Modifier.fillMaxWidth().background(Color.LightGray.copy(alpha = 0.1f)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL CA DIFFERENCE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black))
                            val color = if (Math.abs(totalCaDiff) < 1.0) Color(0xFF2E7D32) else if (totalCaDiff < 0) Color(0xFFC62828) else Color(0xFFF59E0B)
                            Text("₹${formatDouble(totalCaDiff)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = color)
                        }
                    }
                }
            }

            // 6. Manager's Report Integration
            item {
                if (aggregateData.managerReport != null) {
                    ManagerReconciliationCard(aggregateData.managerReport!!, aggregateData.managerTransactions)
                }
            }

            item {
                Button(
                    onClick = { 
                        printDetailedReportPdf(
                            context = context,
                            date = selectedAuditDate,
                            nozzleStats = consolidatedNozzles,
                            products = stationProducts,
                            config = productConfigMap.toMap(),
                            totals = aggregateData,
                            isShare = false,
                            pumpName = pumpName,
                            ttEntries = ttEntries
                        )
                    }, 
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text("GENERATE FULL REPORT")
                }
            }
        }
    }
}

@Composable
fun ManagerReconciliationCard(mr: SavedAudit, txs: List<com.example.database.ManagerTransaction>) {
    Text("SECTION 3: MANAGER'S REPORT RECONCILIATION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, color = Color(0xFF1A237E)))
    Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.5.dp, Color(0xFF1A237E).copy(alpha = 0.3f)), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Report Date:", style = MaterialTheme.typography.bodySmall)
                Text(mr.date, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1A237E)))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Last Settled Point:", style = MaterialTheme.typography.bodySmall)
                Text(mr.lastSettledReportDate ?: "N/A", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FlowRowItemUI("Opening Balance (Carry Forward)", mr.openingBalanceUsed, Color(0xFF64748B))
            
            val dailyParts = mr.dailyCashDetails?.split("; ") ?: emptyList()
            if (dailyParts.isNotEmpty()) {
                Text("System Handover Details:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                dailyParts.forEach {
                    val p = it.split(": ")
                    if (p.size == 2) {
                        Row(Modifier.fillMaxWidth().padding(start = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p[0], style = MaterialTheme.typography.bodySmall)
                            Text(p[1], style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
                FlowRowItemUI("Total System Handover", mr.totalFuelSalesAmount, Color(0xFF2563EB), isBold = true)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            
            val jama = txs.filter { it.type == "MANAGER_JAMA" }
            val loansIn = txs.filter { it.type in listOf("LOAN_BORROWED", "OWNER_BORROWED") }
            val expenses = txs.filter { it.type in listOf("MANAGER_EXPENSE", "SALON_EXPENSE") }
            val udhar = txs.filter { it.type == "MANAGER_UDHAR" }
            val loansOut = txs.filter { it.type in listOf("LOAN_CLEARED", "OWNER_WITHDRAWAL") }
            val deposits = txs.filter { it.type == "BANK_DEPOSIT" }

            @Composable
            fun CatGroup(title: String, list: List<com.example.database.ManagerTransaction>, color: Color, sign: String) {
                if (list.isEmpty()) return
                Text(title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color, modifier = Modifier.padding(top = 4.dp))
                list.forEach { tx ->
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tx.description ?: tx.type.replace("_", " "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("$sign₹${formatDouble(tx.amount)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = color)
                    }
                }
            }

            CatGroup("UDHARI RECOVERED (JAMA):", jama, Color(0xFF15803D), "+")
            CatGroup("LOANS BORROWED:", loansIn, Color(0xFF15803D), "+")
            CatGroup("EXPENSES:", expenses, Color(0xFFC62828), "-")
            CatGroup("UDHARI:", udhar, Color(0xFFC62828), "-")
            CatGroup("LOANS CLEARED:", loansOut, Color(0xFFC62828), "-")

            if (deposits.isNotEmpty()) {
                Text("BANK DEPOSITS:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1E293B), modifier = Modifier.padding(top = 4.dp))
                deposits.forEach { tx ->
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tx.date, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("₹${formatDouble(tx.amount)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
                FlowRowItemUI("Total Bank Deposited", deposits.sumOf { it.amount }, Color(0xFF1E293B), isSubtraction = true)
            }

            HorizontalDivider(thickness = 2.dp, modifier = Modifier.padding(vertical = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("FINAL DIFFERENCE", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black))
                Text("₹${formatDouble(mr.manualDifference)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black), color = if (mr.isSettled) Color(0xFF15803D) else Color(0xFFC62828))
            }
            Text(if (mr.isSettled) "STATUS: SETTLED" else "STATUS: CARRIED FORWARD", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (mr.isSettled) Color(0xFF15803D) else Color(0xFF1E293B))
        }
    }
}

@Composable
fun FlowRowItemUI(label: String, amount: Double, color: Color, isAddition: Boolean = false, isSubtraction: Boolean = false, isBold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (isBold) Color.Black else Color(0xFF64748B), fontSize = 12.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
        Text(text = (if (isAddition) "+" else if (isSubtraction) "-" else "") + "₹${formatDouble(amount)}", color = color, fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun DeductionRow(label: String, amt: Double, isBold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (isBold) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall)
        Text("-₹${formatDouble(amt)}", style = if (isBold) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black) else MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
    }
}

private fun parseAmount(text: String, pattern: String): Double = Regex(pattern, RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

private fun parseListItems(text: String, startTag: String, endTag: String): List<Pair<String, Double>> {
    val list = mutableListOf<Pair<String, Double>>()
    var capture = false
    text.lines().forEach { line ->
        if (line.contains(startTag, ignoreCase = true)) { capture = true; return@forEach }
        if (capture && (line.contains(endTag, ignoreCase = true) || line.startsWith("---"))) { capture = false; return@forEach }
        if (capture && (line.trim().startsWith("+") || line.trim().startsWith("-"))) {
            try {
                val content = line.trim().substring(1).trim()
                val amt = content.substringAfter("₹").replace(",", "").toDoubleOrNull() ?: 0.0
                if (amt > 0) list.add(content.substringBefore(":").trim() to amt)
            } catch (e: Exception) {}
        }
    }
    return list
}

private fun printDetailedReportPdf(
    context: Context, date: String, nozzleStats: List<NozzleStats>, products: List<String>, 
    config: Map<String, Map<String, String>>, totals: DailyAuditSummary, isShare: Boolean, pumpName: String, ttEntries: List<TtReceiptEntry>
) {
    val webView = WebView(context)
    val html = buildDetailedHtml(pumpName, date, nozzleStats, products, config, totals, ttEntries)
    
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val jobName = "${pumpName}_Report_$date"
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            
            if (isShare) {
                try {
                    val cacheDir = context.cacheDir
                    val file = java.io.File(cacheDir, "Station_Report_$date.pdf")
                    android.print.PrintHelper.savePdf(printAdapter, file, object : android.print.PrintHelper.PrintCallback {
                        override fun onSuccess() {
                            val uri = FileProvider.getUriForFile(context, "com.mypump.cal.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Report"))
                        }
                        override fun onFailure(error: String?) {
                            Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                        }
                    })
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } else {
                printManager?.print(jobName, printAdapter, null)
            }
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
}

private fun buildDetailedHtml(pumpName: String, date: String, nozzleStats: List<NozzleStats>, products: List<String>, config: Map<String, Map<String, String>>, totals: DailyAuditSummary, ttEntries: List<TtReceiptEntry>): String {
    val productRows = products.joinToString("") { p ->
        val op = config[p]?.get("stock")?.toDoubleOrNull() ?: 0.0
        val rate = config[p]?.get("rate") ?: "0.0"
        val receipt = ttEntries.sumOf { entry ->
            val isMs = isSameProduct(p, "MS") || isSameProduct(p, "Petrol")
            val isHsd = isSameProduct(p, "HSD") || isSameProduct(p, "Diesel")
            
            if (isMs) entry.msInvoiceQuantity
            else if (isHsd) entry.hsdInvoiceQuantity
            else if (entry.extraProductName != null && isSameProduct(entry.extraProductName, p)) entry.extraInvoiceQuantity
            else 0.0
        }
        "<tr><td>$p</td><td class='num'>${formatDouble(op)} L</td><td class='num'>${formatDouble(receipt)} L</td><td class='num'>${formatDouble(op + receipt)} L</td><td class='num'>₹$rate</td></tr>"
    }

    val nozzleTables = nozzleStats.groupBy { it.productName }.map { (prod, list) ->
        val prodQty = list.sumOf { (it.totalNetVolume - it.totalTesting).coerceAtLeast(0.0) }
        """<h4>$prod NOZZLE REPORT</h4>
        <table><thead><tr><th>Nozzle</th><th>Opening</th><th>Closing</th><th>Testing</th><th>Net Sales</th></tr></thead>
        <tbody>${list.joinToString("") { n -> "<tr><td>${n.label}</td><td class='num'>${formatDouble(n.firstOpening)}</td><td class='num'>${formatDouble(n.lastClosing)}</td><td class='num' style='color:red;'>-${formatDouble(n.totalTesting)}</td><td class='num'><b>${formatDouble(n.totalNetVolume - n.totalTesting)} L</b></td></tr>" }}
        <tr class="total"><td>TOTAL $prod</td><td colspan="4" class="num">${formatDouble(prodQty)} L</td></tr>
        </tbody></table>"""
    }.joinToString("")

    val totalRevenue = totals.sales + totals.recoveries
    val productSalesOnly = totals.productSales.sumOf { it.second * it.third }
    val totalAccounted = totals.phonePe + totals.cards + totals.expenses + totals.udhar + totals.totalShortage + totals.cashSubmitted + totals.actualCashCollected
    val tallyDiff = totalAccounted - totalRevenue

    val auditTally = """
        <div class="grid"><div class="col">
        <h3 style="color:#1a237e; border-bottom: 2px solid #1a237e;">REVENUES</h3><table>
        ${totals.productSales.joinToString("") { "<tr><td><strong>${it.first} Total Sales</strong><br/><small>${formatDouble(it.second)} L @ ₹${formatDouble(it.third)}</small></td><td class='num'>₹${formatDouble(it.second * it.third)}</td></tr>" }}
        <tr class="total" style="background:#e8f5e9;"><td>TOTAL PRODUCT SALES</td><td class="num">₹${formatDouble(productSalesOnly)}</td></tr>
        ${totals.recoveriesList.joinToString("") { "<tr><td>+ Recovery: ${it.first}</td><td class='num'>₹${formatDouble(it.second)}</td></tr>" }}
        <tr class="total" style="font-size:12px; border-top: 2px solid #333;"><td>GRAND TOTAL REVENUE</td><td class="num">₹${formatDouble(totalRevenue)}</td></tr>
        </table></div><div class="col">
        <h3 style="color:#b91c1c; border-bottom: 2px solid #b91c1c;">DEDUCTIONS & REMITTANCE</h3><table>
        ${if (totals.phonePe > 0) "<tr><td>- PhonePe</td><td class='num'>-₹${formatDouble(totals.phonePe)}</td></tr>" else ""}
        ${if (totals.cards > 0) "<tr><td>- Cards</td><td class='num'>-₹${formatDouble(totals.cards)}</td></tr>" else ""}
        
        ${if (totals.expensesList.isNotEmpty()) "<tr><td colspan='2' style='background:#f8f9fa; font-weight:bold; font-size:9px;'>EXPENSES</td></tr>" + 
            totals.lineItems.filter { it.type == com.example.database.AuditLineItemType.EXPENSE }.joinToString("") { "<tr><td>&bull; ${it.description}<br/><small>Added by: ${it.caName}</small></td><td class='num'>-₹${formatDouble(it.amount)}</td></tr>" } + 
            "<tr><td><strong>Total Expenses</strong></td><td class='num'><strong>-₹${formatDouble(totals.expenses)}</strong></td></tr>" else ""}
        
        ${if (totals.udharList.isNotEmpty()) "<tr><td colspan='2' style='background:#f8f9fa; font-weight:bold; font-size:9px;'>UDHARI (CREDIT)</td></tr>" + 
            totals.lineItems.filter { it.type == com.example.database.AuditLineItemType.CREDIT }.joinToString("") { "<tr><td>&bull; ${it.category}: ${it.description}<br/><small>By: ${it.caName}</small></td><td class='num'>-₹${formatDouble(it.amount)}</td></tr>" } + 
            "<tr><td><strong>Total Credits</strong></td><td class='num'><strong>-₹${formatDouble(totals.udhar)}</strong></td></tr>" else ""}

        ${if (totals.totalShortage > 0) "<tr><td>- Shortage</td><td class='num'>-₹${formatDouble(totals.totalShortage)}</td></tr>" else ""}
        
        <tr style="background:#f8f9fa;"><td>&bull; Total Cash Submitted</td><td class='num'>-₹${formatDouble(totals.cashSubmitted)}</td></tr>
        <tr style="background:#f8f9fa;"><td>&bull; Total Cash Balance</td><td class='num'>-₹${formatDouble(totals.actualCashCollected)}</td></tr>
        <tr style="background:#f1f3f4; font-weight:bold;"><td>TOTAL HANDOVER CASH</td><td class='num'>-₹${formatDouble(totals.cashSubmitted + totals.actualCashCollected)}</td></tr>

        <tr class="total"><td>Total Accounted (Out)</td><td class="num">-₹${formatDouble(totalAccounted)}</td></tr>
        <tr class="total" style="background:${if(Math.abs(tallyDiff)<1.0) "#e8f5e9" else "#ffebee"}; font-size:12px; border-top: 2px solid #333;">
            <td>TALLY BALANCE (${if(Math.abs(tallyDiff)<1.0) "PERFECT" else if(tallyDiff < 0) "SHORTAGE" else "SURPLUS"})</td>
            <td class="num">₹${formatDouble(tallyDiff)}</td>
        </tr>
        </table></div></div>"""

    val caPerformance = """<h3>INDIVIDUAL SHIFT PERFORMANCE</h3><table><thead><tr><th>CA Name</th><th class="num">Tally Result</th></tr></thead><tbody>
        ${totals.shortages.joinToString("") { "<tr><td>${it.caName}</td><td class='num'>${if(Math.abs(it.shortage)<1.0) "Perfect" else "₹"+formatDouble(it.shortage)}</td></tr>" }}
        <tr class="total"><td>TOTAL CA DIFFERENCE</td><td class="num">₹${formatDouble(totals.shortages.sumOf { it.shortage })}</td></tr>
        </tbody></table>"""

    val managerSection = if (totals.managerReport != null) {
        val mr = totals.managerReport!!
        val txs = totals.managerTransactions
        fun buildCatTable(title: String, list: List<com.example.database.ManagerTransaction>, sign: String, cls: String): String {
            if (list.isEmpty()) return ""
            return "<h3>$title</h3><table><tbody>" + list.joinToString("") { "<tr><td>${it.description ?: "-"}</td><td class='num $cls'>$sign₹${formatDouble(it.amount)}</td></tr>" } + "</tbody></table>"
        }
        // Formatting dynamic report date for heading
        val fmtDate = try {
            val inputSdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
            val outputSdf = SimpleDateFormat("dd MMMM yyyy", Locale.US)
            inputSdf.parse(mr.date)?.let { outputSdf.format(it) } ?: mr.date
        } catch (e: Exception) { mr.date }

        """<div style="page-break-before: always;"></div><h2 class="center">3. MANAGER'S REPORT ($fmtDate)</h2>
        <h3>OPENING BALANCE</h3><table><tr><td>Carry Forward Amount</td><td class="col-num">₹${formatDouble(mr.openingBalanceUsed)}</td></tr></table>
        <h3>SYSTEM HANDOVER ENTRIES</h3><table><tbody>
        ${mr.dailyCashDetails?.split("; ")?.joinToString("") { try { val parts = it.split(": "); "<tr><td>${parts[0]}</td><td class='num'>${parts[1]}</td></tr>" } catch(e: Exception) { "" } } ?: ""}
        <tr class="total"><td>TOTAL SYSTEM CASH</td><td class="num">₹${formatDouble(mr.totalFuelSalesAmount)}</td></tr></tbody></table>
        
        <h3>CASH ADDITIONS</h3>
        ${buildCatTable("UDHARI RECOVERED (JAMA)", txs.filter { it.type == "MANAGER_JAMA" }, "+", "addition")}
        ${buildCatTable("LOAN BORROWED", txs.filter { it.type in listOf("LOAN_BORROWED", "OWNER_BORROWED") }, "+", "addition")}
        
        <h3>CASH AVAILABLE TO DEPOSIT</h3><table><tr class="total" style="background:#e8eaf6;"><td>TOTAL AVAILABLE</td><td class="num">₹${formatDouble(mr.cashAvailableToDeposit)}</td></tr></table>
        
        <h3>DEDUCTIONS</h3>
        ${buildCatTable("EXPENSES", txs.filter { it.type in listOf("MANAGER_EXPENSE", "SALON_EXPENSE") }, "-", "deduction")}
        ${buildCatTable("UDHARI", txs.filter { it.type == "MANAGER_UDHAR" }, "-", "deduction")}
        ${buildCatTable("LOAN CLEARED", txs.filter { it.type in listOf("LOAN_CLEARED", "OWNER_WITHDRAWAL") }, "-", "deduction")}
        
        <h3>NET CASH BEFORE BANK DEPOSIT</h3><table><tr class="total"><td>NET CASH</td><td class="num">₹${formatDouble(mr.expectedCashBalance)}</td></tr></table>
        
        <h3>BANK DEPOSIT</h3><table><tbody>
        ${txs.filter { it.type == "BANK_DEPOSIT" }.joinToString("") { "<tr><td>${it.date}: ${it.description ?: "Bank Deposit"}</td><td class='num'>₹${formatDouble(it.amount)}</td></tr>" }}
        <tr class="total"><td>TOTAL BANK DEPOSITED</td><td class="num">₹${formatDouble(mr.actualDepositedAmount)}</td></tr></tbody></table>
        
        <h3>DIFFERENCE</h3><table style="background: ${if (mr.isSettled) "#e8f5e9" else "#ffebee"};">
        <tr class="total"><td>DIFFERENCE (${if (mr.isSettled) "SETTLED" else "CARRIED FORWARD"})</td><td class="num">₹${formatDouble(mr.manualDifference)}</td></tr></table>"""
    } else ""

    return """<!DOCTYPE html><html><head><style>
        body { font-family: sans-serif; font-size: 11px; color: #333; }
        .center { text-align: center; } h1, h2, h3 { color: #1a237e; }
        table { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
        th, td { border: 1px solid #eee; padding: 6px; text-align: left; }
        th { background-color: #f8f9fa; font-weight: bold; }
        .num { text-align: right; font-family: monospace; } .total { font-weight: bold; background: #f1f3f4; }
        .grid { display: flex; gap: 20px; } .col { flex: 1; }
        .addition { color: green; } .deduction { color: red; }
    </style></head><body>
        <div class="center"><h1>$pumpName</h1><h3>Detailed Station Report - $date</h3></div>
        <h2 class="center">1. DAILY SALES REPORT</h2>
        <h3>PRODUCT STOCK & RATES</h3>
        <table><thead><tr><th>Product</th><th>Op. Stock</th><th>Receipts</th><th>Total Stock</th><th>Rate</th></tr></thead><tbody>$productRows</tbody></table>
        $nozzleTables
        <div style="page-break-before: always;"></div>
        <h2 class="center">2. DAILY SALES AUDIT REPORT</h2>
        $auditTally
        $caPerformance
        $managerSection
        <div style="margin-top: 30px; text-align: center; color: #999;">
            <p>Report generated by My Application - Fuel Station Manager</p>
        </div>
    </body></html>""".trimIndent()
}
