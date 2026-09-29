package com.example

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.database.HistoryViewModel
import com.example.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class UdhariJamaItem(
    val name: String,
    val receiptNo: String,
    val product: String,
    val litres: Double,
    val rate: Double,
    val description: String,
    val amount: Double
)

data class UdharItem(
    val name: String,
    val receiptNo: String,
    val product: String,
    val litres: Double,
    val rate: Double,
    val description: String,
    val amount: Double
)

@Composable
fun FourNozzleDensityInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "0",
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.getOrNull(0) ?: ""
            if (spokenText.isNotEmpty()) {
                val processed = VoiceInputUtils.parseSpokenNumber(spokenText)
                if (processed.isNotEmpty()) {
                    onValueChange(processed)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .background(
                if (enabled) Color.White.copy(alpha = 0.9f) else Color.LightGray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                1.5.dp,
                if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(10.dp)
            )
            .padding(start = 6.dp, end = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { if (enabled) onValueChange(it) },
                    readOnly = !enabled,
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (enabled) Color.Black else Color.Gray,
                        textAlign = TextAlign.Start
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.Black.copy(alpha = 0.35f),
                                    textAlign = TextAlign.Start
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        innerTextField()
                    }
                )
            }
            
            if (enabled) {
                IconButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak number clearly...")
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (e: Exception) {
                            try {
                                val fallbackIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                speechLauncher.launch(fallbackIntent)
                            } catch (ex: Exception) {
                                Toast.makeText(context, "Voice input not available", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProductTestingSummaryCard(
    title: String,
    grossSale: Double,
    testingVal: String,
    onTestingChanged: (String) -> Unit,
    rateVal: String,
    onRateChanged: (String) -> Unit,
    netSale: Double,
    totalSalesAmount: Double,
    badgeColor: Color,
    badgeTextColor: Color,
    testingTag: String,
    rateTag: String,
    netSaleTag: String,
    totalSalesAmountTag: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = badgeColor.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.5.dp, badgeColor.copy(alpha = 0.4f))
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
                Box(
                    modifier = Modifier
                        .background(badgeColor, shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = title.uppercase() + " TOTAL SUMMARY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            color = badgeTextColor,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (title.contains("MS", ignoreCase = true)) "Gross Sale" else "Gross Sale",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatDouble(grossSale)} L",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Testing (L)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FourNozzleDensityInputField(
                        value = testingVal,
                        onValueChange = onTestingChanged,
                        placeholder = "0.0",
                        modifier = Modifier.testTag(testingTag)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rate per L (from DB) (₹)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FourNozzleDensityInputField(
                        value = rateVal,
                        onValueChange = onRateChanged,
                        placeholder = "Set in DB",
                        enabled = false,
                        modifier = Modifier.testTag(rateTag)
                    )
                }
            }

            HorizontalDivider(color = badgeColor.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL NET SALES L",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = formatDouble(netSale) + " L",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = badgeColor,
                        modifier = Modifier.testTag(netSaleTag)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "TOTAL SALES EXP AMT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            color = Color(0xFF222222),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Text(
                        text = "₹ " + formatDouble(totalSalesAmount),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF222222)
                        ),
                        modifier = Modifier.testTag(totalSalesAmountTag)
                    )
                }
            }
        }
    }
}

