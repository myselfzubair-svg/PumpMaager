package com.example

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.database.TtReceiptEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

data class TtPurchaseLineItem(
    val date: String,
    val productName: String,
    val invoiceNumber: String,
    val ttNumber: String,
    val quantityLitres: Double,
    val shortageLitres: Double,
    val invoiceAmount: Double,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TtEntryReportScreen(
    onBack: () -> Unit,
    adminPhone: String = "",
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Selected Month & Year
    val calendar = Calendar.getInstance()
    var selectedMonth by rememberSaveable { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var selectedYear by rememberSaveable { mutableStateOf(calendar.get(Calendar.YEAR)) }

    // State for loading entries & products
    var allEntries by remember { mutableStateOf(emptyList<TtReceiptEntry>()) }
    var stationProducts by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedProductIndex by rememberSaveable { mutableStateOf(0) }
    var reportType by rememberSaveable { mutableStateOf("PRODUCT_WISE") } // "PRODUCT_WISE" or "TANKER_WISE"

    val resetAndBack = {
        val now = Calendar.getInstance()
        selectedMonth = now.get(Calendar.MONTH)
        selectedYear = now.get(Calendar.YEAR)
        onBack()
    }

    BackHandler {
        resetAndBack()
    }

    LaunchedEffect(adminPhone) {
        if (adminPhone.isNotBlank()) {
            coroutineScope.launch(Dispatchers.IO) {
                val list = com.example.database.SupabaseRepository.getTtEntries(adminPhone)
                val pumpInfo = com.example.database.SupabaseRepository.getPumpInfo(adminPhone)
                val configured = pumpInfo?.productNames?.split(",")?.filter { it.isNotBlank() }?.map { it.trim() } ?: emptyList()
                val products = if (configured.isEmpty()) listOf("MS", "HSD") else configured

                withContext(Dispatchers.Main) {
                    allEntries = list
                    stationProducts = products
                }
            }
        } else {
            stationProducts = listOf("MS", "HSD")
        }
    }

    val activeProduct = if (stationProducts.isNotEmpty() && selectedProductIndex in stationProducts.indices) {
        stationProducts[selectedProductIndex]
    } else "MS"

    // Month lists for Hindi and English translations
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

    // Filtered entries for the selected month and year
    val filteredEntries = remember(allEntries, selectedMonth, selectedYear) {
        val monthStr = String.format(Locale.US, "%02d", selectedMonth + 1)
        val yearStr = selectedYear.toString()
        val suffix = "-$monthStr-$yearStr"
        allEntries.filter { it.date.endsWith(suffix) }
    }

    // Line items for the selected product
    val activeProductPurchaseItems = remember(filteredEntries, activeProduct) {
        val list = mutableListOf<TtPurchaseLineItem>()
        filteredEntries.forEach { entry ->
            val isMs = isSameProduct(activeProduct, "MS") || isSameProduct(activeProduct, "Petrol")
            val isHsd = isSameProduct(activeProduct, "HSD") || isSameProduct(activeProduct, "Diesel")

            if (isMs) {
                val qty = if (entry.msInvoiceQuantity > 0) entry.msInvoiceQuantity 
                         else if (entry.msPostDecantationStock > 0) (entry.msPostDecantationStock - entry.msPreDecantationStock).coerceAtLeast(0.0)
                         else 0.0
                
                val msAmt = if (entry.msInvoiceAmount > 0) entry.msInvoiceAmount else if (qty > 0) entry.invoiceAmount else 0.0

                if (qty > 0.0 || msAmt > 0.0 || entry.invoiceNumber.isNotBlank()) {
                    list.add(
                        TtPurchaseLineItem(
                            date = entry.date,
                            productName = activeProduct,
                            invoiceNumber = entry.invoiceNumber,
                            ttNumber = entry.ttNumber,
                            quantityLitres = qty,
                            shortageLitres = entry.msShortage,
                            invoiceAmount = msAmt,
                            timestamp = entry.timestamp
                        )
                    )
                }
            } else if (isHsd) {
                val qty = if (entry.hsdInvoiceQuantity > 0) entry.hsdInvoiceQuantity
                         else if (entry.hsdPostDecantationStock > 0) (entry.hsdPostDecantationStock - entry.hsdPreDecantationStock).coerceAtLeast(0.0)
                         else 0.0
                
                val hsdAmt = if (entry.hsdInvoiceAmount > 0) entry.hsdInvoiceAmount else if (qty > 0) entry.invoiceAmount else 0.0

                if (qty > 0.0 || hsdAmt > 0.0 || entry.invoiceNumber.isNotBlank()) {
                    list.add(
                        TtPurchaseLineItem(
                            date = entry.date,
                            productName = activeProduct,
                            invoiceNumber = entry.invoiceNumber,
                            ttNumber = entry.ttNumber,
                            quantityLitres = qty,
                            shortageLitres = entry.hsdShortage,
                            invoiceAmount = hsdAmt,
                            timestamp = entry.timestamp
                        )
                    )
                }
            } else if (entry.extraProductName != null && isSameProduct(activeProduct, entry.extraProductName)) {
                if (entry.extraInvoiceQuantity > 0.0 || entry.extraInvoiceAmount > 0.0 || entry.invoiceNumber.isNotBlank()) {
                    list.add(
                        TtPurchaseLineItem(
                            date = entry.date,
                            productName = activeProduct,
                            invoiceNumber = entry.invoiceNumber,
                            ttNumber = entry.ttNumber,
                            quantityLitres = entry.extraInvoiceQuantity,
                            shortageLitres = entry.extraShortage,
                            invoiceAmount = entry.extraInvoiceAmount,
                            timestamp = entry.timestamp
                        )
                    )
                }
            } else {
                // Fallback for items with only total amount
                if (entry.invoiceAmount > 0.0 && entry.msInvoiceAmount == 0.0 && entry.hsdInvoiceAmount == 0.0) {
                    list.add(
                        TtPurchaseLineItem(
                            date = entry.date,
                            productName = activeProduct,
                            invoiceNumber = entry.invoiceNumber,
                            ttNumber = entry.ttNumber,
                            quantityLitres = 0.0,
                            shortageLitres = 0.0,
                            invoiceAmount = entry.invoiceAmount,
                            timestamp = entry.timestamp
                        )
                    )
                }
            }
        }

        list.sortedWith { o1, o2 ->
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

    val totalProductQuantity = remember(activeProductPurchaseItems) {
        activeProductPurchaseItems.sumOf { it.quantityLitres }
    }
    val totalProductInvoiceAmount = remember(activeProductPurchaseItems) {
        activeProductPurchaseItems.sumOf { it.invoiceAmount }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Monthly Purchase Report – $activeProduct",
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
                        onClick = resetAndBack,
                        modifier = Modifier.testTag("tt_report_back_button")
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
                            if (reportType == "PRODUCT_WISE") {
                                printMonthlyPurchaseReportPdf(
                                    context = context,
                                    productName = activeProduct,
                                    monthName = monthNamesEn[selectedMonth],
                                    year = selectedYear,
                                    items = activeProductPurchaseItems,
                                    totalQuantity = totalProductQuantity,
                                    totalAmount = totalProductInvoiceAmount,
                                    isShare = false
                                )
                            } else {
                                printTankerPurchaseReportPdf(
                                    context = context,
                                    monthName = monthNamesEn[selectedMonth],
                                    year = selectedYear,
                                    entries = filteredEntries,
                                    isShare = false
                                )
                            }
                        },
                        modifier = Modifier.testTag("print_purchase_report")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print Purchase Report",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = {
                            if (reportType == "PRODUCT_WISE") {
                                printMonthlyPurchaseReportPdf(
                                    context = context,
                                    productName = activeProduct,
                                    monthName = monthNamesEn[selectedMonth],
                                    year = selectedYear,
                                    items = activeProductPurchaseItems,
                                    totalQuantity = totalProductQuantity,
                                    totalAmount = totalProductInvoiceAmount,
                                    isShare = true
                                )
                            } else {
                                printTankerPurchaseReportPdf(
                                    context = context,
                                    monthName = monthNamesEn[selectedMonth],
                                    year = selectedYear,
                                    entries = filteredEntries,
                                    isShare = true
                                )
                            }
                        },
                        modifier = Modifier.testTag("share_purchase_report")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Purchase Report",
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
            // Month & Year Selector Navigation
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
                            modifier = Modifier.testTag("tt_report_prev_month")
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
                            modifier = Modifier.testTag("tt_report_next_month")
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

            // Report Type Selection
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { reportType = "PRODUCT_WISE" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (reportType == "PRODUCT_WISE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (reportType == "PRODUCT_WISE") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Product-Wise", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { reportType = "TANKER_WISE" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (reportType == "TANKER_WISE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (reportType == "TANKER_WISE") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Tanker-Wise", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (reportType == "PRODUCT_WISE") {
                // Product Selection Tabs
                if (stationProducts.size > 1) {
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            stationProducts.forEachIndexed { index, prod ->
                                FilterChip(
                                    selected = index == selectedProductIndex,
                                    onClick = { selectedProductIndex = index },
                                    label = { Text(prod, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                // Summary Dashboard Card for Selected Product
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Purchase Summary – $activeProduct ($currentMonthName $selectedYear)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Product Quantity",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%,.1f L", totalProductQuantity),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Invoice Amount",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    text = String.format(Locale.US, "₹%,.2f", totalProductInvoiceAmount),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }

                // Daily Summary Table Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily TT Purchase Log ($activeProduct)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Text(
                            text = "${activeProductPurchaseItems.size} ${LanguageManager.translate("Records", "रिकॉर्ड")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Purchase Table / List
                if (activeProductPurchaseItems.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Receipt,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Text(
                                    text = "No TT purchase records found for $activeProduct in $currentMonthName $selectedYear.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // Table Header
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                          ) {
                                Text("Date", modifier = Modifier.weight(0.9f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                Text("Qty", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                Text("Short", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                                Text("Invoice No.", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                                Text("Amt", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                            }
                        }
                    }

                    // Table Rows
                    items(activeProductPurchaseItems) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.date,
                                    modifier = Modifier.weight(0.9f),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = if (item.quantityLitres > 0) "${String.format(Locale.US, "%,.1f", item.quantityLitres)} L" else "--",
                                    modifier = Modifier.weight(0.8f),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.End
                                )

                                Text(
                                    text = if (item.shortageLitres != 0.0) "${formatDouble(item.shortageLitres)}" else "--",
                                    modifier = Modifier.weight(0.7f),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (item.shortageLitres > 0) Color.Red else Color(0xFF2E7D32),
                                    textAlign = TextAlign.End
                                )

                                Text(
                                    text = item.invoiceNumber.ifEmpty { "--" },
                                    modifier = Modifier.weight(1.2f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = if (item.invoiceAmount > 0) "₹${String.format(Locale.US, "%,.2f", item.invoiceAmount)}" else "--",
                                    modifier = Modifier.weight(1.2f),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF2E7D32),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }

                    // Monthly Totals Table Footer
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F5E9),
                            border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TOTALS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black), color = Color(0xFF2E7D32))
                                Text("${String.format(Locale.US, "%,.1f", totalProductQuantity)} L", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End)
                                Text("--", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                                Text("₹${String.format(Locale.US, "%,.2f", totalProductInvoiceAmount)}", modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black), color = Color(0xFF2E7D32), textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            } else {
                // TANKER_WISE REPORT
                item {
                    Text(
                        text = "Consolidated Tanker Purchase Report ($currentMonthName $selectedYear)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (filteredEntries.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No tanker records found.", color = Color.Gray)
                        }
                    }
                } else {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Invoice / TT", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                Text("Product Breakdown (MS | HSD | Other)", modifier = Modifier.weight(2.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                                Text("Total / Amt", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.End)
                            }
                        }
                    }

                    items(filteredEntries.sortedBy { it.timestamp }) { entry ->
                        val msQty = if (entry.msInvoiceQuantity > 0) entry.msInvoiceQuantity else (entry.msPostDecantationStock - entry.msPreDecantationStock).coerceAtLeast(0.0)
                        val hsdQty = if (entry.hsdInvoiceQuantity > 0) entry.hsdInvoiceQuantity else (entry.hsdPostDecantationStock - entry.hsdPreDecantationStock).coerceAtLeast(0.0)
                        val totalQty = msQty + hsdQty + entry.extraInvoiceQuantity

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1.5f)) {
                                        Text(entry.invoiceNumber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("TT: ${entry.ttNumber}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    
                                    Row(modifier = Modifier.weight(2.5f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        Text("${formatDouble(msQty)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(" | ", color = Color.LightGray)
                                        Text("${formatDouble(hsdQty)}", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        if (entry.extraInvoiceQuantity > 0) {
                                            Text(" | ", color = Color.LightGray)
                                            Text("${formatDouble(entry.extraInvoiceQuantity)}", color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.End) {
                                        Text("${formatDouble(totalQty)} L", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                        Text("₹${formatDouble(entry.invoiceAmount)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 12.sp)
                                    }
                                }
                                Text(entry.date, fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun printMonthlyPurchaseReportPdf(
    context: Context,
    productName: String,
    monthName: String,
    year: Int,
    items: List<TtPurchaseLineItem>,
    totalQuantity: Double,
    totalAmount: Double,
    isShare: Boolean = false
) {
    val webView = android.webkit.WebView(context)
    val htmlBuilder = java.lang.StringBuilder()

    val rowsHtml = if (items.isEmpty()) {
        "<tr><td colspan='4' style='text-align: center; color: #888; padding: 12px;'>No purchase records for $productName during $monthName $year</td></tr>"
    } else {
        items.joinToString("") { item ->
            val qtyStr = if (item.quantityLitres > 0) "${String.format(Locale.getDefault(), "%,.1f", item.quantityLitres)} L" else "--"
            val amtStr = if (item.invoiceAmount > 0) "₹${String.format(Locale.getDefault(), "%,.2f", item.invoiceAmount)}" else "--"
            """
            <tr>
                <td style="padding: 8px; border-bottom: 1px solid #eee; font-weight: bold;">${item.date}</td>
                <td style="padding: 8px; border-bottom: 1px solid #eee; text-align: right; font-weight: bold; color: #1a5f7a;">$qtyStr</td>
                <td style="padding: 8px; border-bottom: 1px solid #eee; text-align: center;">${item.invoiceNumber.ifEmpty { "--" }}</td>
                <td style="padding: 8px; border-bottom: 1px solid #eee; text-align: right; font-weight: bold; color: #2e7d32;">$amtStr</td>
            </tr>
            """.trimIndent()
        }
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
                    font-size: 14px;
                    color: #1a5f7a;
                    font-weight: bold;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-top: 15px;
                    margin-bottom: 20px;
                }
                th {
                    background-color: #f5f5f5;
                    padding: 9px;
                    text-align: left;
                    font-weight: bold;
                    border-bottom: 2px solid #ddd;
                }
                td {
                    padding: 8px;
                }
                .total-row {
                    font-weight: bold;
                    background-color: #e8f5e9;
                    border-top: 2px solid #2e7d32;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>D R INAMDAR PETROLEUM</h1>
                <h2>Monthly Purchase Report &mdash; $productName</h2>
                <div style="margin-top: 6px; font-weight: bold; font-size: 12px; color: #1a237e;">
                    Period: $monthName $year
                </div>
            </div>

            <table>
                <thead>
                    <tr>
                        <th style="width: 20%;">Date</th>
                        <th style="width: 25%; text-align: right;">Product Quantity</th>
                        <th style="width: 25%; text-align: center;">Invoice Number</th>
                        <th style="width: 30%; text-align: right;">Invoice Amount</th>
                    </tr>
                </thead>
                <tbody>
                    $rowsHtml
                    <tr class="total-row">
                        <td style="padding: 10px;">MONTHLY TOTALS</td>
                        <td style="padding: 10px; text-align: right; color: #1a5f7a; font-size: 12px;">${String.format(Locale.getDefault(), "%,.1f", totalQuantity)} L</td>
                        <td style="padding: 10px; text-align: center;">&mdash;</td>
                        <td style="padding: 10px; text-align: right; color: #2e7d32; font-size: 12px;">₹${String.format(Locale.getDefault(), "%,.2f", totalAmount)}</td>
                    </tr>
                </tbody>
            </table>
            
            <div style="margin-top: 30px; text-align: right; font-size: 10px; color: #777;">
                Report Generated on: ${java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())}<br/>
                Powered by GenovaCare™ Innovations
            </div>
        </body>
        </html>
    """.trimIndent())

    val htmlContent = htmlBuilder.toString()
    webView.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
            val jobName = "Monthly_Purchase_Report_${productName.replace(" ", "_")}_${monthName}_$year"
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
                                    putExtra(Intent.EXTRA_SUBJECT, "Monthly Purchase Report - $productName ($monthName $year)")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Monthly Purchase Report"))
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
                    printManager.print(jobName, printAdapter, null)
                } else {
                    android.widget.Toast.makeText(context, "Printing not supported on this device", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
}

private fun printTankerPurchaseReportPdf(
    context: Context,
    monthName: String,
    year: Int,
    entries: List<TtReceiptEntry>,
    isShare: Boolean = false
) {
    val webView = android.webkit.WebView(context)
    val htmlBuilder = java.lang.StringBuilder()

    val rowsHtml = if (entries.isEmpty()) {
        "<tr><td colspan='8' style='text-align: center; color: #888; padding: 12px;'>No purchase records for $monthName $year</td></tr>"
    } else {
        entries.sortedBy { it.timestamp }.joinToString("") { entry ->
            val msQty = if (entry.msInvoiceQuantity > 0) entry.msInvoiceQuantity else (entry.msPostDecantationStock - entry.msPreDecantationStock).coerceAtLeast(0.0)
            val hsdQty = if (entry.hsdInvoiceQuantity > 0) entry.hsdInvoiceQuantity else (entry.hsdPostDecantationStock - entry.hsdPreDecantationStock).coerceAtLeast(0.0)
            val totalQty = msQty + hsdQty + entry.extraInvoiceQuantity
            
            """
            <tr>
                <td style="padding: 6px; border-bottom: 1px solid #eee;">${entry.date}</td>
                <td style="padding: 6px; border-bottom: 1px solid #eee; font-weight: bold;">${entry.invoiceNumber}</td>
                <td style="padding: 6px; border-bottom: 1px solid #eee;">${entry.ttNumber}</td>
                <td style="padding: 6px; border-bottom: 1px solid #eee; text-align: right; color: #1a237e;">${formatDouble(msQty)}</td>
                <td style="padding: 6px; border-bottom: 1px solid #eee; text-align: right; color: #004d40;">${formatDouble(hsdQty)}</td>
                <td style="padding: 6px; border-bottom: 1px solid #eee; text-align: right; color: #4a148c;">${formatDouble(entry.extraInvoiceQuantity)}</td>
                <td style="padding: 6px; border-bottom: 1px solid #eee; text-align: right; font-weight: bold;">${formatDouble(totalQty)} L</td>
                <td style="padding: 6px; border-bottom: 1px solid #eee; text-align: right; font-weight: bold; color: #2e7d32;">₹${formatDouble(entry.invoiceAmount)}</td>
            </tr>
            """.trimIndent()
        }
    }

    val totalMs = entries.sumOf { if (it.msInvoiceQuantity > 0) it.msInvoiceQuantity else (it.msPostDecantationStock - it.msPreDecantationStock).coerceAtLeast(0.0) }
    val totalHsd = entries.sumOf { if (it.hsdInvoiceQuantity > 0) it.hsdInvoiceQuantity else (it.hsdPostDecantationStock - it.hsdPreDecantationStock).coerceAtLeast(0.0) }
    val totalExtra = entries.sumOf { it.extraInvoiceQuantity }
    val totalMonthQty = totalMs + totalHsd + totalExtra
    val totalMonthAmt = entries.sumOf { it.invoiceAmount }

    htmlBuilder.append("""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                @page { size: landscape; margin: 6mm; }
                body { font-family: Arial, sans-serif; margin: 0; color: #333; font-size: 10px; line-height: 1.3; }
                .header { text-align: center; border-bottom: 2px solid #1a237e; padding-bottom: 8px; margin-bottom: 15px; }
                .header h1 { margin: 0 0 4px 0; font-size: 16px; color: #1a237e; }
                .header h2 { margin: 0; font-size: 13px; color: #1a5f7a; font-weight: bold; }
                table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                th { background-color: #f0f4f8; padding: 8px 6px; text-align: left; font-weight: bold; border-bottom: 2px solid #1a237e; color: #1a237e; }
                td { padding: 6px; border-bottom: 1px solid #eee; }
                .total-row { font-weight: bold; background-color: #f8f9fa; border-top: 2px solid #1a237e; }
                .num { text-align: right; font-family: 'Courier New', monospace; }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>D R INAMDAR PETROLEUM</h1>
                <h2>Consolidated Tanker Invoice Purchase Report</h2>
                <div style="margin-top: 5px; font-weight: bold; color: #555;">Period: $monthName $year</div>
            </div>

            <table>
                <thead>
                    <tr>
                        <th style="width: 10%;">Date</th>
                        <th style="width: 15%;">Invoice No.</th>
                        <th style="width: 12%;">Tanker No.</th>
                        <th style="width: 10%; text-align: right;">MS Qty</th>
                        <th style="width: 10%; text-align: right;">HSD Qty</th>
                        <th style="width: 10%; text-align: right;">Extra Qty</th>
                        <th style="width: 15%; text-align: right;">Total Quantity</th>
                        <th style="width: 18%; text-align: right;">Invoice Amount</th>
                    </tr>
                </thead>
                <tbody>
                    $rowsHtml
                    <tr class="total-row">
                        <td colspan="3" style="padding: 10px;">TOTAL MONTHLY PURCHASE</td>
                        <td style="text-align: right;">${formatDouble(totalMs)}</td>
                        <td style="text-align: right;">${formatDouble(totalHsd)}</td>
                        <td style="text-align: right;">${formatDouble(totalExtra)}</td>
                        <td style="text-align: right; font-size: 11px;">${formatDouble(totalMonthQty)} L</td>
                        <td style="text-align: right; font-size: 11px; color: #2e7d32;">₹${formatDouble(totalMonthAmt)}</td>
                    </tr>
                </tbody>
            </table>
            
            <div style="margin-top: 20px; text-align: right; font-size: 9px; color: #888;">
                Report Generated: ${java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())}
            </div>
        </body>
        </html>
    """.trimIndent())

    webView.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
            val jobName = "Tanker_Purchase_Report_${monthName}_$year"
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
                                    putExtra(Intent.EXTRA_SUBJECT, "Tanker Purchase Report ($monthName $year)")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Tanker Purchase Report"))
                            } catch (e: Exception) {}
                        }
                        override fun onFailure(error: String?) {}
                    })
                } catch (e: Exception) {}
            } else {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
                if (printManager != null) {
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    printManager.print(jobName, printAdapter, null)
                }
            }
        }
    }
    webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "utf-8", null)
}
