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
    val lineItems: List<com.example.database.AuditLineItem> = emptyList()
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

    LaunchedEffect(selectedAuditDate, adminPhone) {
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
        }
    }

    val filteredAudits = remember(allAudits, selectedAuditDate) {
        allAudits.filter { it.date == selectedAuditDate }.sortedBy { it.timestamp }
    }

    // 4. Consolidate Nozzles (Generic + Legacy MS/HSD) - CONTINUITY LOGIC
    val consolidatedNozzles = remember(allGeneralReadings, allMsReadings, allHsdReadings, selectedAuditDate, allRegisteredNozzles) {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val selectedDateObj = try { sdf.parse(selectedAuditDate) } catch (e: Exception) { null }
        
        // 1. Gather all readings for this nozzle label
        val allReadings = mutableListOf<GeneralNozzleReading>()
        allGeneralReadings.forEach { allReadings.add(it) }
        allMsReadings.forEach { 
            allReadings.add(GeneralNozzleReading(0, it.ownerAdminPhone, "MS", it.nozzleLabel, it.openingReading, it.closingReading, it.testing, it.caName, it.phone, it.udhar, it.kharch, it.udhariJama, it.msSales, it.timestamp, it.date, it.reportId))
        }
        allHsdReadings.forEach {
            allReadings.add(GeneralNozzleReading(0, it.ownerAdminPhone, "HSD", it.nozzleLabel, it.openingReading, it.closingReading, it.testing, it.caName, it.phone, it.udhar, it.kharch, it.udhariJama, it.hsdSales, it.timestamp, it.date, it.reportId))
        }

        // 2. Identify nozzles active today
        val todayLabels = allReadings.filter { it.date == selectedAuditDate }.map { it.nozzleLabel.trim() }.distinct()
        
        todayLabels.map { label ->
            val todayShifts = allReadings.filter { it.date == selectedAuditDate && it.nozzleLabel.trim() == label }
            val productName = todayShifts.firstOrNull()?.productName ?: "Unknown"
            
            // Closing: Highest closing recorded today
            val lastCl = todayShifts.maxOfOrNull { it.closingReading } ?: 0.0
            
            // Opening: Latest closing recorded before today
            val beforeToday = allReadings.filter { r ->
                val rDate = try { sdf.parse(r.date) } catch (e: Exception) { null }
                rDate != null && selectedDateObj != null && rDate.before(selectedDateObj) && r.nozzleLabel.trim() == label
            }
            
            val firstOp = if (beforeToday.isNotEmpty()) {
                beforeToday.maxBy { it.timestamp }.closingReading
            } else {
                // Fallback to today's first opening or registered initial reading
                todayShifts.minOfOrNull { it.openingReading } ?: 0.0
            }

            val totalTesting = todayShifts.sumOf { it.testing }
            // Total Net Volume for the day = Max Closing Today - Latest Closing Before Today
            // This captures all sales happening today including gaps between shifts
            val dayGrossSales = (lastCl - firstOp).coerceAtLeast(0.0)
            
            NozzleStats(
                label = label,
                productName = productName,
                firstOpening = firstOp,
                lastClosing = lastCl,
                totalTesting = totalTesting,
                totalNetVolume = dayGrossSales
            )
        }.sortedBy { it.label }
    }

    val aggregateData = remember(filteredAudits, allGeneralReadings, allMsReadings, allHsdReadings, allAuditLineItems, selectedAuditDate, productConfigMap.toMap(), consolidatedNozzles) {
        var totalPhonePe = 0.0
        var totalCards = 0.0
        var totalCashSubmitted = 0.0
        var totalActualCashCollected = 0.0
        
        val legacyLineItems = mutableListOf<com.example.database.AuditLineItem>()

        filteredAudits.forEach { audit ->
            val text = audit.summaryText
            totalPhonePe += parseAmount(text, """PhonePe\s+Deduction:\s*-?₹?\s*([\d,.]+)""")
            totalCards += parseAmount(text, """Cards\s+Deduction:\s*-?₹?\s*([\d,.]+)""")
            totalCashSubmitted += audit.cashSubmitted
            totalActualCashCollected += audit.actualCashCollected

            // For old reports (no reportId), parse individual items from text for display
            if (audit.reportId.isEmpty()) {
                val parsedExp = parseListItems(text, "Expenses (Kharch):", "Total Expenses:")
                parsedExp.forEach { (desc, amt) ->
                    legacyLineItems.add(com.example.database.AuditLineItem(0, "", audit.ownerAdminPhone, com.example.database.AuditLineItemType.EXPENSE, "General", desc, amt, audit.date, audit.timestamp, audit.caName))
                }
                val parsedUdhar = parseListItems(text, "Credit (Udhar):", "Total Udhar:")
                parsedUdhar.forEach { (desc, amt) ->
                    legacyLineItems.add(com.example.database.AuditLineItem(0, "", audit.ownerAdminPhone, com.example.database.AuditLineItemType.CREDIT, desc, "Credit Sale", amt, audit.date, audit.timestamp, audit.caName))
                }
                val parsedRec = parseListItems(text, "Udhari Jama (Recoveries):", "Total Recoveries Added:")
                parsedRec.forEach { (desc, amt) ->
                    legacyLineItems.add(com.example.database.AuditLineItem(0, "", audit.ownerAdminPhone, com.example.database.AuditLineItemType.RECOVERY, desc, "Recovery", amt, audit.date, audit.timestamp, audit.caName))
                }
            }
        }

        val lineItemsToday = allAuditLineItems.filter { it.date == selectedAuditDate } + legacyLineItems
        
        // Sum only the shortage amounts (where tally result was negative)
        val totalShortageAmount = filteredAudits.sumOf { audit ->
            val expected = parseAmount(audit.summaryText, """EXPECTED NET CASH BAL:\s*₹?\s*([\d,.]+)""")
            val actual = audit.actualCashCollected
            val diff = actual - expected
            if (diff < 0) -diff else 0.0
        }

        val totalRecoveries = lineItemsToday.filter { it.type == com.example.database.AuditLineItemType.RECOVERY }.sumOf { it.amount }
        val totalExpenses = lineItemsToday.filter { it.type == com.example.database.AuditLineItemType.EXPENSE }.sumOf { it.amount }
        val totalUdharSum = lineItemsToday.filter { it.type == com.example.database.AuditLineItemType.CREDIT }.sumOf { it.amount }

        val recoveriesBreakdown = lineItemsToday.filter { it.type == com.example.database.AuditLineItemType.RECOVERY }.map { it.category to it.amount }
        val udharBreakdown = lineItemsToday.filter { it.type == com.example.database.AuditLineItemType.CREDIT }.map { it.category to it.amount }
        val expensesList = lineItemsToday.filter { it.type == com.example.database.AuditLineItemType.EXPENSE }.map { it.description to it.amount }

        // Product-wise sales quantities and revenues - DERIVED FROM CONSOLIDATED NOZZLES for perfect consistency
        var totalSalesRevenue = 0.0
        val productSalesDetails = consolidatedNozzles.groupBy { it.productName }.map { (product, nozzles) ->
            val qty = nozzles.sumOf { (it.totalNetVolume - it.totalTesting).coerceAtLeast(0.0) }
            val rateKey = productConfigMap.keys.find { isSameProduct(it, product) }
            val rate = productConfigMap[rateKey]?.get("rate")?.toDoubleOrNull() ?: 0.0
            val amt = qty * rate
            totalSalesRevenue += amt
            Triple(product, qty, rate)
        }

        // Individual CA Performance with MS/HSD volumes (Derived from actual shift readings)
        val readingsToday = allGeneralReadings.filter { it.date == selectedAuditDate }
        val msDay = allMsReadings.filter { it.date == selectedAuditDate }
        val hsdDay = allHsdReadings.filter { it.date == selectedAuditDate }
        
        val caShortages = filteredAudits.map { audit ->
            val expected = parseAmount(audit.summaryText, """EXPECTED NET CASH BAL:\s*₹?\s*([\d,.]+)""")
            val actual = audit.actualCashCollected
            
            // Link readings to this audit by reportId (preferred) or timestamp (legacy)
            val combinedReadings = (readingsToday + msDay.map { 
                GeneralNozzleReading(0, it.ownerAdminPhone, "MS", it.nozzleLabel, it.openingReading, it.closingReading, it.testing, it.caName, it.phone, it.udhar, it.kharch, it.udhariJama, it.msSales, it.timestamp, it.date, it.reportId)
            } + hsdDay.map {
                GeneralNozzleReading(0, it.ownerAdminPhone, "HSD", it.nozzleLabel, it.openingReading, it.closingReading, it.testing, it.caName, it.phone, it.udhar, it.kharch, it.udhariJama, it.hsdSales, it.timestamp, it.date, it.reportId)
            }).filter { 
                if (audit.reportId.isNotEmpty()) it.reportId == audit.reportId 
                else it.timestamp == audit.timestamp 
            }

            val msQty = combinedReadings.filter { isSameProduct(it.productName, "MS") }.sumOf { (it.closingReading - it.openingReading - it.testing).coerceAtLeast(0.0) }
            val hsdQty = combinedReadings.filter { isSameProduct(it.productName, "HSD") }.sumOf { (it.closingReading - it.openingReading - it.testing).coerceAtLeast(0.0) }
            
            // Per-shift Udhar and Kharch from AuditLineItems
            val shiftLineItems = lineItemsToday.filter { it.reportId == audit.reportId || (it.reportId.isEmpty() && it.timestamp == audit.timestamp) }
            val shiftUdhar = shiftLineItems.filter { it.type == com.example.database.AuditLineItemType.CREDIT }.sumOf { it.amount }
            val shiftKharch = shiftLineItems.filter { it.type == com.example.database.AuditLineItemType.EXPENSE }.sumOf { it.amount }

            val volumes = mapOf("MS" to msQty, "HSD" to hsdQty)
            CaShiftStats(audit.caName, volumes, actual - expected, audit.timestamp, shiftUdhar, shiftKharch)
        }
        
        DailyAuditSummary(
            sales = totalSalesRevenue,
            productSales = productSalesDetails,
            recoveries = totalRecoveries,
            phonePe = totalPhonePe,
            cards = totalCards,
            expenses = totalExpenses,
            udhar = totalUdharSum,
            totalShortage = totalShortageAmount,
            recoveriesList = recoveriesBreakdown,
            expensesList = expensesList,
            udharList = udharBreakdown,
            shortages = caShortages,
            cashSubmitted = totalCashSubmitted,
            actualCashCollected = totalActualCashCollected,
            lineItems = lineItemsToday
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
                        printDailySalesAuditPdf(
                            context = context,
                            date = selectedAuditDate,
                            audits = filteredAudits,
                            nozzleStatsList = consolidatedNozzles,
                            products = stationProducts,
                            productConfigMap = productConfigMap,
                            totals = aggregateData,
                            isShare = true,
                            pumpName = pumpName
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
                                val isMs = isSameProduct(product, "MS")
                                val receipt = if (isMs) ttEntries.sumOf { it.msPostDecantationStock - it.msPreDecantationStock }
                                              else ttEntries.sumOf { it.hsdPostDecantationStock - it.hsdPreDecantationStock }
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
                        val productLabel = if (prod == "MS") "MS PETROL" else if (prod == "HSD") "HSD DIESEL" else prod
                        val badgeColor = if (prod == "MS") MaterialTheme.colorScheme.primary else if (prod == "HSD") MaterialTheme.colorScheme.tertiary else Color.Gray

                        Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = productLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, color = badgeColor),
                                    modifier = Modifier.padding(8.dp)
                                )
                                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(8.dp)) {
                                    Text("Nozzle Label", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Op.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                    Text("Cl.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                    Text("Test", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                    Text("Sales", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                }
                                nozzles.forEach { n ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = n.label, 
                                            modifier = Modifier.weight(2f), 
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(formatDouble(n.firstOpening), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                                        Text(formatDouble(n.lastClosing), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                                        Text(formatDouble(n.totalTesting), modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, color = Color.Red)
                                        Text(formatDouble(n.totalNetVolume - n.totalTesting) + "L", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                    }
                                }
                                val prodTotal = nozzles.sumOf { it.totalNetVolume - it.totalTesting }
                                Row(modifier = Modifier.fillMaxWidth().padding(8.dp).background(badgeColor.copy(alpha = 0.1f))) {
                                    Text("Total $prod Sales", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                    Spacer(Modifier.weight(2.7f))
                                    Text(formatDouble(prodTotal) + " L", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black), textAlign = TextAlign.End)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Financial Layout: Revenues (Left) | Deductions (Right)
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Left Column: Revenues
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("REVENUES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black))
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Detailed Product Sales
                                aggregateData.productSales.forEach { (prod, qty, rate) ->
                                    Column {
                                        Text(prod, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF166534))
                                        Text("${formatDouble(qty)} L @ ₹${formatDouble(rate)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text("₹${formatDouble(qty * rate)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                    }
                                }

                                if (aggregateData.recoveriesList.isNotEmpty()) {
                                    HorizontalDivider(color = Color.Green.copy(alpha = 0.2f))
                                    Text("Recoveries (Jama)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Gray)
                                    aggregateData.recoveriesList.forEach { (desc, amt) ->
                                        RevenueRow(desc, amt, isSubItem = true)
                                    }
                                }
                                HorizontalDivider(color = Color.Green.copy(alpha = 0.3f))
                                val grandRev = aggregateData.sales + aggregateData.recoveries
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TOTAL REV", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black))
                                    Text("₹${formatDouble(grandRev)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black))
                                }
                            }
                        }
                    }
                    // Right Column: Deductions & Remittance
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DEDUCTIONS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black))
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (aggregateData.phonePe > 0) DeductionRow("PhonePe", aggregateData.phonePe)
                                if (aggregateData.cards > 0) DeductionRow("Cards", aggregateData.cards)
                                
                                HorizontalDivider(color = Color.Red.copy(alpha = 0.1f))

                                // 1. Expenses Section
                                if (aggregateData.expenses > 0) {
                                    Text("EXPENSES (KHARCH)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Gray)
                                    aggregateData.lineItems.filter { it.type == com.example.database.AuditLineItemType.EXPENSE }.forEach { item ->
                                        DeductionRow(
                                            label = item.description.ifEmpty { "Expense Entry" }, 
                                            amt = item.amount, 
                                            isSubItem = true
                                        )
                                        // Sub-label for CA name
                                        Text(
                                            text = "By: ${item.caName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                                        )
                                    }
                                    DeductionRow("Total Expenses", aggregateData.expenses, isBold = true)
                                    HorizontalDivider(color = Color.Red.copy(alpha = 0.1f))
                                }

                                // 2. Credit Sales (Udhar)
                                val credits = aggregateData.lineItems.filter { it.type == com.example.database.AuditLineItemType.CREDIT }
                                if (credits.isNotEmpty()) {
                                    Text("CREDIT (UDHAR) LIST", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Gray)
                                    credits.forEach { item ->
                                        DeductionRow(
                                            label = "${item.category}: ${item.description}",
                                            amt = item.amount,
                                            isSubItem = true
                                        )
                                        // Sub-label for CA name
                                        Text(
                                            text = "Added by: ${item.caName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                                        )
                                    }
                                    DeductionRow("Total Credit Sales", aggregateData.udhar, isBold = true)
                                    HorizontalDivider(color = Color.Red.copy(alpha = 0.1f))
                                }
                                
                                // 3. Shortages Section
                                if (aggregateData.totalShortage > 0) {
                                    DeductionRow("Total Staff Shortage", aggregateData.totalShortage, isBold = true)
                                    HorizontalDivider(color = Color.Red.copy(alpha = 0.1f))
                                }
                                
                                Text("REMITTANCE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Gray)
                                DeductionRow("Total Cash (Remitted + Hand)", aggregateData.cashSubmitted + aggregateData.actualCashCollected)

                                HorizontalDivider(color = Color.Red.copy(alpha = 0.2f))
                                
                                val totalRevenue = aggregateData.sales + aggregateData.recoveries
                                val totalOut = aggregateData.phonePe + aggregateData.cards + aggregateData.expenses + aggregateData.udhar + aggregateData.totalShortage + aggregateData.cashSubmitted + aggregateData.actualCashCollected
                                val finalTallyDiff = totalOut - totalRevenue
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TOTAL ACCOUNTED", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black))
                                    Text("₹${formatDouble(totalOut)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black), color = Color.Red)
                                }
                            }
                        }
                    }
                }
            }

            // 4.5. Tally Section
            item {
                val totalRevenue = aggregateData.sales + aggregateData.recoveries
                val totalOut = aggregateData.phonePe + aggregateData.cards + aggregateData.expenses + aggregateData.udhar + aggregateData.totalShortage + aggregateData.cashSubmitted + aggregateData.actualCashCollected
                val tallyDiff = totalOut - totalRevenue

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (kotlin.math.abs(tallyDiff) < 1.0) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)
                    ),
                    border = BorderStroke(1.dp, if (kotlin.math.abs(tallyDiff) < 1.0) Color(0xFF22C55E) else Color(0xFFF59E0B))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("FINAL RECONCILIATION TALLY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Revenue (Sales + Recoveries)", style = MaterialTheme.typography.bodyMedium)
                            Text("₹${formatDouble(totalRevenue)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Accounted (Deductions + Cash)", style = MaterialTheme.typography.bodyMedium)
                            Text("₹${formatDouble(totalOut)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Overall Tally Balance", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            val color = if (kotlin.math.abs(tallyDiff) < 1.0) Color(0xFF166534) else if (tallyDiff < 0) Color(0xFF991B1B) else Color(0xFF9A3412)
                            val label = if (kotlin.math.abs(tallyDiff) < 1.0) "PERFECT" else if (tallyDiff < 0) "STATION SHORTAGE" else "STATION SURPLUS"
                            Text("$label: ₹${formatDouble(tallyDiff)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = color)
                        }
                    }
                }
            }

            // 5. CA Performance
            item {
                Text("INDIVIDUAL CA PERFORMANCE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black))
                Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        aggregateData.shortages.forEach { stats ->
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stats.caName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    val color = if (Math.abs(stats.shortage) < 1.0) Color(0xFF2E7D32) else if (stats.shortage < 0) Color(0xFFC62828) else Color(0xFFF59E0B)
                                    val label = if (Math.abs(stats.shortage) < 1.0) "Perfect" else if (stats.shortage < 0) "Short: ₹${formatDouble(-stats.shortage)}" else "Extra: ₹${formatDouble(stats.shortage)}"
                                    Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black), color = color)
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), shape = RoundedCornerShape(4.dp)) {
                                        Text(" MS: ${formatDouble(stats.productVolumes["MS"] ?: 0.0)} L ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f), shape = RoundedCornerShape(4.dp)) {
                                        Text(" HSD: ${formatDouble(stats.productVolumes["HSD"] ?: 0.0)} L ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                    if (stats.udhar > 0) {
                                        Surface(color = Color.Red.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                            Text(" Udhar: ₹${formatDouble(stats.udhar)} ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                        }
                                    }
                                    // Removed Kharch (Expenses) display from CA Performance UI
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { 
                        printDailySalesAuditPdf(
                            context = context,
                            date = selectedAuditDate,
                            audits = filteredAudits,
                            nozzleStatsList = consolidatedNozzles,
                            products = stationProducts,
                            productConfigMap = productConfigMap.toMap(),
                            totals = aggregateData,
                            isShare = false,
                            pumpName = pumpName
                        )
                    }, 
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text("GENERATE FULL REPORT")
                }
            }
            
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
fun RevenueRow(label: String, amt: Double, isSubItem: Boolean = false, isBold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (isSubItem) "• $label" else label, style = if (isBold) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall, maxLines = 1)
        Text("₹${formatDouble(amt)}", style = if (isBold) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black) else MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DeductionRow(label: String, amt: Double, isSubItem: Boolean = false, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isSubItem) "• $label" else label,
            style = if (isBold) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Text(
            text = "-₹${formatDouble(amt)}",
            style = if (isBold) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black) else MaterialTheme.typography.bodySmall,
            color = Color(0xFFC62828),
            textAlign = TextAlign.End
        )
    }
}

