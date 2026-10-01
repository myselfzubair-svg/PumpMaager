package com.example

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.ManagerTransaction
import com.example.database.SavedAudit
import com.example.database.SupabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CashToBankScreen(
    adminPhone: String,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val calendar = Calendar.getInstance()

    var selectedDate by rememberSaveable {
        mutableStateOf(
            String.format(
                Locale.US,
                "%02d-%02d-%04d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR)
            )
        )
    }

    // --- DATA STATE ---
    var latestReport by remember { mutableStateOf<SavedAudit?>(null) }
    var lastSettledReport by remember { mutableStateOf<SavedAudit?>(null) }
    var dailyCashRecords by remember { mutableStateOf<List<com.example.database.DailyCashRecord>>(emptyList()) }
    
    // sessionDraftTransactions replaces live managerTransactions for new entries
    val sessionDraftTransactions = remember { mutableStateListOf<ManagerTransaction>() }
    
    var udhariNamesList by remember { mutableStateOf(listOf("Miscellaneous")) }
    var openingBalance by remember { mutableStateOf(0.0) }
    var isLoading by remember { mutableStateOf(true) }

    // --- REFRESH DATA ---
    fun refreshData() {
        if (adminPhone.isBlank()) return
        isLoading = true
        coroutineScope.launch {
            val names = SupabaseRepository.getUdhariNames(adminPhone)
            val latest = SupabaseRepository.getLatestManagerReport(adminPhone)
            val lastSettled = SupabaseRepository.getLatestSettledManagerReport(adminPhone)
            
            val settledAnchorTimestamp = lastSettled?.timestamp ?: 0L
            val latestReportTimestamp = latest?.timestamp ?: 0L
            
            val upToNow = System.currentTimeMillis()
            
            // Fetch direct handover sums from the Audit reports instead of the summary table
            val records = SupabaseRepository.getDailyCashHandoverFromAudits(adminPhone, settledAnchorTimestamp, upToNow)
            val todayStr = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())

            withContext(Dispatchers.Main) {
                udhariNamesList = names
                latestReport = latest
                lastSettledReport = lastSettled
                // Exclude today's date cash so only cash till yesterday is displayed as available cash
                dailyCashRecords = records.filter { it.timestamp > latestReportTimestamp && it.date != todayStr }
                
                openingBalance = if (latest != null && !latest.isSettled) {
                    latest.manualDifference
                } else 0.0
                
                isLoading = false
            }
        }
    }

    LaunchedEffect(adminPhone) {
        refreshData()
    }

    // --- CALCULATIONS ---
    val effectiveReportDate = dailyCashRecords.lastOrNull()?.date ?: selectedDate
    
    // Formatting the date for display: dd MMMM yyyy
    val displayDate = remember(effectiveReportDate) {
        try {
            val inputSdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
            val outputSdf = SimpleDateFormat("dd MMMM yyyy", Locale.US)
            inputSdf.parse(effectiveReportDate)?.let { outputSdf.format(it) } ?: effectiveReportDate
        } catch (e: Exception) { effectiveReportDate }
    }

    val totalDailyCash = dailyCashRecords.sumOf { it.totalCash }

    val udhariRecoveredDrafts = sessionDraftTransactions.filter { it.type == "MANAGER_JAMA" }
    val loansBorrowedDrafts = sessionDraftTransactions.filter { it.type in listOf("LOAN_BORROWED", "OWNER_BORROWED") }
    val totalAdditions = udhariRecoveredDrafts.sumOf { it.amount } + loansBorrowedDrafts.sumOf { it.amount }
    
    val expensesDrafts = sessionDraftTransactions.filter { it.type in listOf("MANAGER_EXPENSE", "SALON_EXPENSE") }
    val udhariGivenDrafts = sessionDraftTransactions.filter { it.type == "MANAGER_UDHAR" }
    val loansClearedDrafts = sessionDraftTransactions.filter { it.type in listOf("LOAN_CLEARED", "OWNER_WITHDRAWAL") }
    val totalDeductions = expensesDrafts.sumOf { it.amount } + udhariGivenDrafts.sumOf { it.amount } + loansClearedDrafts.sumOf { it.amount }
    
    val cashAvailableToDeposit = openingBalance + totalDailyCash + totalAdditions
    val netCashBeforeBankDeposit = cashAvailableToDeposit - totalDeductions
    
    val bankDepositsDrafts = sessionDraftTransactions.filter { it.type == "BANK_DEPOSIT" }
    val totalBankDeposited = bankDepositsDrafts.sumOf { it.amount }
    val difference = netCashBeforeBankDeposit - totalBankDeposited

    // --- DIALOG STATES ---
    var showActionDialog by remember { mutableStateOf<String?>(null) }
    var transactionAmount by remember { mutableStateOf("") }
    var transactionDescription by remember { mutableStateOf("") }
    var transactionParty by remember { mutableStateOf("Miscellaneous") }
    var transactionDate by remember { mutableStateOf(selectedDate) }
    
    var showDeleteConfirm by remember { mutableStateOf<ManagerTransaction?>(null) }
    var isSavingReport by remember { mutableStateOf(false) }

    var showAddNewNameDialog by remember { mutableStateOf<String?>(null) } // "UDHARI" or "JAMA"
    var newNameInput by remember { mutableStateOf("") }

    // --- HELPERS ---
    fun saveReport(isSettled: Boolean) {
        if (dailyCashRecords.isEmpty() && sessionDraftTransactions.isEmpty()) {
            Toast.makeText(context, "No new data to save", Toast.LENGTH_SHORT).show()
            return
        }
        isSavingReport = true
        coroutineScope.launch {
            try {
                val reportId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()
                
                // 1. Save all drafts to DB first
                sessionDraftTransactions.forEach { draft ->
                    SupabaseRepository.saveManagerTransaction(draft.copy(reportId = reportId))
                }

                // 2. Build and save main audit report
                val html = buildManagerReportHtml(
                    effectiveReportDate, reportId, openingBalance, dailyCashRecords, 
                    udhariRecoveredDrafts, loansBorrowedDrafts, 
                    expensesDrafts, udhariGivenDrafts, loansClearedDrafts,
                    bankDepositsDrafts, totalAdditions, totalDeductions, 
                    cashAvailableToDeposit, netCashBeforeBankDeposit, 
                    totalBankDeposited, difference, isSettled
                )

                val report = SavedAudit(
                    ownerAdminPhone = adminPhone,
                    date = effectiveReportDate, // Using calculated effective date
                    caName = "Manager",
                    meterNo = "Manager Report",
                    auditType = "Manager's Report",
                    summaryText = "Manager's Report | Status: ${if (isSettled) "SETTLED" else "CARRY FORWARD"}",
                    htmlContent = html,
                    timestamp = timestamp,
                    reportId = reportId,
                    totalFuelSalesAmount = totalDailyCash,
                    totalUdhariJama = totalAdditions,
                    totalKharch = totalDeductions,
                    expectedCashBalance = netCashBeforeBankDeposit,
                    tallyDifference = difference,
                    isSettled = isSettled,
                    openingBalance = openingBalance,
                    manualDifference = difference,
                    lastSettledReportDate = lastSettledReport?.date ?: "Start",
                    dailyCashDetails = dailyCashRecords.joinToString("; ") { "${it.date}: ₹${formatDouble(it.totalCash)}" },
                    cashAvailableToDeposit = cashAvailableToDeposit,
                    actualDepositedAmount = totalBankDeposited,
                    openingBalanceUsed = openingBalance
                )
                
                SupabaseRepository.saveManagerReport(report, emptyList(), emptyList())
                
                withContext(Dispatchers.Main) {
                    isSavingReport = false
                    Toast.makeText(context, "Report saved Successfully!", Toast.LENGTH_LONG).show()
                    onBack()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSavingReport = false
                    Toast.makeText(context, "Error saving report: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manager's Report ($displayDate)", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, "Logout") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. OPENING BALANCE
                item {
                    SectionHeader("OPENING BALANCE")
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Carry Forward Amount", fontWeight = FontWeight.Medium)
                            Text("₹${formatDouble(openingBalance)}", fontWeight = FontWeight.Black)
                        }
                    }
                }

                // 2. System Cash (Handovers) - NO HEADING LABEL
                if (dailyCashRecords.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(16.dp)) {
                                dailyCashRecords.forEach { record ->
                                    EntryRow(record.date, "", "", record.totalCash, true, onDelete = null)
                                }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TOTAL DAILY CASH", fontWeight = FontWeight.Black)
                                    Text("₹${formatDouble(totalDailyCash)}", fontWeight = FontWeight.Black, color = Color(0xFF2563EB))
                                }
                            }
                        }
                    }
                }

                // 3. CASH ADDITIONS (Grouping)
                item {
                    SectionHeader("CASH ADDITIONS")
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp)) {
                            if (udhariRecoveredDrafts.isEmpty() && loansBorrowedDrafts.isEmpty()) {
                                Text("No additions recorded", color = Color.Gray, fontSize = 12.sp)
                            } else {
                                if (udhariRecoveredDrafts.isNotEmpty()) {
                                    Text("UDHARI RECOVERED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    udhariRecoveredDrafts.forEach { draft ->
                                        EntryRow(draft.date, "Jama", draft.description ?: "", draft.amount, true, onDelete = { showDeleteConfirm = draft })
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                                if (loansBorrowedDrafts.isNotEmpty()) {
                                    Text("LOAN BORROWED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    loansBorrowedDrafts.forEach { draft ->
                                        EntryRow(draft.date, "Loan", draft.description ?: "", draft.amount, true, onDelete = { showDeleteConfirm = draft })
                                    }
                                }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TOTAL ADDITIONS", fontWeight = FontWeight.Black)
                                    Text("₹${formatDouble(totalAdditions)}", fontWeight = FontWeight.Black, color = Color(0xFF10B981))
                                }
                            }
                        }
                    }
                }

                // 4. CASH AVAILABLE
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CASH AVAILABLE TO DEPOSIT", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("₹${formatDouble(cashAvailableToDeposit)}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // Categorical detail sections
                // 5. EXPENSES
                item {
                    SectionHeader("EXPENSES")
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp)) {
                            if (expensesDrafts.isEmpty()) {
                                Text("No manager expenses", color = Color.Gray, fontSize = 12.sp)
                            } else {
                                expensesDrafts.forEach { draft ->
                                    EntryRow(draft.date, "Expense", draft.description ?: "", draft.amount, false, onDelete = { showDeleteConfirm = draft })
                                }
                            }
                        }
                    }
                }

                // 6. UDHARI
                item {
                    SectionHeader("UDHARI")
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp)) {
                            if (udhariGivenDrafts.isEmpty()) {
                                Text("No udhari entries", color = Color.Gray, fontSize = 12.sp)
                            } else {
                                udhariGivenDrafts.forEach { draft ->
                                    EntryRow(draft.date, "Udhar", draft.description ?: "", draft.amount, false, onDelete = { showDeleteConfirm = draft })
                                }
                            }
                        }
                    }
                }

                // 7. LOAN CLEARED
                item {
                    SectionHeader("LOAN CLEARED")
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp)) {
                            if (loansClearedDrafts.isEmpty()) {
                                Text("No loans cleared", color = Color.Gray, fontSize = 12.sp)
                            } else {
                                loansClearedDrafts.forEach { draft ->
                                    EntryRow(draft.date, "Cleared", draft.description ?: "", draft.amount, false, onDelete = { showDeleteConfirm = draft })
                                }
                            }
                        }
                    }
                }

                // 8. NET CASH
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("TOTAL DEDUCTIONS", fontWeight = FontWeight.Bold)
                                Text("₹${formatDouble(totalDeductions)}", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("NET CASH BEFORE BANK DEPOSIT", fontWeight = FontWeight.Black, fontSize = 13.sp)
                                Text("₹${formatDouble(netCashBeforeBankDeposit)}", fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                        }
                    }
                }

                // 9. BANK DEPOSIT
                item {
                    SectionHeader("BANK DEPOSIT")
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp)) {
                            if (bankDepositsDrafts.isEmpty()) {
                                Text("No bank deposits recorded", color = Color.Gray, fontSize = 12.sp)
                            } else {
                                bankDepositsDrafts.forEach { draft ->
                                    EntryRow(draft.date, "Deposit", draft.description ?: "", draft.amount, false, onDelete = { showDeleteConfirm = draft })
                                }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TOTAL BANK DEPOSITED", fontWeight = FontWeight.Black)
                                    Text("₹${formatDouble(totalBankDeposited)}", fontWeight = FontWeight.Black)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { showActionDialog = "DEPOSIT" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("ADD BANK DEPOSIT")
                            }
                        }
                    }
                }

                // 10. DIFFERENCE
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DIFFERENCE", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                            Text("₹${formatDouble(difference)}", color = if (Math.abs(difference) < 1.0) Color(0xFF10B981) else Color(0xFFF87171), fontSize = 36.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // 11. ACTIONS
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { saveReport(true) }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)), enabled = !isSavingReport) {
                            Text("SETTLE", fontWeight = FontWeight.Black)
                        }
                        Button(onClick = { saveReport(false) }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)), enabled = !isSavingReport) {
                            Text("CARRY FORWARD", fontWeight = FontWeight.Black)
                        }
                    }
                }

                // 12. QUICK ADD
                item {
                    Text("Add Transaction", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallActionButton("Expense", Icons.Default.Receipt) { showActionDialog = "EXPENSE" }
                        SmallActionButton("Jama", Icons.Default.TrendingUp) { showActionDialog = "JAMA" }
                        SmallActionButton("Udhar", Icons.Default.TrendingDown) { showActionDialog = "UDHAR" }
                        SmallActionButton("Loan In", Icons.Default.AddCard) { showActionDialog = "LOAN_IN" }
                        SmallActionButton("Loan Out", Icons.Default.CreditCardOff) { showActionDialog = "LOAN_OUT" }
                    }
                }
            }
        }
    }

    // --- TRANSACTION DIALOG ---
    if (showActionDialog != null) {
        val type = showActionDialog!!
        AlertDialog(
            onDismissRequest = { showActionDialog = null; transactionAmount = ""; transactionDescription = ""; transactionParty = "Miscellaneous"; transactionDate = selectedDate },
            title = { Text(when(type) {
                "DEPOSIT" -> "Add Bank Deposit"
                "EXPENSE" -> "Add Manager Expense"
                "JAMA" -> "Add Udhari Recovered"
                "UDHAR" -> "Add Udhari Entry"
                "LOAN_IN" -> "Add Loan Borrowed"
                "LOAN_OUT" -> "Add Loan Cleared"
                else -> ""
            }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (type == "DEPOSIT") {
                        Box(modifier = Modifier.fillMaxWidth().clickable {
                            DatePickerDialog(context, { _, y, m, d -> transactionDate = String.format(Locale.US, "%02d-%02d-%04d", d, m + 1, y) }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                        }) {
                            OutlinedTextField(
                                value = transactionDate,
                                onValueChange = { },
                                label = { Text("Deposit Date") },
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                trailingIcon = { Icon(Icons.Default.DateRange, null) }
                            )
                        }
                    }
                    if (type == "JAMA" || type == "UDHAR") {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            VoiceOutlinedTextField(value = transactionParty, onValueChange = { transactionParty = it }, label = { Text("Party Name") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { expanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } })
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                udhariNamesList.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { transactionParty = name; expanded = false }) }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("+ Add New ${if(type=="JAMA") "Jama" else "Udhari"} Name", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                                    onClick = { expanded = false; showAddNewNameDialog = if(type=="JAMA") "JAMA" else "UDHARI" }
                                )
                            }
                        }
                    }
                    VoiceOutlinedTextField(value = transactionAmount, onValueChange = { transactionAmount = it }, label = { Text("Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    VoiceOutlinedTextField(value = transactionDescription, onValueChange = { transactionDescription = it }, label = { Text("Description${if(type=="EXPENSE") "*" else " (Optional)"}") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = transactionAmount.toDoubleOrNull() ?: 0.0
                    if (amt <= 0) return@Button
                    if (type == "EXPENSE" && transactionDescription.trim().isBlank()) {
                        Toast.makeText(context, "Description is mandatory for expenses", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val txType = when(type) {
                        "DEPOSIT" -> "BANK_DEPOSIT"
                        "EXPENSE" -> "MANAGER_EXPENSE"
                        "JAMA" -> "MANAGER_JAMA"
                        "UDHAR" -> "MANAGER_UDHAR"
                        "LOAN_IN" -> "LOAN_BORROWED"
                        "LOAN_OUT" -> "LOAN_CLEARED"
                        else -> return@Button
                    }
                    val desc = if (type == "JAMA" || type == "UDHAR") "[$transactionParty] ${transactionDescription.trim()}" else transactionDescription.trim()
                    
                    sessionDraftTransactions.add(ManagerTransaction(
                        ownerAdminPhone = adminPhone, 
                        date = if(type=="DEPOSIT") transactionDate else selectedDate, 
                        amount = amt, 
                        type = txType, 
                        description = desc.ifBlank { null }
                    ))
                    
                    showActionDialog = null; transactionAmount = ""; transactionDescription = ""; transactionParty = "Miscellaneous"
                }) { Text("Add to Session") }
            },
            dismissButton = { TextButton(onClick = { showActionDialog = null }) { Text("Cancel") } }
        )
    }

    if (showAddNewNameDialog != null) {
        AlertDialog(
            onDismissRequest = { showAddNewNameDialog = null; newNameInput = "" },
            title = { Text("Add New Name") },
            text = { VoiceOutlinedTextField(value = newNameInput, onValueChange = { newNameInput = it }, label = { Text("Enter Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = {
                    val name = newNameInput.trim()
                    if (name.isNotEmpty()) {
                        coroutineScope.launch {
                            SupabaseRepository.saveUdhariNames(adminPhone, listOf(name))
                            val updated = SupabaseRepository.getUdhariNames(adminPhone)
                            withContext(Dispatchers.Main) {
                                udhariNamesList = updated
                                transactionParty = name
                                newNameInput = ""
                                showAddNewNameDialog = null
                            }
                        }
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddNewNameDialog = null }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to remove this draft entry from your session?") },
            confirmButton = { Button(onClick = { sessionDraftTransactions.remove(showDeleteConfirm!!); showDeleteConfirm = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
fun EntryRow(date: String, label: String, detail: String, amount: Double, isAddition: Boolean, onDelete: (() -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            if (label.isNotEmpty()) {
                Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text("${if (detail.isNotEmpty()) "$detail | " else ""}$date", fontSize = 11.sp, color = Color.Gray)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (isAddition) "+" else "-") + "₹${formatDouble(amount)}", fontWeight = FontWeight.Black, color = if (isAddition) Color(0xFF10B981) else Color(0xFFEF4444))
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.5f), modifier = Modifier.size(18.dp)) }
            } else {
                Spacer(Modifier.width(32.dp))
            }
        }
    }
}

@Composable
fun SmallActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(modifier = Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun buildManagerReportHtml(
    date: String, reportId: String, openingBalance: Double, dailyCash: List<com.example.database.DailyCashRecord>,
    jama: List<ManagerTransaction>, loansIn: List<ManagerTransaction>,
    expenses: List<ManagerTransaction>, udhar: List<ManagerTransaction>, loansOut: List<ManagerTransaction>,
    deposits: List<ManagerTransaction>, totalAdditions: Double, totalDeductions: Double, cashAvailable: Double, 
    netCash: Double, totalDeposited: Double, diff: Double, isSettled: Boolean
): String {
    fun buildCatTable(title: String, list: List<ManagerTransaction>, sign: String, cls: String): String {
        if (list.isEmpty()) return ""
        return "<h3>$title</h3><table><tbody>" + list.joinToString("") { "<tr><td>${it.description ?: "-"}</td><td class='num $cls'>$sign₹${formatDouble(it.amount)}</td></tr>" } + "</tbody></table>"
    }

    return """
        <html><head><style>
            body { font-family: sans-serif; font-size: 11px; }
            h2 { text-align: center; color: #1a237e; border-bottom: 2px solid #1a237e; }
            h3 { background: #eee; padding: 4px; border-left: 4px solid #1a237e; margin-top: 15px; }
            table { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
            th, td { border: 1px solid #ddd; padding: 5px; text-align: left; }
            .num { text-align: right; font-family: monospace; }
            .total { font-weight: bold; background: #f5f5f5; }
            .addition { color: green; } .deduction { color: red; }
        </style></head><body>
            <h1 class="center">MANAGER'S REPORT</h1>
            <p><b>Date:</b> $date | <b>Report ID:</b> ${reportId.take(8)}</p>
            
            <h3>OPENING BALANCE</h3>
            <table><tr><td>Carry Forward Amount</td><td class="num">₹${formatDouble(openingBalance)}</td></tr></table>
            
            <h3>CASH HANDOVER (System Cash)</h3>
            <table>
                <tbody>
                    ${dailyCash.joinToString("") { "<tr><td>${it.date}</td><td class='num'>₹${formatDouble(it.totalCash)}</td></tr>" }}
                    <tr class="total"><td>TOTAL SYSTEM CASH</td><td class="num">₹${formatDouble(dailyCash.sumOf { it.totalCash })}</td></tr>
                </tbody>
            </table>
            
            <h3>CASH ADDITIONS</h3>
            ${buildCatTable("UDHARI RECOVERED (JAMA)", jama, "+", "addition")}
            ${buildCatTable("LOAN BORROWED", loansIn, "+", "addition")}
            
            <h3>CASH AVAILABLE TO DEPOSIT</h3><table><tr class="total" style="font-size: 13px; background: #e8eaf6;"><td>TOTAL AVAILABLE</td><td class="num">₹${formatDouble(cashAvailable)}</td></tr></table>
            
            <h3>DEDUCTIONS</h3>
            ${buildCatTable("EXPENSES", expenses, "-", "deduction")}
            ${buildTableManual("UDHARI", udhar, "-", "deduction")}
            ${buildCatTable("LOAN CLEARED", loansOut, "-", "deduction")}
            
            <h3>NET CASH BEFORE BANK DEPOSIT</h3><table><tr class="total"><td>NET CASH</td><td class="num">₹${formatDouble(netCash)}</td></tr></table>
            
            <h3>BANK DEPOSIT</h3>
            <table>
                <tbody>
                    ${deposits.joinToString("") { "<tr><td>${it.date}: ${it.description ?: "Bank Deposit"}</td><td class='num'>₹${formatDouble(it.amount)}</td></tr>" }}
                    <tr class="total"><td>TOTAL BANK DEPOSITED</td><td class="num">₹${formatDouble(totalDeposited)}</td></tr>
                </tbody>
            </table>
            
            <h3>DIFFERENCE</h3>
            <table style="background: ${if (Math.abs(diff) < 1.0) "#e8f5e9" else "#ffebee"};">
                <tr class="total" style="font-size: 14px;">
                    <td>DIFFERENCE (${if (isSettled) "SETTLED" else "CARRIED FORWARD"})</td>
                    <td class="num">₹${formatDouble(diff)}</td>
                </tr>
            </table>
            <p>Final Status: ${if (isSettled) "SETTLED" else "CARRY FORWARD: ₹${formatDouble(diff)}"}</p>
        </body></html>
    """.trimIndent()
}

// Helper to keep names clear in PDF manual sections
private fun buildTableManual(title: String, list: List<ManagerTransaction>, sign: String, cls: String): String {
    if (list.isEmpty()) return ""
    return "<h3>$title</h3><table><tbody>" + list.joinToString("") { "<tr><td>${it.description ?: "-"}</td><td class='num $cls'>$sign₹${formatDouble(it.amount)}</td></tr>" } + "</tbody></table>"
}
