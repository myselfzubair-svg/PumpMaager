package com.example

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.database.SavedAudit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

data class CreditRecord(
    val date: String,
    val description: String,
    val amount: Double,
    val caName: String,
    val auditId: Int
)

data class CreditCategorySummary(
    val name: String,
    val total: Double,
    val percentage: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyCreditReportScreen(
    adminPhone: String,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Selected Month & Year
    val calendar = Calendar.getInstance()
    var selectedMonth by rememberSaveable { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var selectedYear by rememberSaveable { mutableStateOf(calendar.get(Calendar.YEAR)) }

    // Search query
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val resetAndBack = {
        val now = Calendar.getInstance()
        selectedMonth = now.get(Calendar.MONTH)
        selectedYear = now.get(Calendar.YEAR)
        searchQuery = ""
        onBack()
    }

    BackHandler {
        resetAndBack()
    }

    // Load credit entries and audits from Cloud (combining table entries, manager reports, pump reports, and CA reports)
    val allCreditsState = com.example.database.SupabaseRepository.getCreditEntriesFlow(adminPhone).collectAsState(initial = emptyList())
    val allAuditsState = com.example.database.SupabaseRepository.getAuditsFlow(adminPhone).collectAsState(initial = emptyList())

    // Month List configuration
    val monthNamesEn = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val monthNamesHi = listOf(
        "जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून",
        "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"
    )

    val currentMonthName = LanguageManager.translate(
        monthNamesEn[selectedMonth],
        monthNamesHi[selectedMonth]
    )

    // Parse and combine all credits from table entries and audits (Manager reports, pump reports, CA reports)
    val parsedCredits = remember(allCreditsState.value, allAuditsState.value, selectedMonth, selectedYear) {
        val list = mutableListOf<CreditRecord>()
        val monthStr = String.format(Locale.getDefault(), "%02d", selectedMonth + 1)
        val yearStr = selectedYear.toString()
        val suffix = "-$monthStr-$yearStr"

        // 1. From udhari table entries
        allCreditsState.value.forEach { entry ->
            if (entry.date.endsWith(suffix)) {
                list.add(
                    CreditRecord(
                        date = entry.date,
                        description = "${entry.party}: ${entry.description}",
                        amount = entry.amount,
                        caName = entry.caName,
                        auditId = entry.id?.toInt() ?: 0
                    )
                )
            }
        }

        // 2. From saved audits (Manager reports, pump reports, CA reports)
        allAuditsState.value.forEach { audit ->
            if (audit.date.endsWith(suffix)) {
                val extracted = parseDetailedCredits(audit.summaryText)
                extracted.forEach { (party, amt) ->
                    if (list.none { it.date == audit.date && it.description.contains(party, ignoreCase = true) && Math.abs(it.amount - amt) < 0.01 }) {
                        list.add(
                            CreditRecord(
                                date = audit.date,
                                description = "$party: Audit Entry",
                                amount = amt,
                                caName = audit.caName,
                                auditId = audit.id ?: 0
                            )
                        )
                    }
                }
            }
        }
        // Sort chronologically by parsing date dd-MM-yyyy
        list.sortedWith { o1, o2 ->
            try {
                val sdf = java.text.SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val d1 = sdf.parse(o1.date)
                val d2 = sdf.parse(o2.date)
                d1?.compareTo(d2) ?: 0
            } catch (e: Exception) {
                o1.date.compareTo(o2.date)
            }
        }
    }

    // Filter parsed credits by search query
    val filteredCredits = remember(parsedCredits, searchQuery) {
        if (searchQuery.isBlank()) {
            parsedCredits
        } else {
            parsedCredits.filter {
                it.description.contains(searchQuery, ignoreCase = true) ||
                        it.caName.contains(searchQuery, ignoreCase = true) ||
                        it.date.contains(searchQuery)
            }
        }
    }

    // Key stats
    val totalCredits = remember(filteredCredits) {
        filteredCredits.sumOf { it.amount }
    }

    val highestCredit = remember(filteredCredits) {
        filteredCredits.maxByOrNull { it.amount }
    }

    val totalRecordsCount = filteredCredits.size

    // Category summary aggregation (Party breakdown)
    val categorySummaries = remember(filteredCredits, totalCredits) {
        val breakdown = filteredCredits.groupBy { it.description.lowercase().trim() }
            .map { (cat, list) ->
                val sum = list.sumOf { it.amount }
                val pct = if (totalCredits > 0.0) (sum / totalCredits).toFloat() else 0.0f
                // Capitalize the first letter of each word for clean display
                val displayName = cat.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
                CreditCategorySummary(displayName, sum, pct)
            }
        breakdown.sortedByDescending { it.total }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = LanguageManager.translate("Monthly Credit (Udhar) Report", "मासिक उधार रिपोर्ट"),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = resetAndBack,
                        modifier = Modifier.testTag("credit_report_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            printMonthlyCreditReportPdf(
                                context = context,
                                monthName = monthNamesEn[selectedMonth],
                                year = selectedYear,
                                credits = filteredCredits,
                                categorySummaries = categorySummaries,
                                totalCredits = totalCredits,
                                isShare = false
                            )
                        },
                        modifier = Modifier.testTag("print_credit_report")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print Report",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            printMonthlyCreditReportPdf(
                                context = context,
                                monthName = monthNamesEn[selectedMonth],
                                year = selectedYear,
                                credits = filteredCredits,
                                categorySummaries = categorySummaries,
                                totalCredits = totalCredits,
                                isShare = true
                            )
                        },
                        modifier = Modifier.testTag("share_credit_report")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share PDF",
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
            // MONTH & YEAR PICKER
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedMonth == 0) {
                                    selectedMonth = 11
                                    selectedYear -= 1
                                } else {
                                    selectedMonth -= 1
                                }
                            },
                            modifier = Modifier.testTag("credit_prev_month")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous Month"
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Calendar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$currentMonthName $selectedYear",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
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
                            },
                            modifier = Modifier.testTag("credit_next_month")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next Month"
                            )
                        }
                    }
                }
            }

            // SUMMARY CARDS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Total Credits Card
                    Card(
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = LanguageManager.translate("Total Credit (Udhar)", "कुल उधार"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "₹" + String.format(Locale.getDefault(), "%,.2f", totalCredits),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Count Card
                    Card(
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = LanguageManager.translate("Records", "प्रविष्टियां"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$totalRecordsCount",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // HIGHEST CREDIT INDICATOR
            if (highestCredit != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Highest Credit Info",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = LanguageManager.translate("Highest Debit Record", "अधिकतम उधार प्रविष्टि"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${highestCredit.description} on ${highestCredit.date} — ₹" + String.format(Locale.getDefault(), "%,.2f", highestCredit.amount),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // SEARCH BAR
            item {
                VoiceOutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("credit_search_input"),
                    placeholder = {
                        Text(
                            text = LanguageManager.translate(
                                "Search by account/party, cashier, date...",
                                "खाता/पार्टी, कैशियर, तिथि से खोजें..."
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            // PARTY/ACCOUNT-WISE BREAKDOWN SECTION
            if (categorySummaries.isNotEmpty()) {
                item {
                    Text(
                        text = LanguageManager.translate("ACCOUNT / PARTY-WISE BREAKDOWN", "खाता / पार्टी-वार विवरण"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            categorySummaries.forEach { summary ->
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = summary.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "₹" + String.format(Locale.getDefault(), "%,.2f", summary.total),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = "(${String.format(Locale.getDefault(), "%.1f", summary.percentage * 100)}%)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { summary.percentage },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp),
                                        color = MaterialTheme.colorScheme.error,
                                        trackColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // CHRONOLOGICAL ENTRIES LIST
            item {
                Text(
                    text = LanguageManager.translate("CHRONOLOGICAL DEBIT LOG", "कालानुक्रमिक उधार लॉग"),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                )
            }

            if (filteredCredits.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "📭",
                                fontSize = 36.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = LanguageManager.translate("No Credit (Udhar) Records Found", "कोई उधार रिकॉर्ड नहीं मिला"),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = LanguageManager.translate("Try changing the selected month or search keyword.", "कृपया चयनित महीना या खोज शब्द बदलने का प्रयास करें।"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                items(filteredCredits) { credit ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { /* Detail modal or popup can be shown */ },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = credit.date,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Text(
                                        text = "By: ${credit.caName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = credit.description,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "₹" + String.format(Locale.getDefault(), "%,.2f", credit.amount),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseDetailedCredits(summaryText: String): List<Pair<String, Double>> {
    val list = mutableListOf<Pair<String, Double>>()
    val lines = summaryText.lines()
    var insideCredits = false
    for (line in lines) {
        if (line.contains("Credit (Udhar):", ignoreCase = true)) {
            insideCredits = true
            continue
        }
        if (insideCredits) {
            val trimmed = line.trim()
            if (trimmed.contains("Total Udhar:", ignoreCase = true) ||
                trimmed.startsWith("---") ||
                trimmed.contains("EXPECTED NET CASH BAL", ignoreCase = true) ||
                trimmed.contains("TALLY RESULT:", ignoreCase = true)
            ) {
                insideCredits = false
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

private fun printMonthlyCreditReportPdf(
    context: Context,
    monthName: String,
    year: Int,
    credits: List<CreditRecord>,
    categorySummaries: List<CreditCategorySummary>,
    totalCredits: Double,
    isShare: Boolean = false
) {
    val webView = android.webkit.WebView(context)
    val htmlBuilder = java.lang.StringBuilder()

    val breakdownRowsHtml = categorySummaries.joinToString("") { summary ->
        """
        <tr>
            <td style="padding: 8px; border-bottom: 1px solid #ddd; font-weight: bold;">${summary.name}</td>
            <td style="padding: 8px; border-bottom: 1px solid #ddd; text-align: right; font-weight: bold; color: #c62828;">₹${String.format(Locale.getDefault(), "%,.2f", summary.total)}</td>
            <td style="padding: 8px; border-bottom: 1px solid #ddd; text-align: right;">${String.format(Locale.getDefault(), "%.1f", summary.percentage * 100)}%</td>
        </tr>
        """.trimIndent()
    }

    val detailedRowsHtml = credits.joinToString("") { credit ->
        """
        <tr>
            <td style="padding: 8px; border-bottom: 1px solid #eee;">${credit.date}</td>
            <td style="padding: 8px; border-bottom: 1px solid #eee;">${credit.caName}</td>
            <td style="padding: 8px; border-bottom: 1px solid #eee; font-weight: bold;">${credit.description}</td>
            <td style="padding: 8px; border-bottom: 1px solid #eee; text-align: right; font-weight: bold; color: #c62828;">-₹${String.format(Locale.getDefault(), "%,.2f", credit.amount)}</td>
        </tr>
        """.trimIndent()
    }

    htmlBuilder.append("""
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
                    margin: 0;
                    color: #333;
                    font-size: 11px;
                    line-height: 1.4;
                }
                .header {
                    text-align: center;
                    border-bottom: 3px double #333;
                    padding-bottom: 10px;
                    margin-bottom: 20px;
                }
                .header h1 {
                    margin: 0 0 4px 0;
                    font-size: 18px;
                    color: #1a237e;
                }
                .header h2 {
                    margin: 0;
                    font-size: 13px;
                    color: #555;
                    font-weight: normal;
                }
                .section-title {
                    font-size: 12px;
                    font-weight: bold;
                    color: #1a237e;
                    border-bottom: 1px solid #1a237e;
                    padding-bottom: 4px;
                    margin-top: 20px;
                    margin-bottom: 10px;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-bottom: 15px;
                }
                th {
                    background-color: #f5f5f5;
                    padding: 8px;
                    text-align: left;
                    font-weight: bold;
                    border-bottom: 2px solid #ddd;
                }
                td {
                    padding: 6px 8px;
                }
                .summary-box {
                    background-color: #ffebee;
                    border: 1px solid #ffcdd2;
                    border-radius: 4px;
                    padding: 12px;
                    margin-bottom: 15px;
                    font-size: 12px;
                }
                .summary-box h3 {
                    margin: 0 0 6px 0;
                    color: #c62828;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>D R INAMDAR PETROLEUM</h1>
                <h2>Monthly Credit (Udhar) Report</h2>
                <div style="margin-top: 5px; font-weight: bold; font-size: 12px; color: #1a237e;">
                    Period: ${monthName} ${year}
                </div>
            </div>

            <div class="summary-box">
                <h3>MONTHLY CREDIT SUMMARY</h3>
                <table style="width: 100%; margin: 0; border: none; background: transparent;">
                    <tr style="background: transparent;">
                        <td style="padding: 0; font-weight: bold;">Total Credit (Udhar) for Month:</td>
                        <td style="padding: 0; text-align: right; font-weight: bold; font-size: 14px; color: #c62828;">₹${String.format(Locale.getDefault(), "%,.2f", totalCredits)}</td>
                    </tr>
                    <tr style="background: transparent;">
                        <td style="padding: 4px 0 0 0;">Total Credit Records:</td>
                        <td style="padding: 4px 0 0 0; text-align: right; font-weight: bold;">${credits.size} entries</td>
                    </tr>
                </table>
            </div>

            <div class="section-title">ACCOUNT / PARTY-WISE CREDIT BREAKDOWN</div>
            <table style="margin-bottom: 25px;">
                <thead>
                    <tr>
                        <th style="width: 50%;">Party / Account Name</th>
                        <th style="text-align: right; width: 30%;">Total Credit</th>
                        <th style="text-align: right; width: 20%;">Percentage</th>
                    </tr>
                </thead>
                <tbody>
                    $breakdownRowsHtml
                </tbody>
            </table>

            <div class="section-title">CHRONOLOGICAL CREDIT LOGS</div>
            <table>
                <thead>
                    <tr>
                        <th style="width: 15%;">Date</th>
                        <th style="width: 25%;">Cashier (CA)</th>
                        <th style="width: 40%;">Description / Party</th>
                        <th style="text-align: right; width: 20%;">Amount</th>
                    </tr>
                </thead>
                <tbody>
                    $detailedRowsHtml
                    <tr style="background-color: #f9f9f9; font-weight: bold; border-top: 2px solid #ddd;">
                        <td colspan="3" style="padding: 8px;">GRAND TOTAL</td>
                        <td style="padding: 8px; text-align: right; color: #c62828;">-₹${String.format(Locale.getDefault(), "%,.2f", totalCredits)}</td>
                    </tr>
                </tbody>
            </table>
        </body>
        </html>
    """.trimIndent())

    val htmlContent = htmlBuilder.toString()
    webView.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
            if (isShare) {
                try {
                    val cacheDir = context.cacheDir
                    val pdfFile = java.io.File(cacheDir, "monthly credit report $monthName $year.pdf")
                    val printAdapter = webView.createPrintDocumentAdapter("monthly credit report $monthName $year")

                    android.print.PrintHelper.savePdf(printAdapter, pdfFile, object : android.print.PrintHelper.PrintCallback {
                        override fun onSuccess() {
                            try {
                                val uri = FileProvider.getUriForFile(context, "com.mypump.cal.fileprovider", pdfFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Monthly Credit Report"))
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Sharing failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onFailure(error: String?) {
                            android.widget.Toast.makeText(context, "PDF generation failed: $error", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Failed to initialize PDF sharing: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
                if (printManager != null) {
                    val jobName = "Monthly_Credit_Report_${monthName}_${year}_${System.currentTimeMillis()}"
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    printManager.print("Monthly Credit Report", printAdapter, null)
                } else {
                    android.widget.Toast.makeText(context, "Printing not supported on this device", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
}
