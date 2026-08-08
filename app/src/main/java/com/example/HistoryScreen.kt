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
                    if (audits.isNotEmpty()) {
                        IconButton(
                            onClick = { showDeleteConfirmAll = true },
                            modifier = Modifier.testTag("history_clear_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All History",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
                            "Complete a reconciliation or sales sheet, then tap the 'Save' button to persist it locally.",
                            "एक मिलान या बिक्री शीट पूरी करें, फिर इसे स्थानीय रूप से सुरक्षित करने के लिए 'सेव' बटन पर टैप करें।"
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Date selection bar
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                    ) {
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
                                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (selectedFilterDate != null) {
                                        LanguageManager.translate("Selected Date: $selectedFilterDate", "चुनी गई तारीख: $selectedFilterDate")
                                    } else {
                                        LanguageManager.translate("Select Date to Filter Reports", "रिपोर्ट्स फ़िल्टर करने के लिए तारीख चुनें")
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = if (selectedFilterDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (selectedFilterDate != null) {
                                TextButton(
                                    onClick = { selectedFilterDate = null },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear Filter",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = LanguageManager.translate("Clear", "साफ करें"),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }

                    val filteredAudits = remember(audits, selectedFilterDate) {
                        if (selectedFilterDate == null) {
                            audits
                        } else {
                            audits.filter { it.date == selectedFilterDate }
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
                                text = LanguageManager.translate("No Reports for This Date", "इस तारीख के लिए कोई रिपोर्ट नहीं"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = LanguageManager.translate(
                                    "Try choosing a different date or clear the filter to view all records.",
                                    "अलग तारीख चुनने का प्रयास करें या सभी रिकॉर्ड देखने के लिए फ़िल्टर साफ करें।"
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredAudits, key = { it.timestamp }) { audit ->
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
                                    onDelete = { auditToDelete = audit }
                                )
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
                    if (audit.meterNo.isNotEmpty()) {
                        Text(
                            text = "Meter: ${audit.meterNo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(12.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = audit.summaryText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
            title = { Text("Delete Saved Report?") },
            text = {
                Text(
                    "Are you sure you want to permanently delete the audit record for ${audit.caName} dated ${audit.date}?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAudit(adminPhone, audit.date, audit.caName, audit.timestamp)
                        auditToDelete = null
                        Toast.makeText(context, "Report deleted successfully", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { auditToDelete = null }) {
                    Text("Cancel")
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
                    OutlinedTextField(
                        value = editedCaName,
                        onValueChange = { editedCaName = it },
                        label = { Text(LanguageManager.translate("CA Name / Employee", "सीए नाम / कर्मचारी")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editedMeterNo,
                        onValueChange = { editedMeterNo = it },
                        label = { Text(LanguageManager.translate("Meter Number", "मीटर नंबर")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
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
    modifier: Modifier = Modifier
) {
    val badgeColor = when {
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
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                            imageVector = Icons.Default.Pin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Meter No: ${audit.meterNo}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
