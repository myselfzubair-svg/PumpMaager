package com.example

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.TtReceiptEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtReceiptEntryScreen(
    onBack: () -> Unit,
    adminPhone: String = "",
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Date state
    val calendar = Calendar.getInstance()
    var selectedDate by rememberSaveable {
        mutableStateOf(
            String.format(
                "%02d-%02d-%04d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR)
            )
        )
    }

    // Input States
    var invoiceNumber by remember { mutableStateOf("") }
    var ttNumber by remember { mutableStateOf("") }
    var invoiceAmount by remember { mutableStateOf("") }

    // MS (Petrol) inputs
    var msInvoiceAmount by remember { mutableStateOf("") }
    var msInvoiceDensity by remember { mutableStateOf("") }
    var msActualFuelDensity by remember { mutableStateOf("") }
    var msPreDecantationStock by remember { mutableStateOf("") }
    var msPostDecantationStock by remember { mutableStateOf("") }
    var msShortage by remember { mutableStateOf("") }

    // HSD (Diesel) inputs
    var hsdInvoiceAmount by remember { mutableStateOf("") }
    var hsdInvoiceDensity by remember { mutableStateOf("") }
    var hsdActualFuelDensity by remember { mutableStateOf("") }
    var hsdPreDecantationStock by remember { mutableStateOf("") }
    var hsdPostDecantationStock by remember { mutableStateOf("") }
    var hsdShortage by remember { mutableStateOf("") }



    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = LanguageManager.translate("TT Receipt Entry", "टीटी रसीद प्रविष्टि"),
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
                        modifier = Modifier.testTag("tt_receipt_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                    // Date Selector Card
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
                                    .clickable {
                                        try {
                                            val parts = selectedDate.split("-")
                                            if (parts.size == 3) {
                                                calendar.set(Calendar.DAY_OF_MONTH, parts[0].toInt())
                                                calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                                                calendar.set(Calendar.YEAR, parts[2].toInt())
                                            }
                                        } catch (e: Exception) {}
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, dayOfMonth ->
                                                selectedDate = String.format("%02d-%02d-%04d", dayOfMonth, month + 1, year)
                                            },
                                            calendar.get(Calendar.YEAR),
                                            calendar.get(Calendar.MONTH),
                                            calendar.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "Select Date",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column {
                                        Text(
                                            text = LanguageManager.translate("Receipt Date", "रसीद की तारीख"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = selectedDate,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = LanguageManager.translate("Change", "बदलें"),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Invoice Details Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Receipt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = LanguageManager.translate("Invoice Information", "इनवॉइस जानकारी"),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                OutlinedTextField(
                                    value = invoiceNumber,
                                    onValueChange = { invoiceNumber = it },
                                    label = { Text(LanguageManager.translate("Invoice Number", "इनवॉइस नंबर")) },
                                    modifier = Modifier.fillMaxWidth().testTag("invoice_number_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = ttNumber,
                                    onValueChange = { ttNumber = it },
                                    label = { Text(LanguageManager.translate("TT Number", "टीटी (TT) नंबर")) },
                                    modifier = Modifier.fillMaxWidth().testTag("tt_number_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    // MS Petrol Details Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalGasStation,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = LanguageManager.translate("MS Petrol Specifications", "एमएस पेट्रोल विनिर्देश"),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                OutlinedTextField(
                                    value = msInvoiceAmount,
                                    onValueChange = { 
                                        msInvoiceAmount = it 
                                        val msAmt = it.toDoubleOrNull() ?: 0.0
                                        val hsdAmt = hsdInvoiceAmount.toDoubleOrNull() ?: 0.0
                                        if (msAmt > 0.0 || hsdAmt > 0.0) {
                                            invoiceAmount = String.format(Locale.US, "%.2f", msAmt + hsdAmt)
                                        }
                                    },
                                    label = { Text(LanguageManager.translate("MS Invoice Amount (₹)", "एमएस इनवॉइस राशि (₹)")) },
                                    modifier = Modifier.fillMaxWidth().testTag("ms_invoice_amount_input"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = msInvoiceDensity,
                                        onValueChange = { msInvoiceDensity = it },
                                        label = { Text(LanguageManager.translate("Invoice Density", "इनवॉइस डेंसिटी")) },
                                        modifier = Modifier.weight(1f).testTag("ms_invoice_density"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    OutlinedTextField(
                                        value = msActualFuelDensity,
                                        onValueChange = { msActualFuelDensity = it },
                                        label = { Text(LanguageManager.translate("Actual Density", "वास्तविक डेंसिटी")) },
                                        modifier = Modifier.weight(1f).testTag("ms_actual_density"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = msPreDecantationStock,
                                        onValueChange = { msPreDecantationStock = it },
                                        label = { Text(LanguageManager.translate("Pre Decant Stock (L)", "प्री डीकैंट स्टॉक (L)")) },
                                        modifier = Modifier.weight(1f).testTag("ms_pre_stock"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    OutlinedTextField(
                                        value = msPostDecantationStock,
                                        onValueChange = { msPostDecantationStock = it },
                                        label = { Text(LanguageManager.translate("Post Decant Stock (L)", "पोस्ट डीकैंट स्टॉक (L)")) },
                                        modifier = Modifier.weight(1f).testTag("ms_post_stock"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = msShortage,
                                    onValueChange = { msShortage = it },
                                    label = { Text(LanguageManager.translate("Shortage (Litres)", "शॉर्टेज (लीटर)")) },
                                    modifier = Modifier.fillMaxWidth().testTag("ms_shortage"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    placeholder = { Text("0.0") }
                                )
                            }
                        }
                    }

                    // HSD Diesel Details Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalGasStation,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = LanguageManager.translate("HSD Diesel Specifications", "एचएसडी डीजल विनिर्देश"),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                OutlinedTextField(
                                    value = hsdInvoiceAmount,
                                    onValueChange = { 
                                        hsdInvoiceAmount = it 
                                        val msAmt = msInvoiceAmount.toDoubleOrNull() ?: 0.0
                                        val hsdAmt = it.toDoubleOrNull() ?: 0.0
                                        if (msAmt > 0.0 || hsdAmt > 0.0) {
                                            invoiceAmount = String.format(Locale.US, "%.2f", msAmt + hsdAmt)
                                        }
                                    },
                                    label = { Text(LanguageManager.translate("HSD Invoice Amount (₹)", "एचएसडी इनवॉइस राशि (₹)")) },
                                    modifier = Modifier.fillMaxWidth().testTag("hsd_invoice_amount_input"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = hsdInvoiceDensity,
                                        onValueChange = { hsdInvoiceDensity = it },
                                        label = { Text(LanguageManager.translate("Invoice Density", "इनवॉइस डेंसिटी")) },
                                        modifier = Modifier.weight(1f).testTag("hsd_invoice_density"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    OutlinedTextField(
                                        value = hsdActualFuelDensity,
                                        onValueChange = { hsdActualFuelDensity = it },
                                        label = { Text(LanguageManager.translate("Actual Density", "वास्तविक डेंसिटी")) },
                                        modifier = Modifier.weight(1f).testTag("hsd_actual_density"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = hsdPreDecantationStock,
                                        onValueChange = { hsdPreDecantationStock = it },
                                        label = { Text(LanguageManager.translate("Pre Decant Stock (L)", "प्री डीकैंट स्टॉक (L)")) },
                                        modifier = Modifier.weight(1f).testTag("hsd_pre_stock"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    OutlinedTextField(
                                        value = hsdPostDecantationStock,
                                        onValueChange = { hsdPostDecantationStock = it },
                                        label = { Text(LanguageManager.translate("Post Decant Stock (L)", "पोस्ट डीकैंट स्टॉक (L)")) },
                                        modifier = Modifier.weight(1f).testTag("hsd_post_stock"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = hsdShortage,
                                    onValueChange = { hsdShortage = it },
                                    label = { Text(LanguageManager.translate("Shortage (Litres)", "शॉर्टेज (लीटर)")) },
                                    modifier = Modifier.fillMaxWidth().testTag("hsd_shortage"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    placeholder = { Text("0.0") }
                                )
                            }
                        }
                    }

                    // Save Button
                    item {
                        Button(
                            onClick = {
                                if (invoiceNumber.isBlank()) {
                                    Toast.makeText(context, "Please enter an invoice number", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val amount = invoiceAmount.toDoubleOrNull() ?: 0.0
                                val msInvAmt = msInvoiceAmount.toDoubleOrNull() ?: 0.0
                                val hsdInvAmt = hsdInvoiceAmount.toDoubleOrNull() ?: 0.0
                                val msInvD = msInvoiceDensity.toDoubleOrNull() ?: 0.0
                                val msActD = msActualFuelDensity.toDoubleOrNull() ?: 0.0
                                val msPreS = msPreDecantationStock.toDoubleOrNull() ?: 0.0
                                val msPostS = msPostDecantationStock.toDoubleOrNull() ?: 0.0
                                val msS = msShortage.toDoubleOrNull() ?: 0.0

                                val hsdInvD = hsdInvoiceDensity.toDoubleOrNull() ?: 0.0
                                val hsdActD = hsdActualFuelDensity.toDoubleOrNull() ?: 0.0
                                val hsdPreS = hsdPreDecantationStock.toDoubleOrNull() ?: 0.0
                                val hsdPostS = hsdPostDecantationStock.toDoubleOrNull() ?: 0.0
                                val hsdS = hsdShortage.toDoubleOrNull() ?: 0.0

                                val newEntry = TtReceiptEntry(
                                    ownerAdminPhone = adminPhone,
                                    date = selectedDate,
                                    invoiceNumber = invoiceNumber.trim(),
                                    ttNumber = ttNumber.trim(),
                                    invoiceAmount = amount,
                                    msInvoiceAmount = msInvAmt,
                                    hsdInvoiceAmount = hsdInvAmt,
                                    msInvoiceDensity = msInvD,
                                    hsdInvoiceDensity = hsdInvD,
                                    msActualFuelDensity = msActD,
                                    hsdActualFuelDensity = hsdActD,
                                    msPreDecantationStock = msPreS,
                                    hsdPreDecantationStock = hsdPreS,
                                    msPostDecantationStock = msPostS,
                                    hsdPostDecantationStock = hsdPostS,
                                    msShortage = msS,
                                    hsdShortage = hsdS
                                )

                                coroutineScope.launch(Dispatchers.IO) {
                                    com.example.database.SupabaseRepository.saveTtEntry(newEntry)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "TT Receipt saved successfully!", Toast.LENGTH_SHORT).show()
                                        // Reset fields
                                        invoiceNumber = ""
                                        ttNumber = ""
                                        invoiceAmount = ""
                                        msInvoiceAmount = ""
                                        msInvoiceDensity = ""
                                        msActualFuelDensity = ""
                                        msPreDecantationStock = ""
                                        msPostDecantationStock = ""
                                        msShortage = ""
                                        hsdInvoiceAmount = ""
                                        hsdInvoiceDensity = ""
                                        hsdActualFuelDensity = ""
                                        hsdPreDecantationStock = ""
                                        hsdPostDecantationStock = ""
                                        hsdShortage = ""
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("save_tt_receipt_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = LanguageManager.translate("SAVE TT RECEIPT ENTRY", "टीटी रसीद प्रविष्टि सहेजें"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
        }
    }
