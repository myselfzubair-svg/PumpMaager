package com.example

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AccountBalanceWallet
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
import java.io.File
import java.io.FileOutputStream
import java.util.*

data class CustomerTransaction(
    val date: String,
    val type: TransactionType, // GIVEN (Udhar) or RECOVERED (Jama)
    val amount: Double,
    val caName: String,
    val auditId: Int,
    val timestamp: Long
)

enum class TransactionType {
    GIVEN, RECOVERED
}

data class CustomerLedger(
    val name: String,
    val totalGiven: Double,
    val totalRecovered: Double,
    val balance: Double,
    val transactionsCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerUdhariLedgerReportScreen(
    adminPhone: String,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCustomerName by rememberSaveable { mutableStateOf<String?>(null) }

    val resetAndBack = {
        searchQuery = ""
        selectedCustomerName = null
        onBack()
    }

    BackHandler {
        if (selectedCustomerName != null) {
            selectedCustomerName = null
        } else {
            resetAndBack()
        }
    }

    // Load data from Cloud
    val allCreditsState = com.example.database.SupabaseRepository.getCreditEntriesFlow(adminPhone).collectAsState(initial = emptyList())
    val allRecoveriesState = com.example.database.SupabaseRepository.getRecoveryEntriesFlow(adminPhone).collectAsState(initial = emptyList())

    // Parse all transactions from DB entries
    val parsedTransactions = remember(allCreditsState.value, allRecoveriesState.value) {
        val list = mutableListOf<Pair<String, CustomerTransaction>>() // Name to Transaction

        // 1. Credits Given (Udhar)
        allCreditsState.value.forEach { entry ->
            val cleanedName = entry.party.trim()
            if (cleanedName.isNotEmpty()) {
                list.add(
                    cleanedName to CustomerTransaction(
                        date = entry.date,
                        type = TransactionType.GIVEN,
                        amount = entry.amount,
                        caName = entry.caName,
                        auditId = entry.id?.toInt() ?: 0,
                        timestamp = entry.timestamp
                    )
                )
            }
        }

        // 2. Credits Recovered (Udhari Jama)
        allRecoveriesState.value.forEach { entry ->
            val cleanedName = entry.party.trim()
            if (cleanedName.isNotEmpty()) {
                list.add(
                    cleanedName to CustomerTransaction(
                        date = entry.date,
                        type = TransactionType.RECOVERED,
                        amount = entry.amount,
                        caName = entry.caName,
                        auditId = entry.id?.toInt() ?: 0,
                        timestamp = entry.timestamp
                    )
                )
            }
        }
        list
    }

    // Build overall customer ledgers and list of unique customer names
    val customerLedgers = remember(parsedTransactions) {
        val grouped = parsedTransactions.groupBy { it.first.lowercase().trim() }
        val ledgerList = mutableListOf<CustomerLedger>()

        grouped.forEach { (_, pairs) ->
            val rawName = pairs.first().first // keep original case/spelling from first entry
            var totalGiven = 0.0
            var totalRecovered = 0.0
            pairs.forEach { (_, tx) ->
                if (tx.type == TransactionType.GIVEN) {
                    totalGiven += tx.amount
                } else {
                    totalRecovered += tx.amount
                }
            }
            ledgerList.add(
                CustomerLedger(
                    name = rawName,
                    totalGiven = totalGiven,
                    totalRecovered = totalRecovered,
                    balance = totalGiven - totalRecovered,
                    transactionsCount = pairs.size
                )
            )
        }
        ledgerList.sortedBy { it.name.lowercase().trim() }
    }

    // Filtered customer list based on search query
    val filteredCustomers = remember(customerLedgers, searchQuery) {
        if (searchQuery.isBlank()) {
            customerLedgers
        } else {
            customerLedgers.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Selected customer transactions
    val selectedCustomerTransactions = remember(parsedTransactions, selectedCustomerName) {
        if (selectedCustomerName == null) {
            emptyList()
        } else {
            val key = selectedCustomerName!!.lowercase().trim()
            parsedTransactions
                .filter { it.first.lowercase().trim() == key }
                .map { it.second }
                .sortedWith { o1, o2 ->
                    // Sort by timestamp or date
                    try {
                        val sdf = java.text.SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                        val d1 = sdf.parse(o1.date)
                        val d2 = sdf.parse(o2.date)
                        val dateComp = d1?.compareTo(d2) ?: 0
                        if (dateComp != 0) dateComp else o1.timestamp.compareTo(o2.timestamp)
                    } catch (e: Exception) {
                        o1.timestamp.compareTo(o2.timestamp)
                    }
                }
        }
    }

    val selectedLedgerSummary = remember(customerLedgers, selectedCustomerName) {
        customerLedgers.find { it.name.lowercase().trim() == selectedCustomerName?.lowercase()?.trim() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (selectedCustomerName == null) {
                                LanguageManager.translate("Customer Credit Ledger", "ग्राहक क्रेडिट लेजर")
                            } else {
                                selectedCustomerName!!
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (selectedCustomerName == null) {
                                LanguageManager.translate("Track individual customer udhari balance", "व्यक्तिगत ग्राहक उधार और वसूली को ट्रैक करें")
                            } else {
                                LanguageManager.translate("Running Account Ledger", "चालू खाता बही")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedCustomerName != null) {
                                selectedCustomerName = null
                            } else {
                                resetAndBack()
                            }
                        },
                        modifier = Modifier.testTag("ledger_report_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (selectedCustomerName != null && selectedLedgerSummary != null) {
                        IconButton(
                            onClick = {
                                printCustomerLedgerPdf(
                                    context = context,
                                    customerName = selectedCustomerName!!,
                                    transactions = selectedCustomerTransactions,
                                    summary = selectedLedgerSummary,
                                    isShare = false
                                )
                            },
                            modifier = Modifier.testTag("print_ledger_button")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "Print Ledger")
                        }
                        IconButton(
                            onClick = {
                                printCustomerLedgerPdf(
                                    context = context,
                                    customerName = selectedCustomerName!!,
                                    transactions = selectedCustomerTransactions,
                                    summary = selectedLedgerSummary,
                                    isShare = true
                                )
                            },
                            modifier = Modifier.testTag("share_ledger_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Ledger")
                        }
                    } else {
                        IconButton(onClick = onLogout, modifier = Modifier.testTag("logout_button")) {
                            Icon(Icons.Default.Logout, contentDescription = "Logout")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (selectedCustomerName == null) {
                // SCREEN A: CUSTOMER DIRECTORY / SELECTOR
                Text(
                    text = LanguageManager.translate("Search or Select Customer", "ग्राहक खोजें या चुनें"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(LanguageManager.translate("Type customer name...", "ग्राहक का नाम टाइप करें...")) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("customer_ledger_search_input")
                )

                if (filteredCustomers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) {
                                    LanguageManager.translate("No customer found matching '$searchQuery'", "कोई भी ग्राहक '$searchQuery' से मेल नहीं खाता")
                                } else {
                                    LanguageManager.translate("No credit or recovery transactions recorded in audits yet.", "अभी तक ऑडिट में कोई उधार या वसूली लेनदेन दर्ज नहीं किया गया है।")
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filteredCustomers) { ledger ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCustomerName = ledger.name }
                                    .testTag("customer_item_${ledger.name.lowercase().replace(" ", "_")}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            color = if (ledger.balance > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = if (ledger.balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = ledger.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = LanguageManager.translate(
                                                    "${ledger.transactionsCount} entries",
                                                    "${ledger.transactionsCount} प्रविष्टियाँ"
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "₹" + String.format(Locale.getDefault(), "%,.2f", ledger.balance),
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black),
                                            color = if (ledger.balance > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                        )
                                        Text(
                                            text = if (ledger.balance > 0) {
                                                LanguageManager.translate("Outstanding", "शेष बकाया")
                                            } else {
                                                LanguageManager.translate("Settled / Credit", "संतुलित / जमा")
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (ledger.balance > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color(0xFF2E7D32).copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // SCREEN B: CUSTOMER RUNNING LEDGER TABLE
                if (selectedLedgerSummary != null) {
                    // Summary dashboard card for selected user
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .testTag("ledger_summary_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = selectedCustomerName!!,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = LanguageManager.translate("Udhari Running Ledger Account Statement", "उधारी चालू खाता बही विवरण"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = if (selectedLedgerSummary.balance > 0) MaterialTheme.colorScheme.errorContainer else Color(0xFFE8F5E9),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = if (selectedLedgerSummary.balance > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Column 1: Total Given
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = LanguageManager.translate("Total Given", "कुल दिया उधार"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "₹" + String.format(Locale.getDefault(), "%,.2f", selectedLedgerSummary.totalGiven),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                // Column 2: Total Recovered
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = LanguageManager.translate("Total Recovered", "कुल वसूली"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "₹" + String.format(Locale.getDefault(), "%,.2f", selectedLedgerSummary.totalRecovered),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF2E7D32)
                                    )
                                }

                                // Column 3: Outstanding
                                Column(
                                    modifier = Modifier.weight(1.2f),
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = LanguageManager.translate("Outstanding Bal", "शुद्ध बकाया"),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (selectedLedgerSummary.balance > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                    )
                                    Text(
                                        text = "₹" + String.format(Locale.getDefault(), "%,.2f", selectedLedgerSummary.balance),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                        color = if (selectedLedgerSummary.balance > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = LanguageManager.translate("Statement of Account", "खाते का विवरण (लेजर)"),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(selectedCustomerTransactions) { tx ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = if (tx.type == TransactionType.GIVEN) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else Color(0xFFE8F5E9),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = if (tx.type == TransactionType.GIVEN) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = if (tx.type == TransactionType.GIVEN) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (tx.type == TransactionType.GIVEN) {
                                                    LanguageManager.translate("Udhari Given", "उधार दिया")
                                                } else {
                                                    LanguageManager.translate("Udhari Recovered", "उधारी जमा")
                                                },
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (tx.type == TransactionType.GIVEN) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                            )
                                        }
                                        Text(
                                            text = LanguageManager.translate(
                                                "Recorded on ${tx.date} by ${tx.caName}",
                                                "दर्ज तिथि: ${tx.date} द्वारा: ${tx.caName}"
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = (if (tx.type == TransactionType.GIVEN) "-" else "+") + "₹" + String.format(Locale.getDefault(), "%,.2f", tx.amount),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                    color = if (tx.type == TransactionType.GIVEN) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                )
                            }
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
                trimmed.contains("TALLY RESULT:", ignoreCase = true)
            ) {
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

private fun printCustomerLedgerPdf(
    context: Context,
    customerName: String,
    transactions: List<CustomerTransaction>,
    summary: CustomerLedger,
    isShare: Boolean = false
) {
    val webView = android.webkit.WebView(context)
    val htmlBuilder = java.lang.StringBuilder()

    val detailedRowsHtml = transactions.joinToString("") { tx ->
        val typeColor = if (tx.type == TransactionType.GIVEN) "#c62828" else "#2e7d32"
        val typeSign = if (tx.type == TransactionType.GIVEN) "-" else "+"
        val typeLabel = if (tx.type == TransactionType.GIVEN) "Udhari Given" else "Udhari Recovered"
        
        """
        <tr>
            <td style="padding: 10px; border-bottom: 1px solid #eee;">${tx.date}</td>
            <td style="padding: 10px; border-bottom: 1px solid #eee; font-weight: bold; color: $typeColor;">$typeLabel</td>
            <td style="padding: 10px; border-bottom: 1px solid #eee;">${tx.caName}</td>
            <td style="padding: 10px; border-bottom: 1px solid #eee; text-align: right; font-weight: bold; color: $typeColor;">$typeSign₹${String.format(Locale.getDefault(), "%,.2f", tx.amount)}</td>
        </tr>
        """.trimIndent()
    }

    val balanceColor = if (summary.balance > 0) "#c62828" else "#2e7d32"

    htmlBuilder.append("""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                @page {
                    size: portrait;
                    margin: 10mm;
                }
                body {
                    font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                    margin: 0;
                    color: #333;
                    font-size: 11px;
                    line-height: 1.5;
                }
                .header {
                    text-align: center;
                    border-bottom: 3px double #333;
                    padding-bottom: 12px;
                    margin-bottom: 20px;
                }
                .header h1 {
                    margin: 0 0 4px 0;
                    font-size: 20px;
                    color: #1a237e;
                }
                .header h2 {
                    margin: 0;
                    font-size: 14px;
                    color: #555;
                    font-weight: normal;
                }
                .section-title {
                    font-size: 12px;
                    font-weight: bold;
                    color: #1a237e;
                    border-bottom: 2px solid #1a237e;
                    padding-bottom: 4px;
                    margin-top: 25px;
                    margin-bottom: 10px;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-bottom: 20px;
                }
                th {
                    background-color: #f5f5f5;
                    padding: 10px;
                    text-align: left;
                    font-weight: bold;
                    border-bottom: 2px solid #ddd;
                    color: #333;
                }
                td {
                    padding: 8px 10px;
                }
                .summary-card {
                    background-color: #fcfcfc;
                    border: 1px solid #e0e0e0;
                    border-radius: 6px;
                    padding: 15px;
                    margin-bottom: 20px;
                }
                .summary-title {
                    font-size: 13px;
                    font-weight: bold;
                    color: #1a237e;
                    margin-top: 0;
                    margin-bottom: 10px;
                    text-transform: uppercase;
                }
                .summary-grid {
                    width: 100%;
                    margin: 0;
                }
                .summary-grid td {
                    padding: 4px 0;
                }
                .outstanding-box {
                    font-size: 14px;
                    font-weight: bold;
                    color: $balanceColor;
                    text-align: right;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>D R INAMDAR PETROLEUM</h1>
                <h2>Customer Credit Ledger Statement</h2>
                <div style="margin-top: 5px; font-weight: bold; font-size: 12px; color: #1a237e;">
                    Account of: ${customerName}
                </div>
            </div>

            <div class="summary-card">
                <div class="summary-title">Statement Overview</div>
                <table class="summary-grid" style="border: none; margin-bottom: 0;">
                    <tr style="background: transparent;">
                        <td style="width: 50%; font-size: 12px; font-weight: bold;">Customer Name:</td>
                        <td style="width: 50%; text-align: right; font-size: 12px; font-weight: bold;">${customerName}</td>
                    </tr>
                    <tr style="background: transparent;">
                        <td>Total Udhari Extended (Given):</td>
                        <td style="text-align: right; font-weight: bold; color: #c62828;">₹${String.format(Locale.getDefault(), "%,.2f", summary.totalGiven)}</td>
                    </tr>
                    <tr style="background: transparent;">
                        <td>Total Recoveries Received (Deposited):</td>
                        <td style="text-align: right; font-weight: bold; color: #2e7d32;">₹${String.format(Locale.getDefault(), "%,.2f", summary.totalRecovered)}</td>
                    </tr>
                    <tr style="background: transparent; border-top: 1px solid #ddd;">
                        <td style="padding-top: 10px; font-size: 13px; font-weight: bold;">Net Outstanding Balance:</td>
                        <td style="padding-top: 10px;" class="outstanding-box">₹${String.format(Locale.getDefault(), "%,.2f", summary.balance)}</td>
                    </tr>
                </table>
            </div>

            <div class="section-title">Ledger Transaction History</div>
            <table>
                <thead>
                    <tr>
                        <th style="width: 20%;">Date</th>
                        <th style="width: 30%;">Transaction Type</th>
                        <th style="width: 25%;">Recorded By</th>
                        <th style="width: 25%; text-align: right;">Amount</th>
                    </tr>
                </thead>
                <tbody>
                    $detailedRowsHtml
                </tbody>
            </table>

            <div style="margin-top: 50px; text-align: right; font-size: 10px; color: #777;">
                Report Generated on: ${java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())}<br/>
                Powered by GenovaCare™ Innovations
            </div>
        </body>
        </html>
    """.trimIndent())

    webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "UTF-8", null)

    webView.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
            val jobName = "Customer_Ledger_${customerName.replace(" ", "_")}"
            if (isShare) {
                try {
                    val cacheDir = context.cacheDir
                    val pdfFile = java.io.File(cacheDir, "$jobName.pdf")
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)

                    android.print.PrintHelper.savePdf(printAdapter, pdfFile, object : android.print.PrintHelper.PrintCallback {
                        override fun onSuccess() {
                            try {
                                val uri = FileProvider.getUriForFile(context, "com.mypump.cal.fileprovider", pdfFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Ledger Account Statement - $customerName")
                                    putExtra(Intent.EXTRA_TEXT, "Dear $customerName, please find attached your running ledger account statement for D R INAMDAR PETROLEUM. Outstanding balance is ₹${String.format(Locale.getDefault(), "%,.2f", summary.balance)}.")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Ledger Statement via"))
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
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                } else {
                    android.widget.Toast.makeText(context, "Printing not supported on this device", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
