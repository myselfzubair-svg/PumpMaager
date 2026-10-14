package com.example

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.database.SavedAudit
import kotlinx.coroutines.flow.take
import java.text.SimpleDateFormat
import java.util.*

data class CaPerformanceRecord(
    val date: String,
    val caName: String,
    val totalSalesRs: Double,
    val totalSalesLitres: Double,
    val productLitresDetail: String,
    val totalPayments: Double,
    val tallyDifference: Double,
    val auditType: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaPerformanceReportScreen(
    adminPhone: String,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    val defaultToDate = sdf.format(Date())
    val calFrom = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    val defaultFromDate = sdf.format(calFrom.time)

    var fromDateStr by rememberSaveable { mutableStateOf(defaultFromDate) }
    var toDateStr by rememberSaveable { mutableStateOf(defaultToDate) }
    var selectedCaFilter by rememberSaveable { mutableStateOf("All CAs") }
    var isCaDropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    
    var reportTrigger by remember { mutableStateOf(0) }
    var staffList by remember { mutableStateOf(listOf("All CAs")) }

    val resetAndBack = {
        onBack()
    }

    // Fetch staff members
    LaunchedEffect(adminPhone) {
        if (adminPhone.isNotBlank()) {
            com.example.database.SupabaseRepository.getStaffMembersFlow(adminPhone).take(1).collect { members ->
                staffList = listOf("All CAs") + members.map { it.name }.distinct().sorted()
            }
        }
    }

    val allAuditsState = com.example.database.SupabaseRepository.getAuditsFlow(adminPhone).collectAsState(initial = emptyList())
    val allExpensesState = com.example.database.SupabaseRepository.getExpenseEntriesFlow(adminPhone).collectAsState(initial = emptyList())
    val allNozzleReadingsState = com.example.database.SupabaseRepository.getNozzleReadingsFlow(adminPhone).collectAsState(initial = emptyList())

    LaunchedEffect(allAuditsState.value, allExpensesState.value) {
        val names = (staffList + allAuditsState.value.map { it.caName } + allExpensesState.value.mapNotNull { exp ->
            if (exp.description.startsWith("CA:", ignoreCase = true)) {
                exp.description.substringAfter("CA:").substringBefore("-").trim()
            } else null
        }).filter { it.isNotBlank() && it != "Manager" && it != "All CAs" }.distinct().sorted()
        staffList = listOf("All CAs") + names
    }

    val performanceRecords = remember(allAuditsState.value, allExpensesState.value, allNozzleReadingsState.value, fromDateStr, toDateStr, selectedCaFilter, reportTrigger) {
        val list = mutableListOf<CaPerformanceRecord>()
        val sdfParse = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        
        val fromDateObj = try { sdfParse.parse(fromDateStr) } catch (e: Exception) { null }
        val toDateObj = try { sdfParse.parse(toDateStr) } catch (e: Exception) { null }

        val shiftAudits = allAuditsState.value.filter { audit ->
            if (audit.auditType == "Manager's Report") return@filter false
            if (selectedCaFilter != "All CAs" && !audit.caName.equals(selectedCaFilter, ignoreCase = true)) return@filter false
            
            if (fromDateObj != null && toDateObj != null) {
                val auditDateObj = try { sdfParse.parse(audit.date) } catch (e: Exception) { null }
                if (auditDateObj != null) {
                    !auditDateObj.before(fromDateObj) && !auditDateObj.after(toDateObj)
                } else true
            } else true
        }

        // Map payments by date + caName, ONLY including entries specifically in the name of the CA ("CA: Name")
        val expenseMap = mutableMapOf<String, Double>()
        allExpensesState.value.forEach { exp ->
            if (exp.description.startsWith("CA:", ignoreCase = true)) {
                val expCa = exp.description.substringAfter("CA:").substringBefore("-").trim()
                if (expCa.isNotBlank() && (selectedCaFilter == "All CAs" || expCa.equals(selectedCaFilter, ignoreCase = true))) {
                    val expDateObj = try { sdfParse.parse(exp.date) } catch (e: Exception) { null }
                    val inRange = if (fromDateObj != null && toDateObj != null && expDateObj != null) {
                        !expDateObj.before(fromDateObj) && !expDateObj.after(toDateObj)
                    } else true

                    if (inRange) {
                        val key = "${exp.date}_${expCa.lowercase().trim()}"
                        expenseMap[key] = (expenseMap[key] ?: 0.0) + exp.amount
                    }
                }
            }
        }

        val allDatesAndCas = (shiftAudits.map { it.date to it.caName } + allExpensesState.value.mapNotNull { exp ->
            if (exp.description.startsWith("CA:", ignoreCase = true)) {
                val c = exp.description.substringAfter("CA:").substringBefore("-").trim()
                if (c.isNotBlank()) exp.date to c else null
            } else null
        }).distinct()

        allDatesAndCas.forEach { (date, ca) ->
            if (ca.isBlank() || ca == "Manager") return@forEach
            if (selectedCaFilter != "All CAs" && !ca.equals(selectedCaFilter, ignoreCase = true)) return@forEach

            val dateObj = try { sdfParse.parse(date) } catch (e: Exception) { null }
            val inRange = if (fromDateObj != null && toDateObj != null && dateObj != null) {
                !dateObj.before(fromDateObj) && !dateObj.after(toDateObj)
            } else true

            if (inRange) {
                val auditsForCaDate = shiftAudits.filter { it.date == date && it.caName.equals(ca, true) }
                val salesRs = auditsForCaDate.sumOf { it.totalFuelSalesAmount }
                val diff = auditsForCaDate.sumOf { it.tallyDifference }
                val auditType = auditsForCaDate.joinToString(", ") { it.auditType }.let { if (it.isEmpty()) "Payment Record" else it }

                val expKey = "${date}_${ca.lowercase().trim()}"
                val payments = expenseMap[expKey] ?: getCaPaymentsFromSummary(ca, auditsForCaDate)

                // Calculate Litres for this CA and date
                val caReadings = allNozzleReadingsState.value.filter { it.date == date && it.caName.equals(ca, true) }
                val (totalL, detailL) = if (caReadings.isNotEmpty()) {
                    val pMap = mutableMapOf<String, Double>()
                    caReadings.forEach { r ->
                        val vol = (r.closing - r.opening - r.testing).coerceAtLeast(0.0)
                        pMap[r.productName] = (pMap[r.productName] ?: 0.0) + vol
                    }
                    val tot = pMap.values.sum()
                    val det = pMap.entries.filter { it.value > 0 }.joinToString(", ") { "${it.key}: ${String.format(Locale.getDefault(), "%,.1f", it.value)} L" }
                    Pair(tot, if (det.isEmpty()) "0 L" else det)
                } else {
                    extractLitresFromAudits(auditsForCaDate.map { it.summaryText })
                }

                if (salesRs > 0 || totalL > 0 || payments > 0 || diff != 0.0) {
                    if (list.none { it.date == date && it.caName.equals(ca, true) }) {
                        list.add(
                            CaPerformanceRecord(
                                date = date,
                                caName = ca,
                                totalSalesRs = salesRs,
                                totalSalesLitres = totalL,
                                productLitresDetail = detailL,
                                totalPayments = payments,
                                tallyDifference = diff,
                                auditType = auditType
                            )
                        )
                    }
                }
            }
        }

        list.sortedWith { o1, o2 ->
            try {
                val d1 = sdfParse.parse(o1.date)
                val d2 = sdfParse.parse(o2.date)
                val comp = d1?.compareTo(d2) ?: 0
                if (comp != 0) comp else o1.caName.compareTo(o2.caName)
            } catch (e: Exception) {
                o1.date.compareTo(o2.date)
            }
        }
    }

    val filteredRecords = remember(performanceRecords, searchQuery) {
        if (searchQuery.isBlank()) performanceRecords
        else performanceRecords.filter {
            it.caName.contains(searchQuery, ignoreCase = true) ||
            it.date.contains(searchQuery) ||
            it.auditType.contains(searchQuery, ignoreCase = true)
        }
    }

    val grandTotalLitres = remember(filteredRecords) { filteredRecords.sumOf { it.totalSalesLitres } }
    val grandTotalPayments = remember(filteredRecords) { filteredRecords.sumOf { it.totalPayments } }
    val grandTotalDifference = remember(filteredRecords) { filteredRecords.sumOf { it.tallyDifference } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = LanguageManager.translate("CA Performance Report", "कैशियर (CA) प्रदर्शन रिपोर्ट"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Custom Date Range & CA Performance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = resetAndBack, modifier = Modifier.testTag("ca_perf_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            printCaPerformancePdf(
                                context = context,
                                fromDate = fromDateStr,
                                toDate = toDateStr,
                                caFilter = selectedCaFilter,
                                records = filteredRecords,
                                totalLitres = grandTotalLitres,
                                totalPayments = grandTotalPayments,
                                totalDifference = grandTotalDifference,
                                isShare = false
                            )
                        }
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print Report")
                    }
                    IconButton(
                        onClick = {
                            printCaPerformancePdf(
                                context = context,
                                fromDate = fromDateStr,
                                toDate = toDateStr,
                                caFilter = selectedCaFilter,
                                records = filteredRecords,
                                totalLitres = grandTotalLitres,
                                totalPayments = grandTotalPayments,
                                totalDifference = grandTotalDifference,
                                isShare = true
                            )
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CUSTOM DATE RANGE & CA FILTER CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Report Filters", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // From Date Picker
                            Box(modifier = Modifier.weight(1f).clickable {
                                val parts = fromDateStr.split("-")
                                if (parts.size == 3) {
                                    calendar.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
                                }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d -> fromDateStr = String.format("%02d-%02d-%04d", d, m + 1, y) },
                                    calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }) {
                                OutlinedTextField(
                                    value = fromDateStr,
                                    onValueChange = {},
                                    label = { Text("From Date") },
                                    readOnly = true,
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    trailingIcon = { Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp)) }
                                )
                            }

                            // To Date Picker
                            Box(modifier = Modifier.weight(1f).clickable {
                                val parts = toDateStr.split("-")
                                if (parts.size == 3) {
                                    calendar.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
                                }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d -> toDateStr = String.format("%02d-%02d-%04d", d, m + 1, y) },
                                    calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }) {
                                OutlinedTextField(
                                    value = toDateStr,
                                    onValueChange = {},
                                    label = { Text("To Date") },
                                    readOnly = true,
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    trailingIcon = { Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }

                        // CA Name Dropdown Filter
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedCaFilter,
                                onValueChange = {},
                                label = { Text("Filter by CA Name") },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().clickable { isCaDropdownExpanded = true },
                                trailingIcon = {
                                    IconButton(onClick = { isCaDropdownExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, null)
                                    }
                                }
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { isCaDropdownExpanded = true })
                            DropdownMenu(expanded = isCaDropdownExpanded, onDismissRequest = { isCaDropdownExpanded = false }) {
                                staffList.forEach { ca ->
                                    DropdownMenuItem(
                                        text = { Text(ca, fontWeight = if (ca == selectedCaFilter) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = { selectedCaFilter = ca; isCaDropdownExpanded = false }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val d1 = try { sdf.parse(fromDateStr) } catch (e: Exception) { null }
                                val d2 = try { sdf.parse(toDateStr) } catch (e: Exception) { null }
                                if (d1 != null && d2 != null && d1.after(d2)) {
                                    android.widget.Toast.makeText(context, "From Date cannot be after To Date", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    reportTrigger++
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Generate Report", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // SUMMARY CARDS
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "Total Sales (Litres)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = String.format(Locale.getDefault(), "%,.1f", grandTotalLitres) + " L", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "Total Payments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "₹" + String.format(Locale.getDefault(), "%,.1f", grandTotalPayments), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "Net Difference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "₹" + String.format(Locale.getDefault(), "%,.1f", grandTotalDifference),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (grandTotalDifference < 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }

            // SEARCH BAR
            item {
                VoiceOutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search records...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = null) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // PERFORMANCE RECORDS LIST
            item {
                Text(
                    text = "CA PERFORMANCE RECORDS (${filteredRecords.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            if (filteredRecords.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📭", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No CA performance records found for this date range", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(filteredRecords) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = record.caName,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = record.date,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text("Sales (Litres)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(record.productLitresDetail, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.9f)) {
                                    Text("Payments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("-₹" + String.format(Locale.getDefault(), "%,.2f", record.totalPayments), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                                }
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.9f)) {
                                    Text("Difference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "₹" + String.format(Locale.getDefault(), "%,.2f", record.tallyDifference),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (record.tallyDifference < 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
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

private fun getCaPaymentsFromSummary(caName: String, audits: List<SavedAudit>): Double {
    var total = 0.0
    audits.forEach { audit ->
        val text = audit.summaryText
        var inExpensesSection = false
        text.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.contains("Expenses (Kharch):", ignoreCase = true)) {
                inExpensesSection = true
                return@forEach
            }
            if (inExpensesSection && (trimmed.startsWith("---") || trimmed.contains("Total Expenses:"))) {
                inExpensesSection = false
                return@forEach
            }
            if (inExpensesSection && trimmed.contains("CA: $caName", ignoreCase = true)) {
                val amtMatch = Regex("""[\d,.]+$""").find(trimmed) ?: Regex("""₹\s*([\d,.]+)""").find(trimmed)
                if (amtMatch != null) {
                    val amtStr = (amtMatch.groupValues.getOrNull(1) ?: amtMatch.value).replace(",", "")
                    amtStr.toDoubleOrNull()?.let { total += it }
                }
            }
        }
    }
    return total
}

private fun extractLitresFromAudits(summaryTexts: List<String>): Pair<Double, String> {
    val productMap = mutableMapOf<String, Double>()
    summaryTexts.forEach { summaryText ->
        var currentProduct = ""
        summaryText.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.endsWith(":") && !trimmed.contains(" ") && !trimmed.startsWith("---")) {
                val pName = trimmed.removeSuffix(":")
                if (pName in listOf("MS", "HSD", "SPEED", "DIESEL", "PETROL") || (pName.isNotEmpty() && pName.all { it.isUpperCase() })) {
                    currentProduct = pName
                }
            }
            if (currentProduct.isNotEmpty() && (trimmed.contains("Net Sales") || trimmed.contains("Gross Sale"))) {
                val volMatch = Regex("""([\d,.]+)\s*L""").find(trimmed)
                if (volMatch != null) {
                    val vol = volMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
                    if (vol > 0) {
                        productMap[currentProduct] = (productMap[currentProduct] ?: 0.0) + vol
                    }
                }
            }
        }
    }
    val totalL = productMap.values.sum()
    val detail = if (productMap.isNotEmpty()) {
        productMap.entries.filter { it.value > 0 }.joinToString(", ") { "${it.key}: ${String.format(Locale.getDefault(), "%,.1f", it.value)} L" }
    } else "0 L"
    return Pair(totalL, detail)
}

private fun printCaPerformancePdf(
    context: Context,
    fromDate: String,
    toDate: String,
    caFilter: String,
    records: List<CaPerformanceRecord>,
    totalLitres: Double,
    totalPayments: Double,
    totalDifference: Double,
    isShare: Boolean = false
) {
    val webView = android.webkit.WebView(context)
    val htmlBuilder = StringBuilder()

    val rowsHtml = records.joinToString("") { r ->
        """
        <tr>
            <td style="padding: 8px; border-bottom: 1px solid #eee;">${r.date}</td>
            <td style="padding: 8px; border-bottom: 1px solid #eee; font-weight: bold;">${r.caName}</td>
            <td style="padding: 8px; border-bottom: 1px solid #eee; text-align: right; font-weight: bold;">${r.productLitresDetail}</td>
            <td style="padding: 8px; border-bottom: 1px solid #eee; text-align: right; color: #c62828;">-₹${String.format(Locale.getDefault(), "%,.2f", r.totalPayments)}</td>
            <td style="padding: 8px; border-bottom: 1px solid #eee; text-align: right; font-weight: bold; color: ${if(r.tallyDifference < 0) "#c62828" else "#2e7d32"};">₹${String.format(Locale.getDefault(), "%,.2f", r.tallyDifference)}</td>
        </tr>
        """.trimIndent()
    }

    htmlBuilder.append("""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                @page { size: portrait; margin: 10mm; }
                body { font-family: Arial, sans-serif; font-size: 11px; color: #333; line-height: 1.4; }
                .header { text-align: center; border-bottom: 3px double #333; padding-bottom: 10px; margin-bottom: 15px; }
                .header h1 { margin: 0; font-size: 18px; color: #1a237e; }
                .summary-box { background: #f8f9fa; border: 1px solid #ddd; padding: 12px; border-radius: 6px; margin-bottom: 15px; }
                table { width: 100%; border-collapse: collapse; margin-bottom: 15px; }
                th { background: #f5f5f5; padding: 8px; text-align: left; border-bottom: 2px solid #ddd; }
                td { padding: 6px 8px; }
                .num { text-align: right; font-family: monospace; }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>D R INAMDAR PETROLEUM</h1>
                <h2>CA Performance Report</h2>
                <div>Period: <strong>$fromDate to $toDate</strong> | CA: <strong>$caFilter</strong></div>
            </div>
            <div class="summary-box">
                <table style="border: none; margin: 0;">
                    <tr><td>Total Sales (Litres):</td><td class="num" style="font-weight:bold; color:#1a237e;">${String.format(Locale.getDefault(), "%,.1f", totalLitres)} L</td></tr>
                    <tr><td>Total Payments:</td><td class="num" style="font-weight:bold; color:#c62828;">-₹${String.format(Locale.getDefault(), "%,.2f", totalPayments)}</td></tr>
                    <tr style="border-top:1px solid #ddd;"><td><strong>Net Difference:</strong></td><td class="num" style="font-weight:bold; color:${if(totalDifference < 0) "#c62828" else "#2e7d32"};">₹${String.format(Locale.getDefault(), "%,.2f", totalDifference)}</td></tr>
                </table>
            </div>
            <table>
                <thead>
                    <tr><th>Date</th><th>CA Name</th><th class="num">Sales (Litres)</th><th class="num">Payments</th><th class="num">Difference</th></tr>
                </thead>
                <tbody>
                    $rowsHtml
                </tbody>
            </table>
        </body>
        </html>
    """.trimIndent())

    webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "UTF-8", null)
    webView.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
            val jobName = "CA_Performance_${fromDate}_to_${toDate}"
            if (isShare) {
                val cacheDir = context.cacheDir
                val pdfFile = java.io.File(cacheDir, "$jobName.pdf")
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                android.print.PrintHelper.savePdf(printAdapter, pdfFile, object : android.print.PrintHelper.PrintCallback {
                    override fun onSuccess() {
                        val uri = FileProvider.getUriForFile(context, "com.mypump.cal.fileprovider", pdfFile)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Report"))
                    }
                    override fun onFailure(error: String?) { android.widget.Toast.makeText(context, "PDF failed: $error", android.widget.Toast.LENGTH_SHORT).show() }
                })
            } else {
                val pm = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
                pm?.print(jobName, webView.createPrintDocumentAdapter(jobName), null)
            }
        }
    }
}
