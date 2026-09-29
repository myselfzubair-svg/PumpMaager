package com.example

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.database.HistoryViewModel
import com.example.database.SavedAudit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    adminPhone: String,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(adminPhone) {
        viewModel.setAdminPhone(adminPhone)
    }

    val audits by viewModel.allAudits.collectAsStateWithLifecycle()

    var selectedAudit by remember { mutableStateOf<SavedAudit?>(null) }
    var auditToEdit by remember { mutableStateOf<SavedAudit?>(null) }
    var showDeleteConfirmAll by remember { mutableStateOf(false) }
    var auditToDelete by remember { mutableStateOf<SavedAudit?>(null) }
    var selectedFilterDate by remember { mutableStateOf<String?>(null) }
    
    // Tracks which groups are expanded
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = LanguageManager.translate("Saved Reports", "सहेजे गए रिपोर्ट्स"),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("history_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Back"
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
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            if (audits.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "Empty History",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = LanguageManager.translate("No Saved Reports", "कोई रिपोर्ट सहेजी नहीं गई"),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = LanguageManager.translate(
                            "Complete a reconciliation or sales sheet, then tap the 'Save' button to persist it to your secure Cloud Database.",
                            "एक मिलान या बिक्री शीट पूरी करें, फिर इसे अपने सुरक्षित क्लाउड डेटाबेस में सहेजने के लिए 'सेव' बटन पर टैप करें।"
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Filter bar
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                    ) {
                        Column {
                            // Date Filter Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val calendar = java.util.Calendar.getInstance()
                                        android.app.DatePickerDialog(
                                            context,
                                            { _, year, month, dayOfMonth ->
                                                val formattedDay = String.format("%02d", dayOfMonth)
                                                val formattedMonth = String.format("%02d", month + 1)
                                                selectedFilterDate = "$formattedDay-$formattedMonth-$year"
                                            },
                                            calendar.get(java.util.Calendar.YEAR),
                                            calendar.get(java.util.Calendar.MONTH),
                                            calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Select Date",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (selectedFilterDate != null) "Date: $selectedFilterDate" else "Filter by Date",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selectedFilterDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selectedFilterDate != null) {
                                    IconButton(onClick = { selectedFilterDate = null }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    val filteredAudits = remember(audits, selectedFilterDate) {
                        val dateFiltered = if (selectedFilterDate == null) {
                            audits
                        } else {
                            audits.filter { it.date == selectedFilterDate }
                        }
                        
                        // Sort by timestamp descending (Recent first)
                        dateFiltered.sortedByDescending { it.timestamp }
                    }

                    val groupedAudits = remember(filteredAudits) {
                        filteredAudits.groupBy { audit ->
                            audit.meterNo.trim().ifEmpty { "N/A" }
                        }.toSortedMap { a, b ->
                            if (a == "Manager Report" && b != "Manager Report") 1
                            else if (a != "Manager Report" && b == "Manager Report") -1
                            else {
                                val aInt = a.filter { it.isDigit() }.toIntOrNull()
                                val bInt = b.filter { it.isDigit() }.toIntOrNull()
                                if (aInt != null && bInt != null) aInt.compareTo(bInt)
                                else a.compareTo(b, ignoreCase = true)
                            }
                        }
                    }

                    if (filteredAudits.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "No reports found",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = LanguageManager.translate("No Reports Found", "कोई रिपोर्ट नहीं मिली"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = LanguageManager.translate(
                                    "Try choosing a different date or tab to view your records.",
                                    "अलग तारीख या टैब चुनने का प्रयास करें।"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            
                            
                            groupedAudits.forEach { entry ->
                                val meterNo = entry.key
                                val groupList = entry.value
                                val groupMaxTimestamp = groupList.maxOfOrNull { it.timestamp } ?: 0L
                                // Start collapsed by default so they act like dropdowns
                                val isExpanded = expandedGroups[meterNo] ?: false

                                item {
                                    val isManagerGroup = meterNo == "Manager Report"
                                    Surface(
                                        color = if (isManagerGroup) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.clickable { 
                                            expandedGroups[meterNo] = !isExpanded 
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isManagerGroup) "Manager Report" else "Nozzle No: $meterNo",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                                                color = if (isManagerGroup) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                            )
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                
                                if (isExpanded) {
                                    items(groupList, key = { it.timestamp }) { audit ->
                                        val isGroupNewest = audit.timestamp == groupMaxTimestamp
                                        
                                        AuditHistoryCard(
                                            audit = audit,
                                            onView = { selectedAudit = audit },
                                            onEdit = { auditToEdit = audit },
                                            onShare = {
                                                PdfGenerator.sharePdf(context, audit.summaryText, audit.caName, audit.date)
                                            },
                                            onPrint = {
                                                printSavedReportHtml(context, audit)
                                            },
                                            onDelete = { 
                                                if (isGroupNewest) {
                                                    auditToDelete = audit
                                                } else {
                                                    Toast.makeText(context, "Only the newest report for this nozzle can be deleted", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            showDelete = isGroupNewest
                                        )
                                    }
                                }
                                
                                item { Spacer(modifier = Modifier.height(4.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }

    // Details View Dialog
    if (selectedAudit != null) {
        val audit = selectedAudit!!
        val isManagerReport = audit.auditType == "Manager's Report"
        var managerTransactions by remember { mutableStateOf<List<com.example.database.ManagerTransaction>>(emptyList()) }
        
        LaunchedEffect(audit) {
            if (isManagerReport) {
                managerTransactions = com.example.database.SupabaseRepository.getManagerTransactionsByReportId(adminPhone, audit.reportId)
            }
        }

        AlertDialog(
            onDismissRequest = { selectedAudit = null },
            title = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = audit.auditType,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(audit.summaryText))
                                Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy text summary",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = "CA: ${audit.caName.ifEmpty { "N/A" }} | Date: ${audit.date}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isManagerReport) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Opening Balance:", style = MaterialTheme.typography.labelMedium)
                                    Text("₹${formatDouble(audit.openingBalanceUsed)}", fontWeight = FontWeight.Bold)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Cash Available:", style = MaterialTheme.typography.labelMedium)
                                    Text("₹${formatDouble(audit.cashAvailableToDeposit)}", fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Final Difference:", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black))
                                    Text("₹${formatDouble(audit.manualDifference)}", fontWeight = FontWeight.Black, color = Color(0xFFC62828))
                                }
                                Text(
                                    text = if (audit.isSettled) "Report Status: SETTLED (Next start at ₹0)" else "Report Status: CARRIED FORWARD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (audit.isSettled) Color(0xFF15803D) else Color(0xFF1E293B),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Text("Transaction Details", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(8.dp)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(managerTransactions) { tx ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(0.3f)) {
                                            Text(tx.date, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        }
                                        Column(Modifier.weight(0.7f)) {
                                            Text(tx.type.replace("_", " "), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                            if (!tx.description.isNullOrBlank()) {
                                                Text(tx.description, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            }
                                        }
                                        val sign = if (tx.type.contains("JAMA") || tx.type.contains("BORROWED")) "+" else "-"
                                        val color = if (sign == "+") Color(0xFF15803D) else Color(0xFFC62828)
                                        Text("$sign₹${formatDouble(tx.amount)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black), color = color)
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                }
                                if (managerTransactions.isEmpty()) {
                                    item { Text("No individual transactions linked.", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(12.dp)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Text(
                                        text = audit.summaryText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        PdfGenerator.sharePdf(context, audit.summaryText, audit.caName, audit.date)
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAudit = null }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Delete single item confirmation
    if (auditToDelete != null) {
        val audit = auditToDelete!!
        AlertDialog(
            onDismissRequest = { auditToDelete = null },
            title = { Text(LanguageManager.translate("Delete Report?", "रिपोर्ट हटाएं?")) },
            text = {
                Text(
                    LanguageManager.translate(
                        "Are you sure you want to permanently delete this report and all associated data? This action cannot be undone.",
                        "क्या आप वाकई इस रिपोर्ट और सभी संबंधित डेटा को स्थायी रूप से हटाना चाहते हैं? यह क्रिया पूर्ववत नहीं की जा सकती।"
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAudit(adminPhone, audit.date, audit.caName, audit.timestamp, audit.reportId)
                        auditToDelete = null
                        Toast.makeText(context, LanguageManager.translate("Report and all associated data were permanently deleted.", "रिपोर्ट और सभी संबंधित डेटा स्थायी रूप से हटा दिए गए।"), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(LanguageManager.translate("Delete", "हटाएं"))
                }
            },
            dismissButton = {
                TextButton(onClick = { auditToDelete = null }) {
                    Text(LanguageManager.translate("Cancel", "रद्द करें"))
                }
            }
        )
    }

    // Delete all items confirmation
    if (showDeleteConfirmAll) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmAll = false },
            title = { Text("Delete All Saved Reports?") },
            text = {
                Text(
                    "Are you absolutely sure you want to clear your entire saved history? This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAll(adminPhone)
                        showDeleteConfirmAll = false
                        Toast.makeText(context, "All records deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmAll = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (auditToEdit != null) {
        val audit = auditToEdit!!
        var editedCaName by remember(audit) { mutableStateOf(audit.caName) }
        var editedMeterNo by remember(audit) { mutableStateOf(audit.meterNo) }
        var editedSummaryText by remember(audit) { mutableStateOf(audit.summaryText) }

        AlertDialog(
            onDismissRequest = { auditToEdit = null },
            title = {
                Text(
                    text = LanguageManager.translate("Edit Saved Report", "सहेजी गई रिपोर्ट संपादित करें"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VoiceOutlinedTextField(
                        value = editedCaName,
                        onValueChange = { editedCaName = it },
                        label = { Text(LanguageManager.translate("CA Name / Employee", "सीए नाम / कर्मचारी")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    VoiceOutlinedTextField(
                        value = editedMeterNo,
                        onValueChange = { editedMeterNo = it },
                        label = { Text(LanguageManager.translate("Nozzle Number", "नोजल नंबर")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    VoiceOutlinedTextField(
                        value = editedSummaryText,
                        onValueChange = { editedSummaryText = it },
                        label = { Text(LanguageManager.translate("Report Summary", "रिपोर्ट सारांश")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 300.dp),
                        maxLines = 15,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedAudit = audit.copy(
                            caName = editedCaName,
                            meterNo = editedMeterNo,
                            summaryText = editedSummaryText
                        )
                        viewModel.updateAudit(updatedAudit)
                        auditToEdit = null
                        Toast.makeText(context, "Report updated successfully", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(LanguageManager.translate("Save", "सहेजें"))
                }
            },
            dismissButton = {
                TextButton(onClick = { auditToEdit = null }) {
                    Text(LanguageManager.translate("Cancel", "रद्द करें"))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun AuditHistoryCard(
    audit: SavedAudit,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isManagerReport = audit.auditType == "Manager's Report"
    val badgeColor = when {
        isManagerReport -> Color(0xFF1E3A8A) // Dark Blue for Manager
        audit.auditType.contains("2") -> Color(0xFF0284C7) // Sky blue
        audit.auditType.contains("4") -> Color(0xFF0D9488) // Teal
        else -> Color(0xFF7C3AED) // Purple
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history_card_${audit.timestamp}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(badgeColor, shape = CircleShape)
                    )
                    Text(
                        text = audit.auditType,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    )
                }

                if (showDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Record",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (audit.caName.isNotBlank()) audit.caName else "Manager's Daily Shift",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = audit.date,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (audit.meterNo.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isManagerReport) Icons.Default.TrendingDown else Icons.Default.Pin,
                            contentDescription = null,
                            tint = if (isManagerReport) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isManagerReport) "Final Difference: ₹${formatDouble(audit.manualDifference)}" else "Nozzle No: ${audit.meterNo}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isManagerReport) FontWeight.Bold else FontWeight.Normal),
                            color = if (isManagerReport) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (isManagerReport) {
                    Text(
                        text = if (audit.isSettled) "Status: SETTLED" else "Status: CARRIED FORWARD",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = if (audit.isSettled) Color(0xFF15803D) else Color(0xFF1E3A8A)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = onView,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("View", fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Edit", fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = onPrint,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Print", fontSize = 10.sp)
                }

                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Share", fontSize = 10.sp)
                }
            }
        }
    }
}

private fun printSavedReportHtml(context: Context, audit: SavedAudit) {
    val webView = android.webkit.WebView(context)
    val htmlContent = if (audit.htmlContent.trim().startsWith("<!DOCTYPE html>") || audit.htmlContent.trim().startsWith("<html")) {
        audit.htmlContent
    } else {
        """
        <html>
        <head>
            <style>
                @page {
                    size: portrait;
                    margin: 4mm;
                }
                body {
                    font-family: 'Courier New', Courier, monospace;
                    padding: 20px;
                    margin: 0;
                    font-size: 11px;
                    line-height: 1.25;
                    color: #000;
                    background-color: #fff;
                }
                pre {
                    white-space: pre-wrap;
                    word-wrap: break-word;
                    margin: 0;
                    padding: 0;
                }
                h2 {
                    text-align: center;
                    margin: 0 0 6px 0;
                    border-bottom: 1.5px solid #000;
                    padding-bottom: 3px;
                    font-size: 13px;
                }
                @media print {
                    html, body {
                        width: 100%;
                        height: 99%;
                        overflow: visible;
                    }
                    body {
                        font-size: 10px !important;
                        line-height: 1.1 !important;
                        padding: 0 !important;
                    }
                    h2 {
                        font-size: 11px !important;
                        margin-bottom: 4px !important;
                    }
                }
            </style>
        </head>
        <body>
            <div>
                <h2>${audit.auditType.uppercase()} RECONCILIATION</h2>
                <pre>${audit.summaryText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</pre>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    webView.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
            if (printManager != null) {
                val jobName = "Saved_Audit_${audit.date}_${System.currentTimeMillis()}"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print("Audit Report", printAdapter, null)
            } else {
                Toast.makeText(context, "Printing not supported on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }
    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
}