private fun parseAmount(text: String, pattern: String): Double {
    return Regex(pattern, RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
}

private fun parseListItems(text: String, startTag: String, endTag: String): List<Pair<String, Double>> {
    val list = mutableListOf<Pair<String, Double>>()
    val lines = text.lines()
    var capture = false
    for (line in lines) {
        if (line.contains(startTag, ignoreCase = true)) { capture = true; continue }
        if (capture && (line.contains(endTag, ignoreCase = true) || line.startsWith("---"))) { capture = false; break }
        if (capture && (line.trim().startsWith("+") || line.trim().startsWith("-"))) {
            try {
                val content = line.trim().substring(1).trim()
                val desc = content.substringBefore(":").trim()
                val amt = content.substringAfter("₹").replace(",", "").toDoubleOrNull() ?: 0.0
                if (amt > 0) list.add(desc to amt)
            } catch (e: Exception) {}
        }
    }
    return list
}

private fun printDailySalesAuditPdf(
    context: Context,
    date: String,
    audits: List<SavedAudit>,
    nozzleStatsList: List<NozzleStats>,
    products: List<String>,
    productConfigMap: Map<String, Map<String, String>>,
    totals: DailyAuditSummary,
    isShare: Boolean,
    pumpName: String
) {
    val webView = WebView(context)
    val htmlBuilder = StringBuilder()

    // 1. Header (Stock & Inventory)
    val productRowsHtml = products.map { product ->
        val config = productConfigMap[product] ?: emptyMap()
        val opStock = config["stock"] ?: "0.0"
        val rate = config["rate"] ?: "0.0"
        """
        <tr>
            <td><strong>$product</strong></td>
            <td class="num">$opStock L</td>
            <td class="num">₹$rate</td>
        </tr>
        """.trimIndent()
    }.joinToString("")

    // 2. Nozzle Tables (Grouped)
    val groupedNozzles = nozzleStatsList.groupBy { it.productName }
    val nozzleTablesHtml = groupedNozzles.map { (prod, nozzles) ->
        val prodTotal = nozzles.sumOf { it.totalNetVolume - it.totalTesting }
        """
        <h4 style="color: #1a237e; border-bottom: 1px solid #eee; padding-bottom: 4px;">$prod NOZZLE REPORT</h4>
        <table>
            <thead>
                <tr>
                    <th class="col-nozzle">Nozzle</th>
                    <th class="col-num">Opening</th>
                    <th class="col-num">Closing</th>
                    <th class="col-num">Testing</th>
                    <th class="col-num">Net Sales</th>
                </tr>
            </thead>
            <tbody>
                ${nozzles.joinToString("") { n ->
                    "<tr><td class='col-nozzle'>${n.label}</td><td class='col-num'>${formatDouble(n.firstOpening)}</td><td class='col-num'>${formatDouble(n.lastClosing)}</td><td class='col-num' style='color:red;'>-${formatDouble(n.totalTesting)}</td><td class='col-num'><b>${formatDouble(n.totalNetVolume - n.totalTesting)} L</b></td></tr>"
                }}
                <tr class="total-row">
                    <td colspan="4">Total $prod Sales Volume</td>
                    <td class="col-num">${formatDouble(prodTotal)} L</td>
                </tr>
            </tbody>
        </table>
        """.trimIndent()
    }.joinToString("")

    // 3. Revenues & Deductions Ledger
    val revenuesHtml = totals.productSales.joinToString("") { (prod, qty, rate) ->
        "<tr><td>$prod Sales ($qty L @ ₹$rate)</td><td class='col-num'>₹${formatDouble(qty * rate)}</td></tr>"
    } + totals.recoveriesList.joinToString("") { (desc, amt) ->
        "<tr><td>+ Recovery: $desc</td><td class='col-num'>₹${formatDouble(amt)}</td></tr>"
    }

    val totalRevenue = totals.sales + totals.recoveries

    val expensesHtml = if (totals.expenses > 0) {
        "<tr><td colspan='2' style='background:#f8f9fa; font-weight:bold; font-size:9px;'>EXPENSES (KHARCH)</td></tr>" +
        totals.lineItems.filter { it.type == com.example.database.AuditLineItemType.EXPENSE }.joinToString("") { item ->
            "<tr><td><small>&bull; ${item.description} (By: ${item.caName})</small></td><td class='col-num'><small>-₹${formatDouble(item.amount)}</small></td></tr>"
        } + "<tr><td><strong>Total Expenses</strong></td><td class='col-num'><strong>-₹${formatDouble(totals.expenses)}</strong></td></tr>"
    } else ""

    val creditsHtml = if (totals.udhar > 0) {
        "<tr><td colspan='2' style='background:#f8f9fa; font-weight:bold; font-size:9px;'>CREDIT (UDHAR) SALES</td></tr>" +
        totals.lineItems.filter { it.type == com.example.database.AuditLineItemType.CREDIT }.joinToString("") { item ->
            "<tr><td><small>&bull; ${item.category} (${item.description}) (Added by: ${item.caName})</small></td><td class='col-num'><small>-₹${formatDouble(item.amount)}</small></td></tr>"
        } + "<tr><td><strong>Total Credit</strong></td><td class='col-num'><strong>-₹${formatDouble(totals.udhar)}</strong></td></tr>"
    } else ""

    val deductionsHtml = listOf(
        if (totals.phonePe > 0) "<tr><td>- PhonePe Deduction</td><td class='col-num'>-₹${formatDouble(totals.phonePe)}</td></tr>" else "",
        if (totals.cards > 0) "<tr><td>- Card Deduction</td><td class='col-num'>-₹${formatDouble(totals.cards)}</td></tr>" else "",
        if (totals.totalShortage > 0) "<tr><td>- Staff Shortages (Loss)</td><td class='col-num'>-₹${formatDouble(totals.totalShortage)}</td></tr>" else ""
    ).joinToString("") + expensesHtml + creditsHtml + """
        <tr class="total-row"><td>Total Cash & Remittance</td><td class="col-num">-₹${formatDouble(totals.cashSubmitted + totals.actualCashCollected)}</td></tr>
    """.trimIndent()

    val totalAccounted = totals.phonePe + totals.cards + totals.expenses + totals.udhar + totals.totalShortage + totals.cashSubmitted + totals.actualCashCollected
    val tallyDiff = totalAccounted - totalRevenue
    val tallyLabel = if (Math.abs(tallyDiff) < 1.0) "STATION PERFECT" else if (tallyDiff < 0) "STATION SHORTAGE" else "STATION SURPLUS"
    val tallyColor = if (Math.abs(tallyDiff) < 1.0) "#2e7d32" else "#c62828"

    // 4. CA Performance
    val caPerformanceHtml = totals.shortages.joinToString("") { stats ->
        val status = if (Math.abs(stats.shortage) < 1.0) "PERFECT" else if (stats.shortage < 0) "SHORTAGE: ₹${formatDouble(-stats.shortage)}" else "SURPLUS: ₹${formatDouble(stats.shortage)}"
        val statusColor = if (Math.abs(stats.shortage) < 1.0) "#2e7d32" else if (stats.shortage < 0) "#c62828" else "#f57c00"
        
        val shiftFinance = mutableListOf<String>()
        if (stats.udhar > 0) shiftFinance.add("Udhar: ₹${formatDouble(stats.udhar)}")
        // Removed Kharch (Expenses) from CA Performance Section
        val financeStr = if (shiftFinance.isNotEmpty()) " | ${shiftFinance.joinToString(" | ")}" else ""

        """
        <tr>
            <td><strong>${stats.caName}</strong><br/><small>MS: ${formatDouble(stats.productVolumes["MS"] ?: 0.0)}L | HSD: ${formatDouble(stats.productVolumes["HSD"] ?: 0.0)}L$financeStr</small></td>
            <td class="col-num" style="color: $statusColor; font-weight: bold;">$status</td>
        </tr>
        """.trimIndent()
    }

    htmlBuilder.append("""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { font-family: Arial, sans-serif; font-size: 11px; line-height: 1.4; color: #333; margin: 0; padding: 0; }
                .header { text-align: center; border-bottom: 2px solid #1a237e; padding-bottom: 10px; margin-bottom: 20px; }
                .header h1 { margin: 0; color: #1a237e; font-size: 18px; }
                table { width: 100%; border-collapse: collapse; margin-bottom: 15px; table-layout: fixed; }
                th, td { border: 1px solid #eee; padding: 6px; text-align: left; word-wrap: break-word; }
                th { background-color: #f8f9fa; font-weight: bold; color: #1a237e; }
                .col-nozzle { width: 30%; }
                .col-num { width: 17.5%; text-align: right; font-family: 'Courier New', monospace; }
                .total-row { background-color: #f1f3f4; font-weight: bold; }
                .section-title { background-color: #1a237e; color: white; padding: 5px 10px; font-weight: bold; margin-bottom: 10px; }
                .grid { display: flex; gap: 20px; }
                .col { flex: 1; }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>$pumpName</h1>
                <h3>Daily Sales Audit Report - $date</h3>
            </div>

            <div class="grid">
                <div class="col">
                    <div class="section-title">PRODUCT STOCK & RATES</div>
                    <table>
                        <thead><tr><th>Product</th><th class="num">Opening Stock</th><th class="num">Rate</th></tr></thead>
                        <tbody>$productRowsHtml</tbody>
                    </table>
                </div>
            </div>

            <div class="section-title">METER SALES DETAILS</div>
            $nozzleTablesHtml

            <div class="grid">
                <div class="col">
                    <div class="section-title">TOTAL REVENUES</div>
                    <table>
                        $revenuesHtml
                        <tr class="total-row"><td>GRAND TOTAL REVENUE</td><td class="num">₹${formatDouble(totalRevenue)}</td></tr>
                    </table>
                </div>
                <div class="col">
                    <div class="section-title">TOTAL DEDUCTIONS & REMITTANCE</div>
                    <table>
                        $deductionsHtml
                        <tr class="total-row" style="background-color: ${if (kotlin.math.abs(tallyDiff) < 1.0) "#e8f5e9" else "#fff3e0"};">
                            <td>FINAL TALLY DIFFERENCE ($tallyLabel)</td>
                            <td class="col-num" style="color: $tallyColor;">₹${formatDouble(tallyDiff)}</td>
                        </tr>
                    </table>
                </div>
            </div>

            <div class="section-title">INDIVIDUAL SHIFT PERFORMANCE</div>
            <table>
                <thead><tr><th>CA Name & Volume</th><th class="num">Tally Result</th></tr></thead>
                <tbody>$caPerformanceHtml</tbody>
            </table>

            <div style="margin-top: 30px; text-align: center; color: #999;">
                <p>Report generated by My Application - Fuel Station Manager</p>
            </div>
        </body>
        </html>
    """.trimIndent())

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            if (isShare) {
                try {
                    val cacheDir = context.cacheDir
                    val file = java.io.File(cacheDir, "Daily_Audit_$date.pdf")
                    val printAdapter = webView.createPrintDocumentAdapter("Daily_Audit_$date")
                    
                    android.print.PrintHelper.savePdf(printAdapter, file, object : android.print.PrintHelper.PrintCallback {
                        override fun onSuccess() {
                            val uri = FileProvider.getUriForFile(context, "com.mypump.cal.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Daily Audit"))
                        }
                        override fun onFailure(error: String?) {
                            Toast.makeText(context, "Failed to generate PDF for sharing", Toast.LENGTH_SHORT).show()
                        }
                    })
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } else {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val jobName = "${pumpName}_Audit_$date"
                printManager?.print(jobName, webView.createPrintDocumentAdapter(jobName), null)
            }
        }
    }
    webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "utf-8", null)
}