@Composable
fun FourNozzleCard(
    title: String,
    closingVal: String,
    openingVal: String,
    netSale: Double,
    onClosingChanged: (String) -> Unit,
    onOpeningChanged: (String) -> Unit,
    closingTag: String,
    openingTag: String,
    badgeColor: Color,
    badgeTextColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Product badge header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(badgeColor, shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            color = badgeTextColor,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Text(
                    text = "READINGS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Input Fields Row (Closing, Opening)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Closing Input
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Closing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    FourNozzleDensityInputField(
                        value = closingVal,
                        onValueChange = onClosingChanged,
                        placeholder = "Closing",
                        modifier = Modifier.testTag(closingTag)
                    )
                }

                Text(
                    text = "−",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp)
                )

                // Opening Input
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Opening",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    FourNozzleDensityInputField(
                        value = openingVal,
                        onValueChange = onOpeningChanged,
                        placeholder = "Opening",
                        enabled = false,
                        modifier = Modifier.testTag(openingTag)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NET SALE (LITRES)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Text(
                    text = formatDouble(netSale) + " L",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace),
                    color = badgeColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onBack: (() -> Unit)? = null,
    onSaveSuccess: (() -> Unit)? = null,
    date: String = "",
    caName: String = "",
    meterNo: String = "",
    selectedNozzles: List<com.example.database.RegisteredNozzle> = emptyList(),
    phone: String = "",
    adminPhone: String = "",
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val historyViewModel: HistoryViewModel = viewModel()

    // Dynamic Nozzle States
    val nozzleReadings = remember { 
        mutableStateListOf<com.example.database.NozzleReadingEntry>().apply {
            // Sort nozzles by number ascending for consistent order
            addAll(selectedNozzles.sortedBy { it.nozzleNumber.toIntOrNull() ?: 999 }.map { 
                com.example.database.NozzleReadingEntry(
                    nozzleId = it.nozzleId,
                    nozzleName = it.nozzleName,
                    nozzleNumber = it.nozzleNumber,
                    nozzleType = it.nozzleType,
                    tankName = it.tankName,
                    tankId = it.tankId
                )
            })
        }
    }

    var ttEntries by remember { mutableStateOf<List<com.example.database.TtReceiptEntry>>(emptyList()) }

    // Dynamic testing and rate states (Map: Product Name -> Value)
    val testingValues = remember { mutableStateMapOf<String, String>() }
    val productRates = remember { mutableStateMapOf<String, Double>() }
    val pumpProducts = remember { mutableStateListOf<String>() }

    var phonePeValue by rememberSaveable { mutableStateOf("") }
    var cardsValue by rememberSaveable { mutableStateOf("") }
    var cashSubmittedValue by rememberSaveable { mutableStateOf("") }
    var actualCashValue by rememberSaveable { mutableStateOf("") }
    
    // Custom Udhari Jama
    var udhariJamaRawString by rememberSaveable { mutableStateOf("") }
    var tempUdhariJamaName by rememberSaveable { mutableStateOf("") }
    var tempUdhariJamaReceiptNo by rememberSaveable { mutableStateOf("") }
    var tempUdhariJamaProduct by rememberSaveable { mutableStateOf("Cash") }
    var tempUdhariJamaLitres by rememberSaveable { mutableStateOf("") }
    var tempUdhariJamaRate by rememberSaveable { mutableStateOf("") }
    var tempUdhariJamaDesc by rememberSaveable { mutableStateOf("") }
    var tempUdhariJamaAmount by rememberSaveable { mutableStateOf("") }

    // Custom Expenses (Kharch)
    var kharchRawString by rememberSaveable { mutableStateOf("") }
    var tempKharchDesc by rememberSaveable { mutableStateOf("") }
    var tempKharchAmount by rememberSaveable { mutableStateOf("") }
    var kharchDropdownExpanded by remember { mutableStateOf(false) }
    val expenseOptions = listOf("Miscellaneous", "TankerEntry", "TeaWater", "ToiletClean", "Custom...")

    // Custom Credit (Udhar)
    var udharRawString by rememberSaveable { mutableStateOf("") }
    var tempUdharName by rememberSaveable { mutableStateOf("") }
    var tempUdharReceiptNo by rememberSaveable { mutableStateOf("") }
    var tempUdharProduct by rememberSaveable { mutableStateOf("") }
    var tempUdharLitres by rememberSaveable { mutableStateOf("") }
    var tempUdharRate by rememberSaveable { mutableStateOf("") }
    var tempUdharDesc by rememberSaveable { mutableStateOf("") }
    var tempUdharAmount by rememberSaveable { mutableStateOf("") }
    var showAddUdharNameDialog by remember { mutableStateOf(false) }
    var newUdharNameInput by remember { mutableStateOf("") }

    val context = LocalContext.current
    var udhariNamesList by remember { mutableStateOf(listOf("Miscellaneous")) }
    var showAddUdhariDialog by remember { mutableStateOf(false) }
    var newUdhariNameInput by remember { mutableStateOf("") }

    var showSaveConfirmDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    var jamaPartyBalance by remember { mutableStateOf(0.0) }
    var udharPartyBalance by remember { mutableStateOf(0.0) }
    
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(tempUdhariJamaName, adminPhone) {
        if (adminPhone.isNotBlank() && tempUdhariJamaName.isNotBlank() && tempUdhariJamaName != "Miscellaneous") {
            jamaPartyBalance = com.example.database.SupabaseRepository.getPartyBalance(adminPhone, tempUdhariJamaName)
        }
    }

    LaunchedEffect(tempUdharName, adminPhone) {
        if (adminPhone.isNotBlank() && tempUdharName.isNotBlank() && tempUdharName != "Miscellaneous") {
            udharPartyBalance = com.example.database.SupabaseRepository.getPartyBalance(adminPhone, tempUdharName)
        }
    }

    LaunchedEffect(date, adminPhone, phone) {
        if (adminPhone.isNotBlank()) {
            historyViewModel.setAdminPhone(adminPhone)
            
            // 1. Resolve Products (from setup info + actual selected nozzles)
            val pumpInfo = com.example.database.SupabaseRepository.getPumpInfo(adminPhone)
            val configuredProducts = pumpInfo?.productNames?.split(",")?.filter { it.isNotBlank() }?.map { it.trim() } ?: emptyList()
            val nozzleProducts = selectedNozzles.map { it.nozzleType }.distinct()
            val allProducts = (configuredProducts + nozzleProducts).distinct()
            
            val finalProducts = if (allProducts.isEmpty()) listOf("MS", "HSD") else allProducts
            pumpProducts.clear()
            pumpProducts.addAll(finalProducts)

            if (tempUdharProduct.isEmpty() && finalProducts.isNotEmpty()) {
                tempUdharProduct = finalProducts.first()
            }

            // 2. Load Daily Rates
            val dailyData = com.example.database.SupabaseRepository.getDailyPumpData(adminPhone, date)
            finalProducts.forEach { productName ->
                val pid = generateProductId(productName)
                val rateObj = dailyData.find { it.productId == pid } 
                    ?: dailyData.find { isSameProduct(it.productName, productName) }
                
                if (rateObj != null) {
                    productRates[productName] = rateObj.rate
                }
                if (!testingValues.containsKey(productName)) {
                    testingValues[productName] = ""
                }
            }
            
            // 2.5 Load TT Entries
            ttEntries = com.example.database.SupabaseRepository.getTtEntriesByDate(adminPhone, date)

            // 3. Load Opening Readings (Observe history changes to fill accurately)
            launch {
                combine(
                    historyViewModel.allGeneralNozzleReadings,
                    historyViewModel.allMsNozzleReadings,
                    historyViewModel.allHsdNozzleReadings
                ) { _, _, _ -> Unit }.collect {
                    nozzleReadings.forEachIndexed { index, nozzle ->
                        val rate = productRates.entries.find { isSameProduct(it.key, nozzle.nozzleType) }?.value ?: 0.0
                        val latest = historyViewModel.getLatestClosingReadingForNozzle(nozzle.nozzleName, adminPhone, date)
                        val openingValStr = if (latest % 1.0 == 0.0) latest.toLong().toString() else latest.toString()

                        if (nozzleReadings[index].openingReading.isEmpty()) {
                            nozzleReadings[index] = nozzleReadings[index].copy(rate = rate, openingReading = openingValStr)
                        } else {
                            nozzleReadings[index] = nozzleReadings[index].copy(rate = rate)
                        }
                    }
                }
            }
            
            val fetchedNames = com.example.database.SupabaseRepository.getUdhariNames(adminPhone)
            Log.d("CalculatorScreen", "Loaded ${fetchedNames.size} udhari names")
            udhariNamesList = fetchedNames
        }
    }

    // Parsed Udhari Jama list
    val udhariJamaList = remember(udhariJamaRawString) {
        if (udhariJamaRawString.isBlank()) {
            emptyList<UdhariJamaItem>()
        } else {
            udhariJamaRawString.split(";").mapNotNull { part ->
                val subParts = part.split("|")
                if (subParts.size == 7) {
                    val name = subParts[0]
                    val receiptNo = subParts[1]
                    val product = subParts[2]
                    val litres = subParts[3].toDoubleOrNull() ?: 0.0
                    val rate = subParts[4].toDoubleOrNull() ?: 0.0
                    val desc = subParts[5]
                    val amt = subParts[6].toDoubleOrNull() ?: 0.0
                    UdhariJamaItem(name, receiptNo, product, litres, rate, desc, amt)
                } else if (subParts.size == 6) {
                    val name = subParts[0]
                    val receiptNo = subParts[1]
                    val product = subParts[2]
                    val rate = subParts[3].toDoubleOrNull() ?: 0.0
                    val desc = subParts[4]
                    val amt = subParts[5].toDoubleOrNull() ?: 0.0
                    UdhariJamaItem(name, receiptNo, product, 0.0, rate, desc, amt)
                } else if (subParts.size == 5) {
                    val name = subParts[0]
                    val product = subParts[1]
                    val rate = subParts[2].toDoubleOrNull() ?: 0.0
                    val desc = subParts[3]
                    val amt = subParts[4].toDoubleOrNull() ?: 0.0
                    UdhariJamaItem(name, "", product, 0.0, rate, desc, amt)
                } else if (subParts.size == 2) {
                    val desc = subParts[0]
                    val amt = subParts[1].toDoubleOrNull() ?: 0.0
                    UdhariJamaItem(name = "Legacy", receiptNo = "", product = "N/A", litres = 0.0, rate = 0.0, description = desc, amount = amt)
                } else null
            }
        }
    }
    val totalUdhariJama = udhariJamaList.sumOf { it.amount }

    // Parsed expenses list
    val kharchList = remember(kharchRawString) {
        if (kharchRawString.isBlank()) {
            emptyList<Pair<String, Double>>()
        } else {
            kharchRawString.split(";").mapNotNull { part ->
                val subParts = part.split("|")
                if (subParts.size == 2) {
                    val desc = subParts[0]
                    val amt = subParts[1].toDoubleOrNull() ?: 0.0
                    if (desc.isNotEmpty() && amt > 0.0) {
                        desc to amt
                    } else null
                } else null
            }
        }
    }
    val totalKharch = kharchList.sumOf { it.second }

    // Parsed Udhar (Credit) list
    val udharList = remember(udharRawString) {
        if (udharRawString.isBlank()) {
            emptyList<UdharItem>()
        } else {
            udharRawString.split(";").mapNotNull { part ->
                val subParts = part.split("|")
                if (subParts.size == 7) {
                    val name = subParts[0]
                    val receiptNo = subParts[1]
                    val product = subParts[2]
                    val litres = subParts[3].toDoubleOrNull() ?: 0.0
                    val rate = subParts[4].toDoubleOrNull() ?: 0.0
                    val desc = subParts[5]
                    val amt = subParts[6].toDoubleOrNull() ?: 0.0
                    UdharItem(name, receiptNo, product, litres, rate, desc, amt)
                } else if (subParts.size == 6) {
                    val name = subParts[0]
                    val receiptNo = subParts[1]
                    val product = subParts[2]
                    val rate = subParts[3].toDoubleOrNull() ?: 0.0
                    val desc = subParts[4]
                    val amt = subParts[5].toDoubleOrNull() ?: 0.0
                    UdharItem(name, receiptNo, product, 0.0, rate, desc, amt)
                } else if (subParts.size == 5) {
                    val name = subParts[0]
                    val product = subParts[1]
                    val rate = subParts[2].toDoubleOrNull() ?: 0.0
                    val desc = subParts[3]
                    val amt = subParts[4].toDoubleOrNull() ?: 0.0
                    UdharItem(name, "", product, 0.0, rate, desc, amt)
                } else if (subParts.size == 2) {
                    val desc = subParts[0]
                    val amt = subParts[1].toDoubleOrNull() ?: 0.0
                    UdharItem(name = "Legacy", receiptNo = "", product = "N/A", litres = 0.0, rate = 0.0, description = desc, amount = amt)
                } else null
            }
        }
    }
    val totalUdhar = udharList.sumOf { it.amount }

    // Dynamic Calculations per Product
    val productSummaries = pumpProducts.associate { productName ->
        val nozzles = nozzleReadings.filter { isSameProduct(it.nozzleType, productName) }
        val grossSale = nozzles.sumOf { it.salesQuantity }
        val totalOpening = nozzles.sumOf { it.openingReading.toDoubleOrNull() ?: 0.0 }
        val totalClosing = nozzles.sumOf { it.closingReading.toDoubleOrNull() ?: 0.0 }
        
        val testing = testingValues[productName]?.toDoubleOrNull() ?: 0.0
        val netSale = (grossSale - testing).coerceAtLeast(0.0)
        
        // Find rate using productId generation for maximum consistency
        val pid = generateProductId(productName)
        val rate = productRates[productName] ?: 0.0
        
        val calculatedSalesAmount = grossSale * rate
        val reconciledSalesAmount = netSale * rate
        
        productName to object {
            val nozzles = nozzles
            val totalOpening = totalOpening
            val totalClosing = totalClosing
            val grossSale = grossSale
            val testing = testing
            val netSale = netSale
            val rate = rate
            val calculatedSalesAmount = calculatedSalesAmount
            val reconciledSalesAmount = reconciledSalesAmount
        }
    }

    val grandActualTotalLitres = nozzleReadings.sumOf { it.salesQuantity }
    val totalTestingLitres = productSummaries.values.sumOf { it.testing }
    val finalNetLitres = (grandActualTotalLitres - totalTestingLitres).coerceAtLeast(0.0)
    
    val totalFuelSalesAmount = productSummaries.values.sumOf { it.reconciledSalesAmount }
    val grandTotal = totalFuelSalesAmount + totalUdhariJama
    
    val phonePeAmount = phonePeValue.toDoubleOrNull() ?: 0.0
    val cardsAmount = cardsValue.toDoubleOrNull() ?: 0.0
    val cashSubmittedAmount = cashSubmittedValue.toDoubleOrNull() ?: 0.0
    val finalNetCash = grandTotal - phonePeAmount - cardsAmount - totalKharch - totalUdhar - cashSubmittedAmount

    // Total Physical Cash (Balance)
    val actualCashInHand = actualCashValue.toDoubleOrNull() ?: 0.0

    val missingRateProducts = nozzleReadings
        .groupBy { it.nozzleType }
        .filter { it.value.any { nozzle -> nozzle.rate <= 0.0 } }
        .keys
        .toList()

    val cashDiscrepancy = actualCashInHand - finalNetCash

    fun sanitizeInput(input: String): String {
        var seenDot = false
        return input.filter { char ->
            if (char == '.') {
                if (seenDot) {
                    false
                } else {
                    seenDot = true
                    true
                }
            } else {
                char.isDigit()
            }
        }
    }

    fun sanitizeInteger(input: String): String {
        return input.filter { char -> char.isDigit() }
    }

    fun clearAllInputs() {
        nozzleReadings.forEachIndexed { index, nozzle ->
            nozzleReadings[index] = nozzle.copy(closingReading = "")
        }
        testingValues.clear()
        phonePeValue = ""
        cardsValue = ""
        cashSubmittedValue = ""
        actualCashValue = ""
        udhariJamaRawString = ""
    }

    fun getSummaryText(): String {
        val sb = java.lang.StringBuilder()
        sb.append("--- D R INAMDAR PETROLEUM ---\n")
        sb.append("SHIFT REPORT & TALLY\n")
        sb.append("Date: ${date.ifEmpty { "N/A" }} | CA: ${caName.ifEmpty { "N/A" }}\n")
        sb.append("Meter No: ${meterNo.ifEmpty { "N/A" }}\n")
        sb.append("-----------------------------\n")
        
        sb.append("NOZZLE READINGS:\n")
        nozzleReadings.groupBy { it.nozzleType }.forEach { (product, nozzles) ->
            sb.append("\n$product:\n")
            nozzles.forEach { nozzle ->
                sb.append("  ${nozzle.nozzleName} (#${nozzle.nozzleNumber}):\n")
                sb.append("    Op: ${nozzle.openingReading} Cl: ${nozzle.closingReading}\n")
                sb.append("    Gross Sale: ${formatDouble(nozzle.salesQuantity)} L\n")
            }
        }
        
        sb.append("\nPRODUCT SUMMARIES:\n")
        productSummaries.forEach { (product, s) ->
            sb.append("\n$product:\n")
            sb.append("  Total Nozzles: ${s.nozzles.size}\n")
            sb.append("  Opening: ${formatDouble(s.totalOpening)} | Closing: ${formatDouble(s.totalClosing)}\n")
            sb.append("  Gross Sales Qty: ${formatDouble(s.grossSale)} L\n")
            sb.append("  Testing: -${formatDouble(s.testing)} L\n")
            sb.append("  Net Sales (Reconciled): ${formatDouble(s.netSale)} L\n")
            sb.append("  Rate per Liter: ₹${formatDouble(s.rate)}\n")
            sb.append("  Calculated Sales Amount: ₹${formatDouble(s.calculatedSalesAmount)}\n")
            sb.append("  Reconciled Sales Amount: ₹${formatDouble(s.reconciledSalesAmount)}\n")
        }
        
        sb.append("\nFINANCIAL SUMMARY:\n")
        sb.append("  Total Fuel Sales: ₹${formatDouble(totalFuelSalesAmount)}\n")
        sb.append("  Recoveries (Jama): +₹${formatDouble(totalUdhariJama)}\n")
        sb.append("  Grand Total: ₹${formatDouble(grandTotal)}\n")
        sb.append("-----------------------------\n")
        sb.append("  Expected Net Cash: ₹${formatDouble(finalNetCash)}\n")
        sb.append("  CASH BALANCE: ₹${formatDouble(actualCashInHand)}\n")
        sb.append("  Discrepancy (Farak): ₹${formatDouble(cashDiscrepancy)}\n")
        sb.append("-----------------------------\n")
        
        sb.append("-----------------------------\n")
        productSummaries.forEach { (product, s) ->
            sb.append("$product Reconciled Sales Value: ₹${formatDouble(s.reconciledSalesAmount)}\n")
        }
        sb.append("Total Fuel Sales: ₹${formatDouble(totalFuelSalesAmount)}\n")
        if (udhariJamaList.isNotEmpty()) {
            sb.append("Udhari Jama (Recoveries):\n")
            udhariJamaList.sortedBy { it.name.lowercase() }.forEach { item ->
                val details = if (item.product == "Cash") {
                    "Cash" + (if (item.receiptNo.isNotBlank()) " | Rec. No: ${item.receiptNo}" else "")
                } else {
                    "${item.product} | ${formatDouble(item.litres)} Ltrs @ ₹${formatDouble(item.rate)}" + (if (item.receiptNo.isNotBlank()) " | Rec. No: ${item.receiptNo}" else "")
                }
                sb.append("  + ${item.name} ($details) ${item.description}: +₹${formatDouble(item.amount)}\n")
            }
            sb.append("  Total Recoveries Added: +₹${formatDouble(totalUdhariJama)}\n")
        }
        sb.append("GRAND TOTAL: ₹${formatDouble(grandTotal)}\n")
        sb.append("PhonePe Deduction: -₹${formatDouble(phonePeAmount)}\n")
        if (cardsAmount > 0.0) {
            sb.append("Cards Deduction: -₹${formatDouble(cardsAmount)}\n")
        }
        if (cashSubmittedAmount > 0.0) {
            sb.append("Cash Submitted: -₹${formatDouble(cashSubmittedAmount)}\n")
        }
        if (kharchList.isNotEmpty()) {
            sb.append("Expenses (Kharch):\n")
            kharchList.sortedBy { it.first.lowercase() }.forEach { (desc, amt) ->
                sb.append("  - $desc: -₹${formatDouble(amt)}\n")
            }
            sb.append("  Total Expenses: -₹${formatDouble(totalKharch)}\n")
        }
        if (udharList.isNotEmpty()) {
            sb.append("Credit (Udhar):\n")
            udharList.sortedBy { it.name.lowercase() }.forEach { item ->
                val details = if (item.product == "Cash") {
                    "Cash" + (if (item.receiptNo.isNotBlank()) " | Rec. No. ${item.receiptNo}" else "")
                } else {
                    "${item.product} | ${formatDouble(item.litres)} Ltrs @ ₹${formatDouble(item.rate)}" + (if (item.receiptNo.isNotBlank()) " | Rec. No. ${item.receiptNo}" else "")
                }
                sb.append("  - ${item.name} ($details) ${item.description}: -₹${formatDouble(item.amount)}\n")
            }
            sb.append("  Total Udhar: -₹${formatDouble(totalUdhar)}\n")
        }
        sb.append("-----------------------------\n")
        sb.append("EXPECTED NET CASH BAL: ₹${formatDouble(finalNetCash)}\n")
        sb.append("CASH BALANCE: ₹${formatDouble(actualCashInHand)}\n")

        sb.append("-----------------------------\n")
        when {
            Math.abs(cashDiscrepancy) < 1.0 -> sb.append("TALLY RESULT: SUCCESS (Perfect matching)\n")
            cashDiscrepancy < 0.0 -> sb.append("TALLY RESULT: SHORTAGE -₹${formatDouble(-cashDiscrepancy)}\n")
            cashDiscrepancy > 0.0 -> sb.append("TALLY RESULT: EXTRA CASH +₹${formatDouble(cashDiscrepancy)}\n")
        }
        sb.append("-----------------------------\n")
        return sb.toString()
    }

    fun generateA4HtmlReport(): String {
        val formattedDate = date.ifEmpty { "Same Date" }
        val formattedCaName = caName.ifEmpty { "N/A" }
        val formattedMeterNo = meterNo.ifEmpty { "N/A" }

        val sharedPrefs = context.getSharedPreferences("pump_manager_prefs", android.content.Context.MODE_PRIVATE)
        
        val productParametersHtml = pumpProducts.map { product ->
            val r = productRates[product]?.let { "₹$it" } ?: "N/A"
            val density = sharedPrefs.getString("density_${product.lowercase().replace(" ", "_")}_$date", "") ?: "N/A"
            val stock = sharedPrefs.getString("stock_${product.lowercase().replace(" ", "_")}_$date", "") ?: "N/A"
            
            val isMs = isSameProduct(product, "MS") || isSameProduct(product, "Petrol")
            val isHsd = isSameProduct(product, "HSD") || isSameProduct(product, "Diesel")
            
            val dbReceipt = if (isMs) {
                ttEntries.sumOf { 
                    if (it.msInvoiceQuantity > 0.0) it.msInvoiceQuantity 
                    else (it.msPostDecantationStock - it.msPreDecantationStock).coerceAtLeast(0.0) 
                }
            } else if (isHsd) {
                ttEntries.sumOf { 
                    if (it.hsdInvoiceQuantity > 0.0) it.hsdInvoiceQuantity 
                    else (it.hsdPostDecantationStock - it.hsdPreDecantationStock).coerceAtLeast(0.0)
                }
            } else {
                ttEntries.sumOf { 
                    if (it.extraProductName != null && isSameProduct(it.extraProductName, product)) it.extraInvoiceQuantity
                    else 0.0
                }
            }
            
            val finalReceipt = if (dbReceipt > 0.0) dbReceipt.toString() 
                               else sharedPrefs.getString("receipt_${product.lowercase().replace(" ", "_")}_$date", "0.0") ?: "0.0"

            """
            <tr>
                <td><strong>$product</strong></td>
                <td class="num">$r</td>
                <td class="num">$density</td>
                <td class="num">${if (stock != "N/A" && stock != "") "$stock L" else "N/A"}</td>
                <td class="num">${if (finalReceipt != "0.0" && finalReceipt != "") "$finalReceipt L" else "0.0 L"}</td>
            </tr>
            """.trimIndent()
        }.joinToString("")

        val nozzleReadingsHtml = nozzleReadings.map { nozzle ->
            """
            <tr>
                <td>${nozzle.nozzleName} (#${nozzle.nozzleNumber})</td>
                <td>${formatDouble(nozzle.openingReading.toDoubleOrNull() ?: 0.0)}</td>
                <td>${formatDouble(nozzle.closingReading.toDoubleOrNull() ?: 0.0)}</td>
                <td class="bold">${formatDouble(nozzle.salesQuantity)} L</td>
                <td class="num">&mdash;</td>
            </tr>
            """.trimIndent()
        }.joinToString("")

        val productSummariesHtml = productSummaries.map { (product, s) ->
            """
            <tr>
                <td colspan="2"><strong>$product RECONCILIATION</strong></td>
            </tr>
            <tr>
                <td>$product Meter Gross Sum</td>
                <td class="num">${formatDouble(s.grossSale)} L</td>
            </tr>
            <tr>
                <td>$product Testing Deduction</td>
                <td class="num">-${formatDouble(s.testing)} L</td>
            </tr>
            <tr>
                <td>Net $product Sales (L)</td>
                <td class="num">${formatDouble(s.netSale)} L</td>
            </tr>
            <tr>
                <td>Rate per Liter</td>
                <td class="num">₹${formatDouble(s.rate)}/L</td>
            </tr>
            <tr class="highlight">
                <td>$product Reconciled Sales Amount</td>
                <td class="num"><strong>₹${formatDouble(s.reconciledSalesAmount)}</strong></td>
            </tr>
            <tr><td colspan="2" style="border:none; height:5px;"></td></tr>
            """.trimIndent()
        }.joinToString("")

        val tableUdhariJamaHtml = if (udhariJamaList.isEmpty()) {
            "<tr><td colspan='2' style='text-align: center; color: #a0aec0;'>No recoveries entered</td></tr>"
        } else {
            udhariJamaList.sortedBy { it.name.lowercase() }.joinToString("") { item ->
                val details = if (item.product == "Cash") {
                    "Cash" + (if (item.receiptNo.isNotBlank()) " | Rec. No. ${item.receiptNo}" else "")
                } else {
                    "${item.product} | ${formatDouble(item.litres)} Ltrs @ ₹${formatDouble(item.rate)}" + (if (item.receiptNo.isNotBlank()) " | Rec. No. ${item.receiptNo}" else "")
                }
                val descLine = if (item.description.isNotBlank()) "<br/><span style='font-size: 0.85em; color: #555;'>Desc: ${item.description}</span>" else ""
                "<tr><td>+ Rec: <b>${item.name}</b> ($details)$descLine</td><td class='num'>₹${formatDouble(item.amount)}</td></tr>"
            }
        }

        val tableKharchHtml = if (kharchList.isEmpty()) {
            "<tr><td colspan='2' style='text-align: center; color: #a0aec0;'>No expense entries recorded</td></tr>"
        } else {
            kharchList.sortedBy { it.first.lowercase() }.joinToString("") { (desc, amt) ->
                "<tr><td>- Exp: <b>$desc</b></td><td class='num'>-₹${formatDouble(amt)}</td></tr>"
            }
        }

        val tableUdharHtml = if (udharList.isEmpty()) {
            "<tr><td colspan='2' style='text-align: center; color: #a0aec0;'>No credit entries recorded</td></tr>"
        } else {
            udharList.sortedBy { it.name.lowercase() }.joinToString("") { item ->
                val details = if (item.product == "Cash") {
                    "Cash" + (if (item.receiptNo.isNotBlank()) " | Rec. No. ${item.receiptNo}" else "")
                } else {
                    "${item.product} | ${formatDouble(item.litres)} Ltrs @ ₹${formatDouble(item.rate)}" + (if (item.receiptNo.isNotBlank()) " | Rec. No. ${item.receiptNo}" else "")
                }
                val descLine = if (item.description.isNotBlank()) "<br/><span style='font-size: 0.85em; color: #555;'>Desc: ${item.description}</span>" else ""
                "<tr><td>- Crd: <b>${item.name}</b> ($details)$descLine</td><td class='num'>-₹${formatDouble(item.amount)}</td></tr>"
            }
        }



        val tallyClass = when {
            cashDiscrepancy == 0.0 -> "green-bg"
            cashDiscrepancy < 0.0 -> "red-bg"
            else -> "highlight"
        }

        val tallyText = when {
            cashDiscrepancy == 0.0 -> "Perfect matching (0.00)"
            cashDiscrepancy < 0.0 -> "SHORTAGE (-₹${formatDouble(-cashDiscrepancy)})"
            else -> "EXTRA CASH (+₹${formatDouble(cashDiscrepancy)})"
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Fuel Station Reconciliation Report</title>
                <style>
                    @page {
                        size: A4 portrait;
                        margin: 5mm 6mm 5mm 6mm;
                    }
                    body {
                        font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                        color: #2d3748;
                        margin: 0;
                        padding: 0;
                        font-size: 11px;
                        line-height: 1.3;
                        background-color: #fff;
                    }
                    .header-title-box {
                        text-align: center;
                        border-bottom: 2px solid #1a365d;
                        padding-bottom: 4px;
                        margin-bottom: 8px;
                    }
                    .header-title-box h1 {
                        font-size: 16px;
                        margin: 0;
                        color: #1a365d;
                        text-transform: uppercase;
                        letter-spacing: 0.8px;
                        font-weight: 800;
                    }
                    .header-sub {
                        font-size: 11px;
                        font-weight: bold;
                        color: #4a5568;
                        margin: 2px 0 0 0;
                    }
                    .grid-container {
                        display: table;
                        width: 100%;
                        table-layout: fixed;
                    }
                    .grid-col {
                        display: table-cell;
                        width: 50%;
                        vertical-align: top;
                    }
                    .pad-left {
                        padding-left: 6px;
                    }
                    .pad-right {
                        padding-right: 6px;
                    }
                    .card {
                        border: 1px solid #cbd5e0;
                        border-radius: 5px;
                        margin-bottom: 8px;
                        background-color: #f7fafc;
                        padding: 6px 8px;
                        page-break-inside: avoid;
                    }
                    .card-caption {
                        font-size: 10.5px;
                        font-weight: 800;
                        color: #2b6cb0;
                        border-bottom: 1.2px solid #2b6cb0;
                        margin-bottom: 4px;
                        padding-bottom: 2px;
                        text-transform: uppercase;
                        letter-spacing: 0.4px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 10px;
                    }
                    th {
                        background-color: #ebf8ff;
                        color: #2b6cb0;
                        font-weight: bold;
                        border-bottom: 1px solid #cbd5e0;
                        text-align: left;
                        padding: 3px 4px;
                        text-transform: uppercase;
                        font-size: 9.5px;
                    }
                    td {
                        border-bottom: 1px dashed #e2e8f0;
                        padding: 3px 4px;
                        color: #2d3748;
                    }
                    .num {
                        text-align: right;
                        font-family: monospace;
                        font-weight: bold;
                        white-space: nowrap;
                    }
                    .bold {
                        font-weight: bold;
                    }
                    .highlight {
                        background-color: #edf2f7;
                        font-weight: bold;
                    }
                    .bold-total {
                        font-weight: bold;
                        background-color: #e2e8f0;
                        border-top: 1px solid #cbd5e0;
                    }
                    .green-bg {
                        background-color: #c6f6d5 !important;
                        color: #22543d;
                        font-weight: bold;
                    }
                    .red-bg {
                        background-color: #fed7d7 !important;
                        color: #742a2a;
                        font-weight: bold;
                    }
                    .footer {
                        text-align: center;
                        font-size: 7.5px;
                        color: #718096;
                        margin-top: 8px;
                        border-top: 1px dashed #cbd5e0;
                        padding-top: 4px;
                    }
                </style>
            </head>
            <body>
                <div class="header-title-box">
                    <h1>D. R. INAMDAR PETROLEUM</h1>
                    <p class="header-sub">Shift Report & Tally &nbsp;|&nbsp; Date: $formattedDate</p>
                </div>

                <div class="grid-container">
                    <!-- LEFT COLUMN -->
                    <div class="grid-col pad-right">
                        <!-- METADATA CARD -->
                        <div class="card">
                            <div class="card-caption">GENERAL AUDIT INFORMATION</div>
                            <table>
                                <tbody>
                                    <tr>
                                        <td>Date of Report</td>
                                        <td class="num">$formattedDate</td>
                                    </tr>
                                    <tr>
                                        <td>Cashier (CA) Name</td>
                                        <td class="num">$formattedCaName</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>

                        <!-- DAILY SALES PARAMETERS & INVENTORY -->
                        <div class="card">
                            <div class="card-caption">DAILY SALES PARAMETERS & INVENTORY</div>
                            <table>
                                <thead>
                                    <tr>
                                        <th>Fuel Type</th>
                                        <th style="text-align:right;">Rate</th>
                                        <th style="text-align:right;">Density</th>
                                        <th style="text-align:right;">Opening Stock</th>
                                        <th style="text-align:right;">Receipts (New Inventory)</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    $productParametersHtml
                                </tbody>
                            </table>
                        </div>

                        <!-- FUEL NOZZLE READINGS -->
                        <div class="card">
                            <div class="card-caption">FUEL NOZZLE METER SALES DETAIL</div>
                            <table>
                                <thead>
                                    <tr>
                                        <th>Nozzle ID</th>
                                        <th>Opening</th>
                                        <th>Closing</th>
                                        <th>Sale (L)</th>
                                        <th style="text-align:right;">Value</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    $nozzleReadingsHtml
                                </tbody>
                            </table>
                        </div>

                        <!-- METER RECONCILIATION SUMMARY WITH TESTING SUBTRACTION -->
                        <div class="card">
                            <div class="card-caption">FUEL RECONCILIATION & TESTING SUMMARY</div>
                            <table>
                                <tbody>
                                    $productSummariesHtml
                                    <tr class="bold-total">
                                        <td>COMBINED STATION FUEL VOLUME (NET)</td>
                                        <td class="num">${formatDouble(finalNetLitres)} L</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>

                        <!-- CASH TALLY SUMMARY -->
                        <div class="card">
                            <div class="card-caption">FINAL CASH TALLY SUMMARY</div>
                            <table>
                                <tbody>
                                    <tr>
                                        <td>Total Revenue Expected</td>
                                        <td class="num">₹${formatDouble(grandTotal)}</td>
                                    </tr>
                                    <tr>
                                        <td>Cash Submitted</td>
                                        <td class="num">-₹${formatDouble(cashSubmittedAmount)}</td>
                                    </tr>
                                    <tr class="bold-total">
                                        <td>TOTAL CASH BALANCE</td>
                                        <td class="num">₹${formatDouble(actualCashInHand)}</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <!-- RIGHT COLUMN -->
                    <div class="grid-col pad-left">
                        <!-- CASH FLOW RECONCILIATION STATEMENT -->
                        <div class="card">
                            <div class="card-caption">CASH FLOW RECONCILIATION STATEMENT</div>
                            <table>
                                <tbody>
                                    ${pumpProducts.map { product ->
                                        """
                                        <tr>
                                            <td>&nbsp;&bull;&nbsp; $product Sales (Reconciled)</td>
                                            <td class="num">₹${formatDouble(productSummaries[product]?.reconciledSalesAmount ?: 0.0)}</td>
                                        </tr>
                                        """.trimIndent()
                                    }.joinToString("")}
                                    <tr style="font-weight: bold; background-color: #fcfcfc;">
                                        <td>Base Reconciled Fuel Sales Value</td>
                                        <td class="num">₹${formatDouble(totalFuelSalesAmount)}</td>
                                    </tr>
                                    $tableUdhariJamaHtml
                                    <tr class="highlight">
                                        <td class="bold">GRAND TOTAL EXPECTED REVENUE</td>
                                        <td class="num">₹${formatDouble(grandTotal)}</td>
                                    </tr>
                                    <tr>
                                        <td>Total accounted PhonePe/Cards/Expenses/Udhar</td>
                                        <td class="num">-₹${formatDouble(phonePeAmount + cardsAmount + totalKharch + totalUdhar)}</td>
                                    </tr>
                                    <tr class="bold-total">
                                        <td>REMAINING NET CASH TARGET</td>
                                        <td class="num">₹${formatDouble(finalNetCash + cashSubmittedAmount)}</td>
                                    </tr>
                                    <tr class="$tallyClass">
                                        <td class="bold">TALLY STATUS / DISCREPANCY</td>
                                        <td class="num">$tallyText</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>

                <div class="footer">
                    Report compiled & generated via Petrol Pump Sales Auditor &nbsp;|&nbsp; Thank you for your business.
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MS & HSD Sales Auditor",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.5.sp
                            ),
                            modifier = Modifier.testTag("four_nozzle_header_title")
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("four_nozzle_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to mode selection page",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            clearAllInputs()
                            Toast.makeText(context, "All Fields Reset", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .testTag("four_nozzle_clear_all_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Clear all inputs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Audit Profile", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Account profile icon",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clearAllInputs()
                            Toast.makeText(context, "Fields Reset", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("four_nozzle_reset_action_bar_button"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (missingRateProducts.isNotEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Cannot save: Fuel rate missing for ${missingRateProducts.joinToString(", ")}",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                            
                            val missingClosings = nozzleReadings.filter { it.closingReading.trim().isEmpty() }
                            if (missingClosings.isNotEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Please enter closing reading for: ${missingClosings.joinToString { it.nozzleName }}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }

                            // Validation: Closing >= Opening
                            val invalidReadings = nozzleReadings.filter {
                                val open = it.openingReading.toDoubleOrNull() ?: 0.0
                                val close = it.closingReading.toDoubleOrNull() ?: 0.0
                                close < open
                            }
                            if (invalidReadings.isNotEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Closing reading cannot be less than opening for: ${invalidReadings.joinToString { it.nozzleName }}",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                            
                            showSaveConfirmDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("four_nozzle_save_audit_button"),
                        enabled = missingRateProducts.isEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 12.sp)
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            Text(
                text = "Operation: Shift Report & Tally (MS & HSD)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            // Header Session Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("four_nozzle_header_info_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Date
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DATE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = date.ifEmpty { "N/A" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(30.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // CA Name
                    Column(modifier = Modifier.weight(2.5f)) {
                        Text(
                            text = "CA NAME",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = caName.ifEmpty { "N/A" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Black
                            )
                        )
                    }
                }
            }

            if (missingRateProducts.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Fuel rate is not configured for ${missingRateProducts.joinToString(", ")} for the selected date.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }
                }
            }

            pumpProducts.forEach { product ->
                val nozzles = nozzleReadings.filter { isSameProduct(it.nozzleType, product) }
                if (nozzles.isNotEmpty()) {
                    val isMs = isSameProduct(product, "MS")
                    val isHsd = isSameProduct(product, "HSD")
                    
                    val badgeColor = when {
                        isMs -> MaterialTheme.colorScheme.primary
                        isHsd -> MaterialTheme.colorScheme.tertiary
                        else -> Color(0xFF8B5CF6) // Premium/Other
                    }
                    val badgeTextColor = Color.White
                    
                    Text(
                        text = "$product Nozzles (${nozzles.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    nozzles.forEach { nozzle ->
                        val actualIndex = nozzleReadings.indexOfFirst { it.nozzleId == nozzle.nozzleId }
                        FourNozzleCard(
                            title = "${nozzle.nozzleName} (#${nozzle.nozzleNumber})",
                            closingVal = nozzle.closingReading,
                            openingVal = nozzle.openingReading,
                            netSale = nozzle.salesQuantity,
                            onClosingChanged = { newVal ->
                                nozzleReadings[actualIndex] = nozzleReadings[actualIndex].copy(closingReading = sanitizeInput(newVal))
                            },
                            onOpeningChanged = { newVal ->
                                nozzleReadings[actualIndex] = nozzleReadings[actualIndex].copy(openingReading = sanitizeInput(newVal))
                            },
                            closingTag = "noz_${nozzle.nozzleId}_closing",
                            openingTag = "noz_${nozzle.nozzleId}_opening",
                            badgeColor = badgeColor,
                            badgeTextColor = badgeTextColor
                        )
                    }

                    // Summary for this product (Testing/Rate)
                    val summary = productSummaries[product]!!
                    
                    ProductTestingSummaryCard(
                        title = product,
                        grossSale = summary.grossSale,
                        testingVal = testingValues[product] ?: "",
                        onTestingChanged = { testingValues[product] = sanitizeInput(it) },
                        rateVal = if (summary.rate <= 0.0) "Not Configured" else summary.rate.toString(),
                        onRateChanged = { /* Auto-retrieved */ },
                        netSale = summary.netSale,
                        totalSalesAmount = summary.reconciledSalesAmount,
                        badgeColor = badgeColor,
                        badgeTextColor = badgeTextColor,
                        testingTag = "${product}_testing",
                        rateTag = "${product}_rate",
                        netSaleTag = "${product}_net",
                        totalSalesAmountTag = "${product}_total"
                    )
                }
            }

            // Dynamic Udhari Jama Card below Nozzles
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                        Text(
                            text = "UDHARI JAMA (CREDIT RECOVERY)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF222222),
                                letterSpacing = 0.5.sp
                            )
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFAFAFA), shape = RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ADD CREDITS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF222222)
                                )
                            )
                        }
                    }

                    Text(
                        text = "Any recovered outstanding credit (Udhari Jama) here will be automatically added to the Sales Grand Total.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var udhariDropdownExpanded by remember { mutableStateOf(false) }

                        if (showAddUdhariDialog) {
                            AlertDialog(
                                onDismissRequest = { showAddUdhariDialog = false },
                                title = { Text("Add New Udhari Name") },
                                text = {
                                    VoiceOutlinedTextField(
                                        value = newUdhariNameInput,
                                        onValueChange = { newUdhariNameInput = it },
                                        label = { Text("Udhari Name") },
                                        singleLine = true,
                                        modifier = Modifier.testTag("add_udhari_name_dialog_input")
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val name = newUdhariNameInput.trim()
                                            if (name.isNotEmpty()) {
                                                coroutineScope.launch {
                                                    com.example.database.SupabaseRepository.saveUdhariNames(adminPhone, listOf(name))
                                                    // Immediately refresh local list
                                                    udhariNamesList = com.example.database.SupabaseRepository.getUdhariNames(adminPhone)
                                                }
                                                tempUdhariJamaName = name
                                                newUdhariNameInput = ""
                                                showAddUdhariDialog = false
                                            }
                                        },
                                        modifier = Modifier.testTag("add_udhari_name_dialog_confirm")
                                    ) {
                                        Text("Add")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showAddUdhariDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            VoiceOutlinedTextField(
                                value = tempUdhariJamaName.ifEmpty { "Select Udhari" },
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Select Udhari", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("four_nozzle_udhari_jama_name_input"),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = {
                                    IconButton(onClick = { 
                                        udhariDropdownExpanded = true 
                                        coroutineScope.launch {
                                            udhariNamesList = com.example.database.SupabaseRepository.getUdhariNames(adminPhone)
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Udhari Name"
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF222222),
                                    unfocusedBorderColor = Color(0xFF222222).copy(alpha = 0.3f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { 
                                        udhariDropdownExpanded = true 
                                        coroutineScope.launch {
                                            udhariNamesList = com.example.database.SupabaseRepository.getUdhariNames(adminPhone)
                                        }
                                    }
                            )

                            DropdownMenu(
                                expanded = udhariDropdownExpanded,
                                onDismissRequest = { udhariDropdownExpanded = false }
                            ) {
                                udhariNamesList.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            tempUdhariJamaName = name
                                            udhariDropdownExpanded = false
                                        }
                                    )
                                }
                                
                                HorizontalDivider()
                                
                                DropdownMenuItem(
                                    text = { Text("+ Add New Udhari...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        udhariDropdownExpanded = false
                                        showAddUdhariDialog = true
                                    }
                                )
                            }
                        }
                    }

                    if (tempUdhariJamaName.isNotBlank() && tempUdhariJamaName != "Miscellaneous") {
                        val sessionJama = udhariJamaList.filter { it.name == tempUdhariJamaName }.sumOf { it.amount }
                        val sessionUdhar = udharList.filter { it.name == tempUdhariJamaName }.sumOf { it.amount }
                        val netBalance = jamaPartyBalance + sessionUdhar - sessionJama
                        
                        Text(
                            text = "Outstanding Balance: ₹ ${formatDouble(netBalance)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (netBalance > 0) Color(0xFFB91C1C) else Color(0xFF15803D)
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VoiceOutlinedTextField(
                            value = tempUdhariJamaDesc,
                            onValueChange = { tempUdhariJamaDesc = it },
                            placeholder = { Text("Description", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("four_nozzle_udhari_jama_desc_input"),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF222222),
                                unfocusedBorderColor = Color(0xFF222222).copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VoiceOutlinedTextField(
                            value = tempUdhariJamaAmount,
                            onValueChange = { tempUdhariJamaAmount = sanitizeInput(it) },
                            placeholder = { Text("₹ Amount", fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("four_nozzle_udhari_jama_amt_input"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF222222),
                                unfocusedBorderColor = Color(0xFF222222).copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        IconButton(
                            onClick = {
                                val cleanedName = tempUdhariJamaName.trim().replace("|", "").replace(";", "")
                                val cleanedReceiptNo = ""
                                val cleanedProduct = "Cash"
                                val cleanedLitres = 0.0
                                val cleanedRate = 0.0
                                val cleanedDesc = tempUdhariJamaDesc.trim().replace("|", "").replace(";", "")
                                val cleanedAmount = tempUdhariJamaAmount.toDoubleOrNull() ?: 0.0

                                if (cleanedName.isEmpty()) {
                                    Toast.makeText(context, "Enter Udhari Name", Toast.LENGTH_SHORT).show()
                                } else if (cleanedAmount <= 0.0) {
                                    Toast.makeText(context, "Enter Amount (> 0)", Toast.LENGTH_SHORT).show()
                                } else {
                                    val newItemString = "$cleanedName|$cleanedReceiptNo|$cleanedProduct|$cleanedLitres|$cleanedRate|$cleanedDesc|$cleanedAmount"
                                    udhariJamaRawString = if (udhariJamaRawString.isEmpty()) newItemString else "$udhariJamaRawString;$newItemString"
                                    tempUdhariJamaName = ""
                                    tempUdhariJamaReceiptNo = ""
                                    tempUdhariJamaProduct = "Cash"
                                    tempUdhariJamaLitres = ""
                                    tempUdhariJamaRate = ""
                                    tempUdhariJamaDesc = ""
                                    tempUdhariJamaAmount = ""
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF222222), shape = RoundedCornerShape(10.dp))
                                .testTag("four_nozzle_add_udhari_jama_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Udhari Jama",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (udhariJamaList.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFAFAFA).copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, Color(0xFFD3D3D3).copy(alpha = 0.3f)), shape = RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        ) {
                            udhariJamaList.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF222222)
                                        )
                                        Column {
                                            Text(
                                                text = if (item.receiptNo.isNotBlank()) "${item.name} (Rec: ${item.receiptNo})" else item.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (item.product == "Cash") item.description else "${item.product} | ${formatDouble(item.litres)} Ltrs @ ₹${formatDouble(item.rate)} | ${item.description}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "₹ " + formatDouble(item.amount),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        IconButton(
                                            onClick = {
                                                val items = udhariJamaRawString.split(";").toMutableList()
                                                if (index in items.indices) {
                                                    items.removeAt(index)
                                                    udhariJamaRawString = items.joinToString(";")
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Udhari Jama",
                                                tint = Color.Red.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Daily Deductions & Cash Ledger Card, subtracting PhonePe, Kharch, and Udhar from Grand Total
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = LanguageManager.translate("Total Fuel Sales", "कुल ईंधन बिक्री"),
                            style = Modifier.testTag("ms_hsd_sales_title_text").let { 
                                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            },
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "₹ " + formatDouble(totalFuelSalesAmount),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("ms_hsd_sales_total_display")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = LanguageManager.totalNetSaleL,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = formatDouble(finalNetLitres) + " L",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("ms_hsd_net_litres_display")
                        )
                    }

                    if (udhariJamaList.isNotEmpty()) {
                        udhariJamaList.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.name} (${item.product} @ ₹${formatDouble(item.rate)}) (+)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF222222)
                                )
                                Text(
                                    text = "₹ " + formatDouble(item.amount),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF222222)
                                    )
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Sales Grand Total",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "(Total Income to be reconciled)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }
                                Text(
                                    text = "₹ " + formatDouble(grandTotal),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("four_nozzle_grand_total_display")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Text(
                            text = "  DEDUCTIONS START HERE  ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }

                    // PhonePe input row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                            .border(
                                BorderStroke(1.dp, Color(0xFF1A1A1A).copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "PhonePe payments",
                                tint = Color(0xFF1A1A1A),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "PhonePe (₹)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1A1A1A)
                            )
                        }
                        
                        FourNozzleDensityInputField(
                            value = phonePeValue,
                            onValueChange = { phonePeValue = sanitizeInput(it) },
                            placeholder = "₹ 0",
                            modifier = Modifier
                                .width(120.dp)
                                .testTag("four_nozzle_phonepe_input")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))

                    // Cards input row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                            .border(
                                BorderStroke(1.dp, Color(0xFF1A1A1A).copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Card payments",
                                tint = Color(0xFF1A1A1A),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Cards (₹)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1A1A1A)
                            )
                        }
                        
                        FourNozzleDensityInputField(
                            value = cardsValue,
                            onValueChange = { cardsValue = sanitizeInput(it) },
                            placeholder = "₹ 0",
                            modifier = Modifier
                                .width(120.dp)
                                .testTag("four_nozzle_cards_input")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))

                    // Daily Expenses (Kharch) Row Integration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Kharch",
                                tint = Color(0xFF2D2D2D),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Daily Expenses (Kharch)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF2D2D2D)
                            )
                        }
                        if (totalKharch > 0.0) {
                            Text(
                                text = "-₹ " + formatDouble(totalKharch),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Color(0xFF2D2D2D)
                            )
                        } else {
                            Text(
                                text = "₹ 0",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                     // Input Form to log custom expense
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1.5f)) {
                            VoiceOutlinedTextField(
                                value = tempKharchDesc.ifEmpty { "Select Category" },
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Category", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("four_nozzle_kharch_desc_input"),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = {
                                    IconButton(onClick = { kharchDropdownExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Expense Category"
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2D2D2D),
                                    unfocusedBorderColor = Color(0xFF2D2D2D).copy(alpha = 0.3f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { kharchDropdownExpanded = true }
                            )

                            DropdownMenu(
                                expanded = kharchDropdownExpanded,
                                onDismissRequest = { kharchDropdownExpanded = false }
                            ) {
                                expenseOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            if (option == "Custom...") {
                                                tempKharchDesc = ""
                                            } else {
                                                tempKharchDesc = option
                                            }
                                            kharchDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (tempKharchDesc.isEmpty() || tempKharchDesc !in expenseOptions.filter { it != "Custom..." }) {
                             VoiceOutlinedTextField(
                                value = tempKharchDesc,
                                onValueChange = { tempKharchDesc = it },
                                placeholder = { Text("Custom Details", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(50.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2D2D2D),
                                    unfocusedBorderColor = Color(0xFF2D2D2D).copy(alpha = 0.3f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        VoiceOutlinedTextField(
                            value = tempKharchAmount,
                            onValueChange = { tempKharchAmount = sanitizeInput(it) },
                            placeholder = { Text("₹ Amt", fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("four_nozzle_kharch_amt_input"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2D2D2D),
                                unfocusedBorderColor = Color(0xFF2D2D2D).copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        IconButton(
                            onClick = {
                                val cleanedDesc = tempKharchDesc.trim().replace("|", "").replace(";", "")
                                val cleanedAmount = tempKharchAmount.toDoubleOrNull() ?: 0.0
                                if (cleanedDesc.isNotEmpty() && cleanedAmount > 0.0) {
                                    val newItemString = "$cleanedDesc|$cleanedAmount"
                                    kharchRawString = if (kharchRawString.isEmpty()) newItemString else "$kharchRawString;$newItemString"
                                    tempKharchDesc = ""
                                    tempKharchAmount = ""
                                } else {
                                    Toast.makeText(context, "Enter details and amount", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF2D2D2D), shape = RoundedCornerShape(10.dp))
                                .testTag("four_nozzle_add_kharch_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Expense",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Log of added expenses list
                    if (kharchList.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFAFAFA).copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, Color(0xFFD3D3D3).copy(alpha = 0.3f)), shape = RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        ) {
                            kharchList.forEachIndexed { index, (desc, amt) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF2D2D2D)
                                        )
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "₹ " + formatDouble(amt),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        IconButton(
                                            onClick = {
                                                val items = kharchRawString.split(";").toMutableList()
                                                if (index in items.indices) {
                                                    items.removeAt(index)
                                                    kharchRawString = items.joinToString(";")
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Expense",
                                                tint = Color.Red.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))

                    // Dynamic Credit Ledger (Udhar) Row Integration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Udhar",
                                tint = Color(0xFF333333),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Credit Ledger (Udhar)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF333333)
                            )
                        }
                        if (totalUdhar > 0.0) {
                            Text(
                                text = "-₹ " + formatDouble(totalUdhar),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Color(0xFF333333)
                            )
                        } else {
                            Text(
                                text = "₹ 0",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Input Form to log custom udhar entry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var udharNameDropdownExpanded by remember { mutableStateOf(false) }

                        if (showAddUdharNameDialog) {
                            AlertDialog(
                                onDismissRequest = { showAddUdharNameDialog = false },
                                title = { Text("Add New Udhari Name") },
                                text = {
                                    VoiceOutlinedTextField(
                                        value = newUdharNameInput,
                                        onValueChange = { newUdharNameInput = it },
                                        label = { Text("Udhari Name") },
                                        singleLine = true,
                                        modifier = Modifier.testTag("add_udhar_name_dialog_input")
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val name = newUdharNameInput.trim()
                                            if (name.isNotEmpty()) {
                                                coroutineScope.launch {
                                                    com.example.database.SupabaseRepository.saveUdhariNames(adminPhone, listOf(name))
                                                    // Immediately refresh local list
                                                    udhariNamesList = com.example.database.SupabaseRepository.getUdhariNames(adminPhone)
                                                }
                                                tempUdharName = name
                                                newUdharNameInput = ""
                                                showAddUdharNameDialog = false
                                            }
                                        },
                                        modifier = Modifier.testTag("add_udhar_name_dialog_confirm")
                                    ) {
                                        Text("Add")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showAddUdharNameDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        Box(modifier = Modifier.weight(1.2f)) {
                            VoiceOutlinedTextField(
                                value = tempUdharName.ifEmpty { "Select Udhari" },
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Select Udhari", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("four_nozzle_udhar_name_input"),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = {
                                    IconButton(onClick = { 
                                        udharNameDropdownExpanded = true 
                                        coroutineScope.launch {
                                            udhariNamesList = com.example.database.SupabaseRepository.getUdhariNames(adminPhone)
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Udhari Name"
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF333333),
                                    unfocusedBorderColor = Color(0xFF333333).copy(alpha = 0.3f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { 
                                        udharNameDropdownExpanded = true 
                                        coroutineScope.launch {
                                            udhariNamesList = com.example.database.SupabaseRepository.getUdhariNames(adminPhone)
                                        }
                                    }
                            )

                            DropdownMenu(
                                expanded = udharNameDropdownExpanded,
                                onDismissRequest = { udharNameDropdownExpanded = false }
                            ) {
                                udhariNamesList.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            tempUdharName = name
                                            udharNameDropdownExpanded = false
                                        }
                                    )
                                }
                                
                                HorizontalDivider()
                                
                                DropdownMenuItem(
                                    text = { Text("+ Add New Udhari...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        udharNameDropdownExpanded = false
                                        showAddUdharNameDialog = true
                                    }
                                )
                            }
                        }

                        VoiceOutlinedTextField(
                            value = tempUdharReceiptNo,
                            onValueChange = { tempUdharReceiptNo = it },
                            enabled = tempUdharProduct != "Cash",
                            placeholder = { Text("Receipt No", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("four_nozzle_udhar_receipt_input"),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF333333),
                                unfocusedBorderColor = Color(0xFF333333).copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    if (tempUdharName.isNotBlank() && tempUdharName != "Miscellaneous") {
                        val sessionJama = udhariJamaList.filter { it.name == tempUdharName }.sumOf { it.amount }
                        val sessionUdhar = udharList.filter { it.name == tempUdharName }.sumOf { it.amount }
                        val netBalance = udharPartyBalance + sessionUdhar - sessionJama

                        Text(
                            text = "Outstanding Balance: ₹ ${formatDouble(netBalance)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (netBalance > 0) Color(0xFFB91C1C) else Color(0xFF15803D)
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var productDropdownExpanded by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.weight(1.2f)) {
                            VoiceOutlinedTextField(
                                value = tempUdharProduct.ifEmpty { "Select Product" },
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Product", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("four_nozzle_udhar_product_input"),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = {
                                    IconButton(onClick = { productDropdownExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Product"
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF333333),
                                    unfocusedBorderColor = Color(0xFF333333).copy(alpha = 0.3f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { productDropdownExpanded = true }
                            )

                            DropdownMenu(
                                expanded = productDropdownExpanded,
                                onDismissRequest = { productDropdownExpanded = false }
                            ) {
                                (pumpProducts + "Cash").forEach { prod ->
                                    DropdownMenuItem(
                                        text = { Text(prod) },
                                        onClick = {
                                            tempUdharProduct = prod
                                            productDropdownExpanded = false
                                            if (prod == "Cash") {
                                                tempUdharRate = ""
                                                tempUdharReceiptNo = ""
                                                tempUdharLitres = ""
                                            } else {
                                                val newRate = productRates[prod]?.toString() ?: ""
                                                tempUdharRate = newRate
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        VoiceOutlinedTextField(
                            value = tempUdharLitres,
                            onValueChange = { tempUdharLitres = sanitizeInput(it) },
                            enabled = tempUdharProduct != "Cash",
                            placeholder = { Text("Litres", fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("four_nozzle_udhar_litres_input"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF333333),
                                unfocusedBorderColor = Color(0xFF333333).copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        VoiceOutlinedTextField(
                            value = tempUdharRate,
                            onValueChange = { tempUdharRate = sanitizeInput(it) },
                            enabled = tempUdharProduct != "Cash",
                            placeholder = { Text("Rate", fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("four_nozzle_udhar_rate_input"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF333333),
                                unfocusedBorderColor = Color(0xFF333333).copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VoiceOutlinedTextField(
                            value = tempUdharDesc,
                            onValueChange = { tempUdharDesc = it },
                            placeholder = { Text("Description", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("four_nozzle_udhar_desc_input"),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF333333),
                                unfocusedBorderColor = Color(0xFF333333).copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VoiceOutlinedTextField(
                            value = tempUdharAmount,
                            onValueChange = { tempUdharAmount = sanitizeInput(it) },
                            placeholder = { Text("₹ Amount", fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("four_nozzle_udhar_amt_input"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF333333),
                                unfocusedBorderColor = Color(0xFF333333).copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        IconButton(
                            onClick = {
                                val cleanedName = tempUdharName.trim().replace("|", "").replace(";", "")
                                val cleanedReceiptNo = tempUdharReceiptNo.trim().replace("|", "").replace(";", "")
                                val cleanedProduct = tempUdharProduct.trim().replace("|", "").replace(";", "")
                                val cleanedLitres = tempUdharLitres.toDoubleOrNull() ?: 0.0
                                val cleanedRate = tempUdharRate.toDoubleOrNull() ?: 0.0
                                val cleanedDesc = tempUdharDesc.trim().replace("|", "").replace(";", "")
                                val cleanedAmount = tempUdharAmount.toDoubleOrNull() ?: 0.0

                                if (cleanedName.isEmpty()) {
                                    Toast.makeText(context, "Enter Udhari Name", Toast.LENGTH_SHORT).show()
                                } else if (cleanedProduct.isEmpty()) {
                                    Toast.makeText(context, "Enter Product", Toast.LENGTH_SHORT).show()
                                } else if (cleanedAmount <= 0.0) {
                                    Toast.makeText(context, "Enter Amount (> 0)", Toast.LENGTH_SHORT).show()
                                } else {
                                    val newItemString = "$cleanedName|$cleanedReceiptNo|$cleanedProduct|$cleanedLitres|$cleanedRate|$cleanedDesc|$cleanedAmount"
                                    udharRawString = if (udharRawString.isEmpty()) newItemString else "$udharRawString;$newItemString"
                                    tempUdharName = ""
                                    tempUdharReceiptNo = ""
                                    tempUdharProduct = pumpProducts.firstOrNull() ?: ""
                                    tempUdharLitres = ""
                                    tempUdharRate = ""
                                    tempUdharDesc = ""
                                    tempUdharAmount = ""
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF333333), shape = RoundedCornerShape(10.dp))
                                .testTag("four_nozzle_add_udhar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Udhar",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Log of added credits list
                    if (udharList.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFAFAFA), shape = RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, Color(0xFFD3D3D3).copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        ) {
                            udharList.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF333333)
                                        )
                                        Column {
                                            Text(
                                                text = if (item.receiptNo.isNotBlank()) "${item.name} (Rec: ${item.receiptNo})" else item.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (item.product == "Cash") item.description else "${item.product} | ${formatDouble(item.litres)} Ltrs @ ₹${formatDouble(item.rate)} | ${item.description}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "₹ " + formatDouble(item.amount),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        IconButton(
                                            onClick = {
                                                val items = udharRawString.split(";").toMutableList()
                                                if (index in items.indices) {
                                                    items.removeAt(index)
                                                    udharRawString = items.joinToString(";")
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Udhar",
                                                tint = Color.Red.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Net Cash Balance",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "₹ " + formatDouble(finalNetCash),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF222222)
                            )
                        )
                    }


                }
            }

            // FINAL TALLY & CASH POSITION
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "FINAL SHIFT TALLY",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Expected Row
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Expected Net Cash Balance", style = MaterialTheme.typography.bodyMedium)
                            Text("₹ ${formatDouble(finalNetCash + cashSubmittedAmount)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }

                        HorizontalDivider(thickness = 0.5.dp)

                        // Cash Submitted Row
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Cash Submitted (₹)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            FourNozzleDensityInputField(
                                value = cashSubmittedValue,
                                onValueChange = { cashSubmittedValue = sanitizeInput(it) },
                                placeholder = "₹ 0",
                                modifier = Modifier.width(130.dp)
                            )
                        }

                        // Cash Balance Row
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("CASH BALANCE (₹)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            FourNozzleDensityInputField(
                                value = actualCashValue,
                                onValueChange = { actualCashValue = sanitizeInput(it) },
                                placeholder = "₹ 0",
                                modifier = Modifier.width(130.dp)
                            )
                        }

                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

                        // Tally Row
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Tally Difference", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            
                            val diffText = when {
                                Math.abs(cashDiscrepancy) < 1.0 -> "₹ 0 (Perfect)"
                                cashDiscrepancy < 0.0 -> "- ₹ ${formatDouble(-cashDiscrepancy)} [Short]"
                                else -> "+ ₹ ${formatDouble(cashDiscrepancy)} [Extra]"
                            }
                            
                            Text(
                                text = diffText,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = when {
                                        Math.abs(cashDiscrepancy) < 1.0 -> Color(0xFF15803D)
                                        cashDiscrepancy < 0.0 -> Color(0xFFB91C1C)
                                        else -> Color(0xFFC2410C)
                                    }
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmDialog = false },
            title = {
                Text(
                    text = LanguageManager.translate("Save Report", "रिपोर्ट सहेजें"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = LanguageManager.translate(
                        "Are you sure you want to save this report to history?",
                        "क्या आप वाकई इस रिपोर्ट को इतिहास में सहेजना चाहते हैं?"
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveConfirmDialog = false
                        isLoading = true
                        
                        coroutineScope.launch {
                            try {
                                val sharedTimestamp = System.currentTimeMillis()
                                val reportId = java.util.UUID.randomUUID().toString()
                                val summaryReport = getSummaryText()
                                val htmlReport = generateA4HtmlReport()
                                
                                val effectiveMeterNo = if (meterNo.isBlank()) {
                                    selectedNozzles.map { it.nozzleNumber }.distinct().sortedBy { extractNozzleNumber(it) }.joinToString(", ")
                                } else meterNo

                                // 1. Save Main Audit Record
                                val audit = SavedAudit(
                                    ownerAdminPhone = adminPhone,
                                    date = date,
                                    caName = caName,
                                    meterNo = effectiveMeterNo,
                                    auditType = "Shift Report & Tally",
                                    summaryText = summaryReport,
                                    htmlContent = htmlReport,
                                    timestamp = sharedTimestamp,
                                    cashSubmitted = cashSubmittedAmount,
                                    actualCashCollected = actualCashInHand,
                                    reportId = reportId,
                                    totalFuelSalesAmount = totalFuelSalesAmount,
                                    totalUdhariJama = totalUdhariJama,
                                    totalKharch = totalKharch,
                                    totalUdhar = totalUdhar,
                                    phonePeAmount = phonePeAmount,
                                    cardsAmount = cardsAmount,
                                    expectedCashBalance = finalNetCash,
                                    tallyDifference = cashDiscrepancy
                                )
                                com.example.database.SupabaseRepository.saveAudit(audit)

                                // Allow Supabase to index the inserted row to avoid race condition
                                kotlinx.coroutines.delay(1000)

                                // Update Manager's Daily Cash Ledger: Save TOTAL CASH (Submitted + Balance)
                                com.example.database.SupabaseRepository.upsertDailyCash(adminPhone, date, cashSubmittedAmount + actualCashInHand)

                                // Sync Udhari Names
                                val namesInJama = udhariJamaList.map { it.name }
                                val namesInUdhar = udharList.map { it.name }
                                val allUsedNames = (namesInJama + namesInUdhar).filter { it.isNotBlank() && it != "Miscellaneous" && it != "Legacy" }.distinct()
                                if (allUsedNames.isNotEmpty()) {
                                    val currentList = com.example.database.SupabaseRepository.getUdhariNames(adminPhone)
                                    val newList = (currentList + allUsedNames).distinct().sortedBy { it.lowercase() }
                                    if (newList.size > currentList.size) {
                                        com.example.database.SupabaseRepository.saveUdhariNames(adminPhone, newList)
                                    }
                                }

                                // 2. Save Nozzle Readings
                                val readingsToSave = nozzleReadings.map { nozzle ->
                                    val opening = nozzle.openingReading.toDoubleOrNull() ?: 0.0
                                    val closing = nozzle.closingReading.toDoubleOrNull() ?: 0.0
                                    val productName = nozzle.nozzleType

                                    val nozzlesForThisProduct = nozzleReadings.count { it.nozzleType == productName }
                                    val totalTestingForProduct = testingValues[productName]?.toDoubleOrNull() ?: 0.0
                                    val distributedTesting = if (nozzlesForThisProduct > 0) totalTestingForProduct / nozzlesForThisProduct else 0.0
                                    val netSales = (closing - opening) - distributedTesting

                                    NozzleReading(
                                        reportId = reportId,
                                        ownerAdminPhone = adminPhone,
                                        productName = productName,
                                        date = date,
                                        caName = caName,
                                        timestamp = sharedTimestamp,
                                        nozzleLabel = nozzle.nozzleName,
                                        opening = opening,
                                        closing = closing,
                                        testing = distributedTesting,
                                        netSales = netSales
                                    )
                                }
                                if (readingsToSave.isNotEmpty()) {
                                    com.example.database.SupabaseRepository.saveNozzleReadings(readingsToSave)
                                }

                                // 3. Save Financial Entries
                                val creditEntries = udharList.map { item ->
                                    CreditEntry(
                                        reportId = reportId,
                                        ownerAdminPhone = adminPhone,
                                        party = item.name,
                                        description = "${item.product} (${formatDouble(item.litres)}L @ ₹${formatDouble(item.rate)}) ${item.description}",
                                        amount = item.amount,
                                        date = date,
                                        timestamp = sharedTimestamp,
                                        caName = caName
                                    )
                                }
                                if (creditEntries.isNotEmpty()) com.example.database.SupabaseRepository.saveCreditEntries(creditEntries)
                                
                                val expenseEntries = kharchList.map { (desc, amt) ->
                                    ExpenseEntry(
                                        reportId = reportId,
                                        ownerAdminPhone = adminPhone,
                                        category = "General",
                                        description = desc,
                                        amount = amt,
                                        date = date,
                                        timestamp = sharedTimestamp,
                                        caName = caName
                                    )
                                }
                                if (expenseEntries.isNotEmpty()) com.example.database.SupabaseRepository.saveExpenseEntries(expenseEntries)
                                
                                val recoveryEntries = udhariJamaList.map { item ->
                                    RecoveryEntry(
                                        reportId = reportId,
                                        ownerAdminPhone = adminPhone,
                                        party = item.name,
                                        description = "${item.product} ${item.description}",
                                        amount = item.amount,
                                        date = date,
                                        timestamp = sharedTimestamp,
                                        caName = caName
                                    )
                                }
                                if (recoveryEntries.isNotEmpty()) com.example.database.SupabaseRepository.saveRecoveryEntries(recoveryEntries)

                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    Toast.makeText(context, "Report Saved Successfully!", Toast.LENGTH_LONG).show()
                                    clearAllInputs()
                                    onSaveSuccess?.invoke()
                                }
                            } catch (e: Exception) {
                                Log.e("CalculatorScreen", "Save failed", e)
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    Toast.makeText(context, "Save Failed: ${e.localizedMessage ?: "Unknown Error"}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                ) {
                    Text(LanguageManager.translate("Save", "सहेजें"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirmDialog = false }) {
                    Text(LanguageManager.translate("Cancel", "रद्द करें"))
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (isLoading) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
}
private fun shareReport(context: android.content.Context, reportText: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, reportText)
    }
    try {
        context.startActivity(Intent.createChooser(shareIntent, "Share Report"))
    } catch (anyEx: Exception) {
        Toast.makeText(context, "No sharing application found", Toast.LENGTH_SHORT).show()
    }
}


