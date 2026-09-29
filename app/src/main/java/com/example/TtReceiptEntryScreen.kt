package com.example

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.TtReceiptEntry
import com.example.database.SupabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

data class ProductTtState(
    val name: String,
    var amount: String = "",
    var quantity: String = "",
    var invoiceDensity: String = "",
    var actualDensity: String = "",
    var preStock: String = "",
    var postStock: String = ""
)

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
                Locale.US,
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
    var totalInvoiceAmount by remember { mutableStateOf(0.0) }

    // Dynamic Products from Config
    val productStates = remember { mutableStateListOf<ProductTtState>() }
    var isLoadingProducts by remember { mutableStateOf(true) }

    LaunchedEffect(adminPhone) {
        if (adminPhone.isNotBlank()) {
            coroutineScope.launch {
                val pumpInfo = SupabaseRepository.getPumpInfo(adminPhone)
                val configured = pumpInfo?.productNames?.split(",")?.filter { it.isNotBlank() }?.map { it.trim() } ?: listOf("MS", "HSD")
                withContext(Dispatchers.Main) {
                    productStates.clear()
                    configured.forEach { name ->
                        productStates.add(ProductTtState(name))
                    }
                    isLoadingProducts = false
                }
            }
        }
    }

    // Derived total invoice amount
    LaunchedEffect(productStates.map { it.amount }) {
        totalInvoiceAmount = productStates.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    }

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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tt_receipt_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onLogout, modifier = Modifier.testTag("logout_button")) {
                        Icon(imageVector = Icons.Default.Logout, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (isLoadingProducts) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Date and General Info
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                DatePickerDialog(context, { _, y, m, d ->
                                    selectedDate = String.format(Locale.US, "%02d-%02d-%04d", d, m + 1, y)
                                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                            }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Column {
                                    Text("Receipt Date", style = MaterialTheme.typography.labelMedium)
                                    Text(selectedDate, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text("Change", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // 2. Invoice Details
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Receipt, null, tint = MaterialTheme.colorScheme.primary)
                                Text("Invoice Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            VoiceOutlinedTextField(value = invoiceNumber, onValueChange = { invoiceNumber = it }, label = { Text("Invoice Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            VoiceOutlinedTextField(value = ttNumber, onValueChange = { ttNumber = it }, label = { Text("Tanker (TT) Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Text("Total Invoice Amount: ₹${formatDouble(totalInvoiceAmount)}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black), color = Color(0xFF2E7D32))
                        }
                    }
                }

                // 3. Dynamic Product Cards
                items(productStates) { state ->
                    val index = productStates.indexOf(state)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(state.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary))
                            
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                VoiceOutlinedTextField(value = state.amount, onValueChange = { productStates[index] = state.copy(amount = it) }, label = { Text("Amt (₹)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                VoiceOutlinedTextField(value = state.quantity, onValueChange = { productStates[index] = state.copy(quantity = it) }, label = { Text("Qty (L)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                VoiceOutlinedTextField(value = state.invoiceDensity, onValueChange = { productStates[index] = state.copy(invoiceDensity = it) }, label = { Text("Inv Density") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                VoiceOutlinedTextField(value = state.actualDensity, onValueChange = { productStates[index] = state.copy(actualDensity = it) }, label = { Text("Act Density") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                            
                            val invD = state.invoiceDensity.toDoubleOrNull() ?: 0.0
                            val actD = state.actualDensity.toDoubleOrNull() ?: 0.0
                            val dDiff = actD - invD
                            Text("Density Difference: ${if (dDiff >= 0) "+" else ""}${formatDouble(dDiff)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (Math.abs(dDiff) > 0.5) Color.Red else Color.Gray)

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                VoiceOutlinedTextField(value = state.preStock, onValueChange = { productStates[index] = state.copy(preStock = it) }, label = { Text("Pre-Decant (L)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                VoiceOutlinedTextField(value = state.postStock, onValueChange = { productStates[index] = state.copy(postStock = it) }, label = { Text("Post-Decant (L)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }

                            val pre = state.preStock.toDoubleOrNull() ?: 0.0
                            val post = state.postStock.toDoubleOrNull() ?: 0.0
                            val qty = state.quantity.toDoubleOrNull() ?: 0.0
                            val shortage = (qty + pre) - post
                            Text("Calculated Shortage: ${formatDouble(shortage)} L", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = if (shortage > 0) Color.Red else Color(0xFF2E7D32))
                        }
                    }
                }

                // 4. Save Button
                item {
                    Button(
                        onClick = {
                            if (invoiceNumber.isBlank()) {
                                Toast.makeText(context, "Enter Invoice Number", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            coroutineScope.launch {
                                // Check uniqueness
                                val exists = SupabaseRepository.checkInvoiceExists(adminPhone, invoiceNumber)
                                if (exists) {
                                    withContext(Dispatchers.Main) { Toast.makeText(context, "Invoice Number already exists!", Toast.LENGTH_LONG).show() }
                                    return@launch
                                }

                                try {
                                    // Map dynamic states to the fixed TtReceiptEntry fields
                                    val msState = productStates.getOrNull(0)
                                    val hsdState = productStates.getOrNull(1)
                                    val extraState = productStates.getOrNull(2)

                                    fun calcShortage(s: ProductTtState?): Double {
                                        val q = s?.quantity?.toDoubleOrNull() ?: 0.0
                                        val pre = s?.preStock?.toDoubleOrNull() ?: 0.0
                                        val post = s?.postStock?.toDoubleOrNull() ?: 0.0
                                        if (q == 0.0 && pre == 0.0 && post == 0.0) return 0.0
                                        return (q + pre) - post
                                    }

                                    val entry = TtReceiptEntry(
                                        ownerAdminPhone = adminPhone,
                                        date = selectedDate,
                                        invoiceNumber = invoiceNumber.trim(),
                                        ttNumber = ttNumber.trim(),
                                        invoiceAmount = totalInvoiceAmount,
                                        
                                        // MS Mapping
                                        msInvoiceAmount = msState?.amount?.toDoubleOrNull() ?: 0.0,
                                        msInvoiceQuantity = msState?.quantity?.toDoubleOrNull() ?: 0.0,
                                        msInvoiceDensity = msState?.invoiceDensity?.toDoubleOrNull() ?: 0.0,
                                        msActualFuelDensity = msState?.actualDensity?.toDoubleOrNull() ?: 0.0,
                                        msPreDecantationStock = msState?.preStock?.toDoubleOrNull() ?: 0.0,
                                        msPostDecantationStock = msState?.postStock?.toDoubleOrNull() ?: 0.0,
                                        msShortage = calcShortage(msState),
                                        msDensityDiff = (msState?.actualDensity?.toDoubleOrNull() ?: 0.0) - (msState?.invoiceDensity?.toDoubleOrNull() ?: 0.0),

                                        // HSD Mapping
                                        hsdInvoiceAmount = hsdState?.amount?.toDoubleOrNull() ?: 0.0,
                                        hsdInvoiceQuantity = hsdState?.quantity?.toDoubleOrNull() ?: 0.0,
                                        hsdInvoiceDensity = hsdState?.invoiceDensity?.toDoubleOrNull() ?: 0.0,
                                        hsdActualFuelDensity = hsdState?.actualDensity?.toDoubleOrNull() ?: 0.0,
                                        hsdPreDecantationStock = hsdState?.preStock?.toDoubleOrNull() ?: 0.0,
                                        hsdPostDecantationStock = hsdState?.postStock?.toDoubleOrNull() ?: 0.0,
                                        hsdShortage = calcShortage(hsdState),
                                        hsdDensityDiff = (hsdState?.actualDensity?.toDoubleOrNull() ?: 0.0) - (hsdState?.invoiceDensity?.toDoubleOrNull() ?: 0.0),

                                        // Extra Mapping
                                        extraProductName = extraState?.name,
                                        extraInvoiceAmount = extraState?.amount?.toDoubleOrNull() ?: 0.0,
                                        extraInvoiceQuantity = extraState?.quantity?.toDoubleOrNull() ?: 0.0,
                                        extraInvoiceDensity = extraState?.invoiceDensity?.toDoubleOrNull() ?: 0.0,
                                        extraActualDensity = extraState?.actualDensity?.toDoubleOrNull() ?: 0.0,
                                        extraPreDecantationStock = extraState?.preStock?.toDoubleOrNull() ?: 0.0,
                                        extraPostDecantationStock = extraState?.postStock?.toDoubleOrNull() ?: 0.0,
                                        extraShortage = calcShortage(extraState),
                                        extraDensityDiff = (extraState?.actualDensity?.toDoubleOrNull() ?: 0.0) - (extraState?.invoiceDensity?.toDoubleOrNull() ?: 0.0)
                                    )

                                    SupabaseRepository.saveTtEntry(entry)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "TT Receipt saved successfully!", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { Toast.makeText(context, "Save Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("SAVE TT RECEIPT", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
