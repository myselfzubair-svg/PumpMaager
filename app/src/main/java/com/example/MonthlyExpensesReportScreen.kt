package com.example

import android.content.Context
import android.content.Intent
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
import com.example.database.AppDatabase
import com.example.database.SavedAudit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

data class ExpenseRecord(
    val date: String,
    val description: String,
    val amount: Double,
    val caName: String,
    val auditId: Int
)

data class CategorySummary(
    val name: String,
    val total: Double,
    val percentage: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyExpensesReportScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    // Selected Month & Year (0-based Month, e.g. 0 = January, 6 = July)
    val calendar = Calendar.getInstance()
    var selectedMonth by rememberSaveable { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var selectedYear by rememberSaveable { mutableStateOf(calendar.get(Calendar.YEAR)) }

    // Search query
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Load audits from DB
    val allAuditsState = db.savedAuditDao().getAllAudits().collectAsState(initial = emptyList())

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

    // Parse all expenses from audits in parallel/cached
    val parsedExpenses = remember(allAuditsState.value, selectedMonth, selectedYear) {
        val list = mutableListOf<ExpenseRecord>()
        val monthStr = String.format(Locale.getDefault(), "%02d", selectedMonth + 1)
        val yearStr = selectedYear.toString()
        val suffix = "-$monthStr-$yearStr"

        allAuditsState.value.forEach { audit ->
            if (audit.date.endsWith(suffix)) {
                val extracted = parseDetailedExpenses(audit.summaryText)
                extracted.forEach { (desc, amt) ->
                    list.add(
                        ExpenseRecord(
                            date = audit.date,
                            description = desc,
                            amount = amt,
                            caName = audit.caName,
                            auditId = audit.id
                        )
                    )
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

    // Filter parsed expenses by search query
    val filteredExpenses = remember(parsedExpenses, searchQuery) {
        if (searchQuery.isBlank()) {
            parsedExpenses
        } else {
            parsedExpenses.filter {
                it.description.contains(searchQuery, ignoreCase = true) ||
                        it.caName.contains(searchQuery, ignoreCase = true) ||
                        it.date.contains(searchQuery)
            }
        }
    }

    // Key stats
    val totalExpenses = remember(filteredExpenses) {
        filteredExpenses.sumOf { it.amount }
    }

    val highestExpense = remember(filteredExpenses) {
        filteredExpenses.maxByOrNull { it.amount }
    }

    val totalRecordsCount = filteredExpenses.size

    // Category summary aggregation
    val categorySummaries = remember(filteredExpenses, totalExpenses) {
        val breakdown = filteredExpenses.groupBy { it.description.lowercase().trim() }
            .map { (cat, list) ->
                val totalAmount = list.sumOf { it.amount }
                // Use capitalization from the first matching entry
                val originalName = list.firstOrNull()?.description ?: cat.replaceFirstChar { it.uppercase() }
                CategorySummary(
                    name = originalName,
                    total = totalAmount,
                    percentage = if (totalExpenses > 0) (totalAmount / totalExpenses).toFloat() else 0f
                )
            }
            .sortedByDescending { it.total }
        breakdown
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = LanguageManager.translate("Monthly Expenses Report", "मासिक खर्च रिपोर्ट"),
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
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("monthly_expenses_back")
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
                            printMonthlyExpensesReportPdf(
                                context = context,
                                monthName = monthNamesEn[selectedMonth],
                                year = selectedYear,
                                expenses = filteredExpenses,
                                categorySummaries = categorySummaries,
                                totalExpenses = totalExpenses,
                                isShare = false
                            )
                        },
                        enabled = filteredExpenses.isNotEmpty(),
                        modifier = Modifier.testTag("monthly_expenses_print")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print Report"
                        )
                    }
                    IconButton(
                        onClick = {
                            printMonthlyExpensesReportPdf(
                                context = context,
                                monthName = monthNamesEn[selectedMonth],
                                year = selectedYear,
                                expenses = filteredExpenses,
                                categorySummaries = categorySummaries,
                                totalExpenses = totalExpenses,
                                isShare = true
                            )
                        },
                        enabled = filteredExpenses.isNotEmpty(),
                        modifier = Modifier.testTag("monthly_expenses_share")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share PDF"
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
            // MONTH & YEAR NAVIGATION BAR
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
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
                            },
                            modifier = Modifier.testTag("month_prev_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous Month",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
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
                            modifier = Modifier.testTag("month_next_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next Month",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // SEARCH BAR
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = LanguageManager.translate(
                                "Search expenses by description/staff...",
                                "खर्च या कर्मचारी का नाम खोजें..."
                            ),
                            style = MaterialTheme.typography.bodyMedium
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expenses_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }

            if (filteredExpenses.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = LanguageManager.translate(
                                    "No expense entries found",
                                    "कोई खर्च प्रविष्टि नहीं मिली"
                                ),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = LanguageManager.translate(
                                    "Try changing the month or searching another term. Expenses are automatically gathered from saved shifts.",
                                    "महीना बदलें या अन्य खोज शब्द का प्रयास करें। खर्चों को सहेजे गए शिफ्टों से लिया जाता है।"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // HIGH LEVEL SUMMARY CARDS
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Total Expenses Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("card_total_expenses"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = LanguageManager.translate("TOTAL EXPENSES", "कुल खर्च"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "₹" + String.format(Locale.getDefault(), "%,.2f", totalExpenses),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = LanguageManager.translate("$totalRecordsCount entries recorded", "$totalRecordsCount प्रविष्टियां दर्ज"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Highest Expense Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("card_highest_expense"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = LanguageManager.translate("HIGHEST SPEND", "अधिकतम खर्च"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                if (highestExpense != null) {
                                    Text(
                                        text = "₹" + String.format(Locale.getDefault(), "%,.1f", highestExpense.amount),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${highestExpense.description} (${highestExpense.date})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        text = "₹0.00",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "--",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                // CATEGORY WISE SPEND BREAKDOWN
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = LanguageManager.translate("SPEND BY CATEGORY", "श्रेणी अनुसार खर्च"),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            categorySummaries.forEach { summary ->
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = summary.name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "₹" + String.format(Locale.getDefault(), "%,.1f", summary.total) +
                                                    " (${String.format(Locale.getDefault(), "%.1f", summary.percentage * 100)}%)",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { summary.percentage },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }

                // DETAILED EXPENSES LIST HEADER
                item {
                    Text(
                        text = LanguageManager.translate("DETAILED EXPENSE ENTRIES", "विस्तृत खर्च प्रविष्टियां"),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // LIST ENTRIES
                items(filteredExpenses) { expense ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("expense_item_${expense.auditId}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
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
                                            text = expense.date,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Text(
                                        text = "By: ${expense.caName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = expense.description,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "₹" + String.format(Locale.getDefault(), "%,.2f", expense.amount),
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
                trimmed.contains("TALLY RESULT:", ignoreCase = true)
            ) {
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

private fun printMonthlyExpensesReportPdf(
    context: Context,
    monthName: String,
    year: Int,
    expenses: List<ExpenseRecord>,
    categorySummaries: List<CategorySummary>,
    totalExpenses: Double,
    isShare: Boolean = false
) {
    val webView = android.webkit.WebView(context)
    val htmlBuilder = StringBuilder()

    val breakdownRowsHtml = categorySummaries.joinToString("") { summary ->
        """
        <tr>
            <td style="padding: 8px; border-bottom: 1px solid #ddd; font-weight: bold;">${summary.name}</td>
            <td style="padding: 8px; border-bottom: 1px solid #ddd; text-align: right; font-weight: bold; color: #c62828;">₹${String.format(Locale.getDefault(), "%,.2f", summary.total)}</td>
            <td style="padding: 8px; border-bottom: 1px solid #ddd; text-align: right;">${String.format(Locale.getDefault(), "%.1f", summary.percentage * 100)}%</td>
        </tr>
        """.trimIndent()
    }

    val detailedRowsHtml = expenses.joinToString("") { expense ->
        """
        <tr>
            <td style="padding: 8px; border-bottom: 1px solid #eee;">${expense.date}</td>
            <td style="padding: 8px; border-bottom: 1px solid #eee;">${expense.caName}</td>
            <td style="padding: 8px; border-bottom: 1px solid #eee; font-weight: bold;">${expense.description}</td>
            <td style="padding: 8px; border-bottom: 1px solid #eee; text-align: right; font-weight: bold; color: #c62828;">-₹${String.format(Locale.getDefault(), "%,.2f", expense.amount)}</td>
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
                <h2>Monthly Expenses (Kharch) Report</h2>
                <div style="margin-top: 5px; font-weight: bold; font-size: 12px; color: #1a237e;">
                    Period: ${monthName} ${year}
                </div>
            </div>

            <div class="summary-box">
                <h3>MONTHLY SUMMARY</h3>
                <table style="width: 100%; margin: 0; border: none; background: transparent;">
                    <tr style="background: transparent;">
                        <td style="padding: 0; font-weight: bold;">Total Expenses (Kharch) for Month:</td>
                        <td style="padding: 0; text-align: right; font-weight: bold; font-size: 14px; color: #c62828;">₹${String.format(Locale.getDefault(), "%,.2f", totalExpenses)}</td>
                    </tr>
                    <tr style="background: transparent;">
                        <td style="padding: 4px 0 0 0;">Total Expense Records:</td>
                        <td style="padding: 4px 0 0 0; text-align: right; font-weight: bold;">${expenses.size} entries</td>
                    </tr>
                </table>
            </div>

            <div class="section-title">CATEGORY-WISE SPEND BREAKDOWN</div>
            <table style="margin-bottom: 25px;">
                <thead>
                    <tr>
                        <th style="width: 50%;">Category / Description</th>
                        <th style="text-align: right; width: 30%;">Total Spent</th>
                        <th style="text-align: right; width: 20%;">Percentage</th>
                    </tr>
                </thead>
                <tbody>
                    $breakdownRowsHtml
                </tbody>
            </table>

            <div class="section-title">CHRONOLOGICAL EXPENSE ENTRIES</div>
            <table>
                <thead>
                    <tr>
                        <th style="width: 15%;">Date</th>
                        <th style="width: 25%;">Cashier (CA)</th>
                        <th style="width: 40%;">Description / Purpose</th>
                        <th style="text-align: right; width: 20%;">Amount</th>
                    </tr>
                </thead>
                <tbody>
                    $detailedRowsHtml
                    <tr style="background-color: #f9f9f9; font-weight: bold; border-top: 2px solid #ddd;">
                        <td colspan="3" style="padding: 8px;">GRAND TOTAL</td>
                        <td style="padding: 8px; text-align: right; color: #c62828;">-₹${String.format(Locale.getDefault(), "%,.2f", totalExpenses)}</td>
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
                    val pdfFile = java.io.File(cacheDir, "monthly expenses report $monthName $year.pdf")
                    val printAdapter = webView.createPrintDocumentAdapter("monthly expenses report $monthName $year")

                    android.print.PrintHelper.savePdf(printAdapter, pdfFile, object : android.print.PrintHelper.PrintCallback {
                        override fun onSuccess() {
                            try {
                                val uri = FileProvider.getUriForFile(context, "com.mypump.cal.fileprovider", pdfFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Monthly Expenses Report"))
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
                    val jobName = "Monthly_Expenses_Report_${monthName}_${year}_${System.currentTimeMillis()}"
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    printManager.print("Monthly Expenses Report", printAdapter, null)
                } else {
                    android.widget.Toast.makeText(context, "Printing not supported on this device", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
}
