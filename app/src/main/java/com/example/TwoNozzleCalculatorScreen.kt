package com.example

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.example.database.MsNozzleReading
import com.example.database.HsdNozzleReading
import com.example.database.GeneralNozzleReading
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun TwoNozzleDensityInputField(
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
            .height(56.dp)
            .background(
                if (enabled) Color.White else Color(0xFFF1F5F9),
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (enabled) {
                    Modifier.border(
                        1.dp,
                        Color(0xFF132563), // Active primary Navy border from image
                        RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier // No border for read-only to match image perfectly
                }
            )
            .padding(horizontal = 12.dp),
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
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (enabled) Color(0xFF112255) else Color(0xFF5D6B82),
                        textAlign = TextAlign.Start
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.Black.copy(alpha = 0.3f),
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
fun NozzleCard(
    title: String,
    closingVal: String,
    openingVal: String,
    actualSale: Double,
    salesAmount: Double,
    onClosingChanged: (String) -> Unit,
    onOpeningChanged: (String) -> Unit,
    closingTag: String,
    openingTag: String,
    actualSaleTag: String,
    salesAmountTag: String,
    badgeColor: Color,
    badgeTextColor: Color,
    modifier: Modifier = Modifier
) {
    val parsedNozzleName = if (title.contains("Nozzle 1")) "Nozzle 1" else if (title.contains("Nozzle 2")) "Nozzle 2" else title
    val parsedFuelType = if (title.contains("MS")) "Petrol Speed" else if (title.contains("HSD")) "Diesel Regular" else "Fuel"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, Color(0xFFD3DDEB)) // Soft gray-blue border to pop nicely
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Elegant Image-aligned Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Fuel Pump icon box
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalGasStation,
                            contentDescription = "Fuel Pump",
                            tint = Color(0xFF132563),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = parsedNozzleName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF132563)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Product badge
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFFDCE2F9), // Light purple-blue badge as in image
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = parsedFuelType,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF132563)
                                    )
                                )
                            }
                        }
                    }
                }

                // Checkmark icon on the far right as in image
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Verified",
                    tint = MaterialTheme.colorScheme.tertiary, // BrandSuccess green
                    modifier = Modifier.size(22.dp)
                )
            }

            // Input Fields Row (OPENING -> CLOSING) chronologically left-to-right as in image
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Opening Input (Left)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "OPENING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D6B82)
                        )
                    )
                    TwoNozzleDensityInputField(
                        value = openingVal,
                        onValueChange = onOpeningChanged,
                        placeholder = "0.0",
                        enabled = false,
                        modifier = Modifier.testTag(openingTag)
                    )
                }

                // Arrow -> pointing right
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "to",
                    tint = Color(0xFF5D6B82).copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .size(18.dp)
                )

                // Closing Input (Right)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "CLOSING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D6B82)
                        )
                    )
                    TwoNozzleDensityInputField(
                        value = closingVal,
                        onValueChange = onClosingChanged,
                        placeholder = "0.0",
                        modifier = Modifier.testTag(closingTag)
                    )
                }
            }

            // Sales Metrics display row as in image (green total sale highlighted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sale: ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D6B82)
                        )
                    )
                    Text(
                        text = "${formatDouble(actualSale)} L",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary // Success green
                        ),
                        modifier = Modifier.testTag(actualSaleTag)
                    )
                }

                Text(
                    text = "Amount: ₹ ${formatDouble(salesAmount)}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF132563)
                    ),
                    modifier = Modifier.testTag(salesAmountTag)
                )
            }
        }
    }
}

@Composable
fun TwoNozzleProductSummaryCard(
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
                        text = "Gross Sale",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatDouble(grossSale)} L",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
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
                    TwoNozzleDensityInputField(
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
                    TwoNozzleDensityInputField(
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
                        style = MaterialTheme.typography.titleLarge.copy(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoNozzleCalculatorScreen(
    onBack: () -> Unit,
    onSaveSuccess: (() -> Unit)? = null,
    date: String = "",
    caName: String = "",
    meterNo: String = "",
    nozzleCount: Int = 2,
    phone: String = "",
    adminPhone: String = "",
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val historyViewModel: HistoryViewModel = viewModel()

    LaunchedEffect(adminPhone) {
        if (adminPhone.isNotBlank()) {
            historyViewModel.setAdminPhone(adminPhone)
        }
    }
    
    var showSaveConfirmDialog by remember { mutableStateOf(false) }
    var activeNozzleCount by rememberSaveable { mutableStateOf(nozzleCount) }
    var nozzle1Closing by rememberSaveable { mutableStateOf("") }
    var nozzle1Opening by rememberSaveable { mutableStateOf("") }
    var msRateValue by rememberSaveable { mutableStateOf("") }
    var msTestingValue by rememberSaveable { mutableStateOf("") }

    var nozzle2Closing by rememberSaveable { mutableStateOf("") }
    var nozzle2Opening by rememberSaveable { mutableStateOf("") }
    var hsdRateValue by rememberSaveable { mutableStateOf("") }
    var hsdTestingValue by rememberSaveable { mutableStateOf("") }

    var phonePeValue by rememberSaveable { mutableStateOf("") }
    var cardsValue by rememberSaveable { mutableStateOf("") }
    var cashSubmittedValue by rememberSaveable { mutableStateOf("") }
    
    // Cash Denominations
    var notes500 by rememberSaveable { mutableStateOf("") }
    var notes200 by rememberSaveable { mutableStateOf("") }
    var notes100 by rememberSaveable { mutableStateOf("") }
    var notes50 by rememberSaveable { mutableStateOf("") }
    var notes20 by rememberSaveable { mutableStateOf("") }
    var notes10 by rememberSaveable { mutableStateOf("") }
    var notes5 by rememberSaveable { mutableStateOf("") }
    var coinsInput by rememberSaveable { mutableStateOf("") }
    
    // Custom Udhari Jama - Serialized string as "name1|receiptNo1|product1|litres1|rate1|desc1|amount1;name2|receiptNo2|product2|litres2|rate2|desc2|amount2"
    var udhariJamaRawString by rememberSaveable { mutableStateOf("") }
    var tempUdhariJamaName by rememberSaveable { mutableStateOf("") }
    var tempUdhariJamaReceiptNo by rememberSaveable { mutableStateOf("") }
    var tempUdhariJamaProduct by rememberSaveable { mutableStateOf("MS") }
    var tempUdhariJamaLitres by rememberSaveable { mutableStateOf("") }
    var tempUdhariJamaRate by rememberSaveable { mutableStateOf("") }
    var tempUdhariJamaDesc by rememberSaveable { mutableStateOf("") }
    var tempUdhariJamaAmount by rememberSaveable { mutableStateOf("") }

    val extractDouble: (String) -> Double = { input ->
        val direct = input.toDoubleOrNull()
        if (direct != null) direct
        else {
            val regex = """[0-9]+(?:\.[0-9]+)?""".toRegex()
            val match = regex.find(input)
            match?.value?.toDoubleOrNull() ?: 0.0
        }
    }

    // Custom Expenses (Kharch) - Serialized string as "desc1|amount1;desc2|amount2"
    var kharchRawString by rememberSaveable { mutableStateOf("") }
    var tempKharchDesc by rememberSaveable { mutableStateOf("") }
    var tempKharchAmount by rememberSaveable { mutableStateOf("") }

    // Custom Credit (Udhar) - Serialized string as "name|receiptNo|product|litres|rate|desc|amount"
    var udharRawString by rememberSaveable { mutableStateOf("") }
    var tempUdharName by rememberSaveable { mutableStateOf("") }
    var tempUdharReceiptNo by rememberSaveable { mutableStateOf("") }
    var tempUdharProduct by rememberSaveable { mutableStateOf("MS") }
    var tempUdharLitres by rememberSaveable { mutableStateOf("") }
    var tempUdharRate by rememberSaveable { mutableStateOf("") }
    var tempUdharDesc by rememberSaveable { mutableStateOf("") }
    var tempUdharAmount by rememberSaveable { mutableStateOf("") }
    var showAddUdharNameDialog by remember { mutableStateOf(false) }
    var newUdharNameInput by remember { mutableStateOf("") }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var udhariNamesList by remember { mutableStateOf(listOf("Miscellaneous")) }
    var showAddUdhariDialog by remember { mutableStateOf(false) }
    var newUdhariNameInput by remember { mutableStateOf("") }

    var densityMs by remember { mutableStateOf("") }
    var densityHsd by remember { mutableStateOf("") }
    var stockMs by remember { mutableStateOf("") }
    var stockHsd by remember { mutableStateOf("") }
    var receiptMs by remember { mutableStateOf("") }
    var receiptHsd by remember { mutableStateOf("") }

    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(date, adminPhone) {
        if (adminPhone.isNotBlank()) {
            val config = com.example.database.FirestoreRepository.getDailyConfig(adminPhone, date)
            densityMs = config["density_ms"] ?: ""
            densityHsd = config["density_hsd"] ?: ""
            msRateValue = config["rate_ms"] ?: ""
            hsdRateValue = config["rate_hsd"] ?: ""
            stockMs = config["stock_ms"] ?: ""
            stockHsd = config["stock_hsd"] ?: ""
            receiptMs = config["receipt_ms"] ?: ""
            receiptHsd = config["receipt_hsd"] ?: ""

            udhariNamesList = com.example.database.FirestoreRepository.getUdhariNames(adminPhone)
        }
    }

    LaunchedEffect(phone, adminPhone, date) {
        if (phone.isNotEmpty() && adminPhone.isNotEmpty()) {
            historyViewModel.setAdminPhone(adminPhone)

            launch {
                combine(
                    historyViewModel.allMsNozzleReadings,
                    historyViewModel.allHsdNozzleReadings
                ) { ms: List<MsNozzleReading>, hsd: List<HsdNozzleReading> ->
                    ms to hsd
                }.collect { (msReadings, hsdReadings) ->
                    // Noz 1 (MS)
                    val dayMs = msReadings.filter { it.date == date && it.nozzleLabel == "Noz 1 (MS)" }.maxByOrNull { it.timestamp }
                    var openingMs: Double? = dayMs?.closingReading
                    
                    if (openingMs == null) {
                        openingMs = msReadings.filter { it.nozzleLabel == "Noz 1 (MS)" }.maxByOrNull { it.timestamp }?.closingReading
                    }

                    if (nozzle1Opening.isEmpty()) {
                        if (openingMs != null) {
                            nozzle1Opening = if (openingMs % 1.0 == 0.0) openingMs.toLong().toString() else openingMs.toString()
                        } else {
                            val initialVal = historyViewModel.getInitialReadingForNozzle("Noz 1 (MS)", adminPhone)
                            nozzle1Opening = if (initialVal % 1.0 == 0.0) initialVal.toLong().toString() else initialVal.toString()
                        }
                    }

                    // Noz 2 (HSD)
                    val dayHsd = hsdReadings.filter { it.date == date && it.nozzleLabel == "Noz 2 (HSD)" }.maxByOrNull { it.timestamp }
                    var openingHsd: Double? = dayHsd?.closingReading

                    if (openingHsd == null) {
                        openingHsd = hsdReadings.filter { it.nozzleLabel == "Noz 2 (HSD)" }.maxByOrNull { it.timestamp }?.closingReading
                    }

                    if (nozzle2Opening.isEmpty()) {
                        if (openingHsd != null) {
                            nozzle2Opening = if (openingHsd % 1.0 == 0.0) openingHsd.toLong().toString() else openingHsd.toString()
                        } else {
                            val initialVal = historyViewModel.getInitialReadingForNozzle("Noz 2 (HSD)", adminPhone)
                            nozzle2Opening = if (initialVal % 1.0 == 0.0) initialVal.toLong().toString() else initialVal.toString()
                        }
                    }
                }
            }
            
            udhariNamesList = com.example.database.FirestoreRepository.getUdhariNames(adminPhone)
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

    // Calculations
    val msTesting = msTestingValue.toDoubleOrNull() ?: 0.0
    val hsdTesting = if (activeNozzleCount >= 2) (hsdTestingValue.toDoubleOrNull() ?: 0.0) else 0.0

    val n1Closing = nozzle1Closing.toDoubleOrNull() ?: 0.0
    val n1Opening = nozzle1Opening.toDoubleOrNull() ?: 0.0
    val n1Sale = n1Closing - n1Opening
    val msRate = msRateValue.toDoubleOrNull() ?: 0.0
    val msNetSale = n1Sale - msTesting
    val msSalesAmount = msNetSale * msRate

    val n2Closing = if (activeNozzleCount >= 2) (nozzle2Closing.toDoubleOrNull() ?: 0.0) else 0.0
    val n2Opening = if (activeNozzleCount >= 2) (nozzle2Opening.toDoubleOrNull() ?: 0.0) else 0.0
    val n2Sale = if (activeNozzleCount >= 2) (n2Closing - n2Opening) else 0.0
    val hsdRate = if (activeNozzleCount >= 2) (hsdRateValue.toDoubleOrNull() ?: 0.0) else 0.0
    val hsdNetSale = if (activeNozzleCount >= 2) (n2Sale - hsdTesting) else 0.0
    val hsdSalesAmount = if (activeNozzleCount >= 2) (hsdNetSale * hsdRate) else 0.0

    val grandActualTotal = n1Sale + n2Sale
    val totalTesting = msTesting + hsdTesting
    val finalResult = grandActualTotal - totalTesting
    val grandTotal = msSalesAmount + hsdSalesAmount + totalUdhariJama
    val phonePeAmount = phonePeValue.toDoubleOrNull() ?: 0.0
    val cardsAmount = cardsValue.toDoubleOrNull() ?: 0.0
    val cashSubmittedAmount = cashSubmittedValue.toDoubleOrNull() ?: 0.0
    val finalNetCash = grandTotal - phonePeAmount - cardsAmount - totalKharch - totalUdhar - cashSubmittedAmount

    // Total Physical Cash Calculations
    val count500 = notes500.toIntOrNull() ?: 0
    val count200 = notes200.toIntOrNull() ?: 0
    val count100 = notes100.toIntOrNull() ?: 0
    val count50 = notes50.toIntOrNull() ?: 0
    val count20 = notes20.toIntOrNull() ?: 0
    val count10 = notes10.toIntOrNull() ?: 0
    val count5 = notes5.toIntOrNull() ?: 0
    val coinsAmt = coinsInput.toDoubleOrNull() ?: 0.0

    val actualCashInHand = (count500 * 500) + (count200 * 200) + (count100 * 100) + (count50 * 50) + (count20 * 20) + (count10 * 10) + (count5 * 5) + coinsAmt

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
        nozzle1Closing = ""
        nozzle1Opening = ""
        msRateValue = ""
        msTestingValue = ""
        nozzle2Closing = ""
        nozzle2Opening = ""
        hsdRateValue = ""
        hsdTestingValue = ""
        phonePeValue = ""
        cardsValue = ""
        cashSubmittedValue = ""
        udhariJamaRawString = ""
        tempUdhariJamaName = ""
        tempUdhariJamaReceiptNo = ""
        tempUdhariJamaProduct = "MS"
        tempUdhariJamaLitres = ""
        tempUdhariJamaRate = ""
        tempUdhariJamaDesc = ""
        tempUdhariJamaAmount = ""
        kharchRawString = ""
        tempKharchDesc = ""
        tempKharchAmount = ""
        udharRawString = ""
        tempUdharName = ""
        tempUdharReceiptNo = ""
        tempUdharProduct = "MS"
        tempUdharLitres = ""
        tempUdharRate = ""
        tempUdharDesc = ""
        tempUdharAmount = ""
        notes500 = ""
        notes200 = ""
        notes100 = ""
        notes50 = ""
        notes20 = ""
        notes10 = ""
        notes5 = ""
        coinsInput = ""
    }

    fun getSummaryText(): String {
        val sb = StringBuilder()
        sb.append("--- D R INAMDAR PETROLEUM ---\n")
        sb.append("SHIFT REPORT & TALLY\n")
        sb.append("Date: ${date.ifEmpty { "N/A" }} | CA: ${caName.ifEmpty { "N/A" }}\n")
        sb.append("Meter No: ${meterNo.ifEmpty { "N/A" }}\n")
        sb.append("-----------------------------\n")
        
        val sharedPrefs = context.getSharedPreferences("pump_manager_prefs", android.content.Context.MODE_PRIVATE)
        val dMs = sharedPrefs.getString("density_ms_$date", "") ?: ""
        val dHsd = sharedPrefs.getString("density_hsd_$date", "") ?: ""
        val sMs = sharedPrefs.getString("stock_ms_$date", "") ?: ""
        val sHsd = sharedPrefs.getString("stock_hsd_$date", "") ?: ""
        val rMs = if (msRateValue.isNotBlank()) msRateValue else (sharedPrefs.getString("rate_ms_$date", "") ?: "")
        val rHsd = if (hsdRateValue.isNotBlank()) hsdRateValue else (sharedPrefs.getString("rate_hsd_$date", "") ?: "")

        sb.append("DAILY PARAMETERS:\n")
        sb.append("  MS Petrol:\n")
        sb.append("    Rate: ₹${if (rMs.isNotBlank()) rMs else "N/A"}/L\n")
        sb.append("    Density: ${if (dMs.isNotBlank()) "$dMs kg/m³" else "N/A"}\n")
        sb.append("    Opening Stock: ${if (sMs.isNotBlank()) "$sMs L" else "N/A"}\n")
        sb.append("  HSD Diesel:\n")
        sb.append("    Rate: ₹${if (rHsd.isNotBlank()) rHsd else "N/A"}/L\n")
        sb.append("    Density: ${if (dHsd.isNotBlank()) "$dHsd kg/m³" else "N/A"}\n")
        sb.append("    Opening Stock: ${if (sHsd.isNotBlank()) "$sHsd L" else "N/A"}\n")
        sb.append("-----------------------------\n")
        
        sb.append("Noz 1 (MS): Op:${nozzle1Opening.ifEmpty { "0" }} Cl:${nozzle1Closing.ifEmpty { "0" }}\n")
        sb.append("  Gross Sale: ${formatDouble(n1Sale)} L\n")
        if (msTesting > 0.0) {
            sb.append("  Testing Deduction: -${formatDouble(msTesting)} L\n")
        }
        sb.append("  Net Sale: ${formatDouble(msNetSale)} L @ ₹${formatDouble(msRate)}/L\n")
        sb.append("  Amt: ₹${formatDouble(msSalesAmount)}\n\n")
        
        sb.append("Noz 2 (HSD): Op:${nozzle2Opening.ifEmpty { "0" }} Cl:${nozzle2Closing.ifEmpty { "0" }}\n")
        sb.append("  Gross Sale: ${formatDouble(n2Sale)} L\n")
        if (hsdTesting > 0.0) {
            sb.append("  Testing Deduction: -${formatDouble(hsdTesting)} L\n")
        }
        sb.append("  Net Sale: ${formatDouble(hsdNetSale)} L @ ₹${formatDouble(hsdRate)}/L\n")
        sb.append("  Amt: ₹${formatDouble(hsdSalesAmount)}\n")
        
        sb.append("-----------------------------\n")
        sb.append("Grand Total Vol (Gross): ${formatDouble(grandActualTotal)} L\n")
        if (totalTesting > 0.0) {
            sb.append("Total Testing Deduction: -${formatDouble(totalTesting)} L\n")
        }
        sb.append("Net Sales Volume (Final): ${formatDouble(finalResult)} L\n")
        sb.append("-----------------------------\n")
        sb.append("MS Sales Value: ₹${formatDouble(msSalesAmount)}\n")
        sb.append("HSD Sales Value: ₹${formatDouble(hsdSalesAmount)}\n")
        sb.append("Total Sales (MS+HSD): ₹${formatDouble(msSalesAmount + hsdSalesAmount)}\n")
        if (udhariJamaList.isNotEmpty()) {
            sb.append("Udhari Jama (Recoveries):\n")
            udhariJamaList.sortedBy { it.name.lowercase() }.forEach { item ->
                val details = if (item.product == "Cash") {
                    "Cash" + (if (item.receiptNo.isNotBlank()) " | Rec. No. ${item.receiptNo}" else "")
                } else {
                    "${item.product} | ${formatDouble(item.litres)} Ltrs @ ₹${formatDouble(item.rate)}" + (if (item.receiptNo.isNotBlank()) " | Rec. No. ${item.receiptNo}" else "")
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
        sb.append("ACTUAL CASH COLLECTED: ₹${formatDouble(actualCashInHand)}\n")

        if (count500 > 0 || count200 > 0 || count100 > 0 || count50 > 0 || count20 > 0 || count10 > 0 || count5 > 0 || coinsAmt > 0) {
            sb.append("CASH DENOMINATION BREAKDOWN:\n")
            val denoms = mutableListOf<String>()
            if (count500 > 0) denoms.add("  500x$count500=₹${count500 * 500}")
            if (count200 > 0) denoms.add("  200x$count200=₹${count200 * 200}")
            if (count100 > 0) denoms.add("  100x$count100=₹${count100 * 100}")
            if (count50 > 0)  denoms.add("   50x$count50=₹${count50 * 50}")
            if (count20 > 0)  denoms.add("   20x$count20=₹${count20 * 20}")
            if (count10 > 0)  denoms.add("   10x$count10=₹${count10 * 10}")
            if (count5 > 0)   denoms.add("    5x$count5=₹${count5 * 5}")
            if (coinsAmt > 0) denoms.add("  Coins=₹${formatDouble(coinsAmt)}")
            denoms.forEach { sb.append(it + "\n") }
        }

        sb.append("-----------------------------\n")
        when {
            cashDiscrepancy == 0.0 -> sb.append("TALLY RESULT: SUCCESS (Perfect matching)\n")
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
        
        val rMs = if (msRateValue.isNotBlank()) "₹$msRateValue" else "N/A"
        val rHsd = if (hsdRateValue.isNotBlank()) "₹$hsdRateValue" else "N/A"

        val tableUdhariJamaHtml = if (udhariJamaList.isEmpty()) {
            "<tr><td colspan='2' style='text-align: center; color: #a0aec0;'>No recoveries entered</td></tr>"
        } else {
            udhariJamaList.sortedBy { it.name.lowercase() }.joinToString("") { item ->
                val details = if (item.product == "Cash") {
                    "Cash" + (if (item.receiptNo.isNotBlank()) " | Rec. No. ${item.receiptNo}" else "")
                } else {
                    "${item.product} | ${formatDouble(item.litres)} Ltrs @ ₹${formatDouble(item.rate)}" + (if (item.receiptNo.isNotBlank()) " | Rec. No. ${item.receiptNo}" else "")
                }
                "<tr><td>+ Rec: <b>${item.name}</b> ($details)<br/><span style='font-size: 0.85em; color: #555;'>${item.description}</span></td><td class='num'>₹${formatDouble(item.amount)}</td></tr>"
            }
        }

        val tableKharchHtml = if (kharchList.isEmpty()) {
            "<tr><td colspan='2' style='text-align: center; color: #a0aec0;'>No expense entries recorded</td></tr>"
        } else {
            kharchList.sortedBy { it.first.lowercase() }.joinToString("") { (desc, amt) ->
                "<tr><td>- Exp: $desc</td><td class='num'>-₹${formatDouble(amt)}</td></tr>"
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
                "<tr><td>- Crd: <b>${item.name}</b> ($details)<br/><span style='font-size: 0.85em; color: #555;'>${item.description}</span></td><td class='num'>-₹${formatDouble(item.amount)}</td></tr>"
            }
        }

        val denomsList = mutableListOf<Pair<String, Double>>()
        if (count500 > 0) denomsList.add(Pair("500 x $count500", (count500 * 500).toDouble()))
        if (count200 > 0) denomsList.add(Pair("200 x $count200", (count200 * 200).toDouble()))
        if (count100 > 0) denomsList.add(Pair("100 x $count100", (count100 * 100).toDouble()))
        if (count50 > 0)  denomsList.add(Pair("50 x $count50", (count50 * 50).toDouble()))
        if (count20 > 0)  denomsList.add(Pair("20 x $count20", (count20 * 20).toDouble()))
        if (count10 > 0)  denomsList.add(Pair("10 x $count10", (count10 * 10).toDouble()))
        if (count5 > 0)   denomsList.add(Pair("5 x $count5", (count5 * 5).toDouble()))
        if (coinsAmt > 0) denomsList.add(Pair("Coins", coinsAmt))

        val denomsHtml = if (denomsList.isEmpty()) {
            "<tr><td colspan='2' style='text-align: center; color: #a0aec0;'>No cash breakdown entered</td></tr>"
        } else {
            denomsList.joinToString("") { (label, value) ->
                "<tr><td>$label</td><td class='num'>₹${formatDouble(value)}</td></tr>"
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
                <title>Fuel Station Reconciliation Report (2-Nozzle)</title>
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
                        font-size: 9px;
                        line-height: 1.25;
                        background-color: #fff;
                    }
                    .header-title-box {
                        text-align: center;
                        border-bottom: 2px solid #1a365d;
                        padding-bottom: 4px;
                        margin-bottom: 8px;
                    }
                    .header-title-box h1 {
                        font-size: 14px;
                        margin: 0;
                        color: #1a365d;
                        text-transform: uppercase;
                        letter-spacing: 0.8px;
                        font-weight: 800;
                    }
                    .header-sub {
                        font-size: 9px;
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
                        font-size: 8.5px;
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
                        font-size: 8.2px;
                    }
                    th {
                        background-color: #ebf8ff;
                        color: #2b6cb0;
                        font-weight: bold;
                        border-bottom: 1px solid #cbd5e0;
                        text-align: left;
                        padding: 3px 4px;
                        text-transform: uppercase;
                        font-size: 7.5px;
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
                                    <tr>
                                        <td><strong>MS Petrol</strong></td>
                                        <td class="num">$rMs</td>
                                        <td class="num">${if (densityMs.isNotBlank()) densityMs else "N/A"}</td>
                                        <td class="num">${if (stockMs.isNotBlank()) "$stockMs L" else "N/A"}</td>
                                        <td class="num">${if (receiptMs.isNotBlank()) "$receiptMs L" else "0.0 L"}</td>
                                    </tr>
                                    <tr>
                                        <td><strong>HSD Diesel</strong></td>
                                        <td class="num">$rHsd</td>
                                        <td class="num">${if (densityHsd.isNotBlank()) densityHsd else "N/A"}</td>
                                        <td class="num">${if (stockHsd.isNotBlank()) "$stockHsd L" else "N/A"}</td>
                                        <td class="num">${if (receiptHsd.isNotBlank()) "$receiptHsd L" else "0.0 L"}</td>
                                    </tr>
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
                                        <th style="text-align:right;">Value (₹)</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td>Noz 1 (MS)</td>
                                        <td>${nozzle1Opening.ifEmpty { "0" }}</td>
                                        <td>${nozzle1Closing.ifEmpty { "0" }}</td>
                                        <td class="bold">${formatDouble(n1Sale)} L</td>
                                        <td class="num">₹${formatDouble(msSalesAmount)}</td>
                                    </tr>
                                    <tr>
                                        <td>Noz 2 (HSD)</td>
                                        <td>${nozzle2Opening.ifEmpty { "0" }}</td>
                                        <td>${nozzle2Closing.ifEmpty { "0" }}</td>
                                        <td class="bold">${formatDouble(n2Sale)} L</td>
                                        <td class="num">₹${formatDouble(hsdSalesAmount)}</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>

                        <!-- METER RECONCILIATION SUMMARY WITH TESTING SUBTRACTION -->
                        <div class="card">
                            <div class="card-caption">FUEL RECONCILIATION & TESTING SUMMARY</div>
                            <table>
                                <tbody>
                                    <tr>
                                        <td>MS Petrol Meter Gross Sum</td>
                                        <td class="num">${formatDouble(n1Sale)} L</td>
                                    </tr>
                                    <tr>
                                        <td>MS Testing Deduction</td>
                                        <td class="num">-${formatDouble(msTesting)} L</td>
                                    </tr>
                                    <tr class="highlight">
                                        <td>Net MS Petrol Sales (L) @ ₹${formatDouble(msRate)}/L</td>
                                        <td class="num">${formatDouble(msNetSale)} L &nbsp;|&nbsp; <strong>₹${formatDouble(msSalesAmount)}</strong></td>
                                    </tr>
                                    <tr>
                                        <td>HSD Diesel Meter Gross Sum</td>
                                        <td class="num">${formatDouble(n2Sale)} L</td>
                                    </tr>
                                    <tr>
                                        <td>HSD Testing Deduction</td>
                                        <td class="num">-${formatDouble(hsdTesting)} L</td>
                                    </tr>
                                    <tr class="highlight">
                                        <td>Net HSD Diesel Sales (L) @ ₹${formatDouble(hsdRate)}/L</td>
                                        <td class="num">${formatDouble(hsdNetSale)} L &nbsp;|&nbsp; <strong>₹${formatDouble(hsdSalesAmount)}</strong></td>
                                    </tr>
                                    <tr class="bold-total">
                                        <td>COMBINED BASE FUEL VOLUME (NET)</td>
                                        <td class="num">${formatDouble(finalResult)} L</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>

                        <!-- CASH DENOMINATION BREAKDOWN -->
                        <div class="card">
                            <div class="card-caption">CASH DENOMINATION DETAIL</div>
                            <table>
                                <thead>
                                    <tr>
                                        <th>Denomination</th>
                                        <th class="num">Amount (₹)</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    $denomsHtml
                                    <tr class="bold-total">
                                        <td>TOTAL CASH FROM BREAKDOWN</td>
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
                                    <tr>
                                        <td>&nbsp;&bull;&nbsp; MS Petrol Sales (Net)</td>
                                        <td class="num">₹${formatDouble(msSalesAmount)}</td>
                                    </tr>
                                    <tr>
                                        <td>&nbsp;&bull;&nbsp; HSD Diesel Sales (Net)</td>
                                        <td class="num">₹${formatDouble(hsdSalesAmount)}</td>
                                    </tr>
                                    <tr style="font-weight: bold; background-color: #fcfcfc;">
                                        <td>Base Reconciled Fuel Sales Value</td>
                                        <td class="num">₹${formatDouble(msSalesAmount + hsdSalesAmount)}</td>
                                    </tr>
                                    $tableUdhariJamaHtml
                                    <tr class="highlight">
                                        <td class="bold">GRAND TOTAL EXPECTED REVENUE</td>
                                        <td class="num">₹${formatDouble(grandTotal)}</td>
                                    </tr>
                                    <tr>
                                        <td>PhonePe Transaction Deduction</td>
                                        <td class="num">-₹${formatDouble(phonePeAmount)}</td>
                                    </tr>
                                    <tr>
                                        <td>Cards / Swipe Deduction</td>
                                        <td class="num">-₹${formatDouble(cardsAmount)}</td>
                                    </tr>
                                    <tr>
                                        <td>Direct Bank Deposit / Cash Submitted</td>
                                        <td class="num">-₹${formatDouble(cashSubmittedAmount)}</td>
                                    </tr>
                                    $tableKharchHtml
                                    $tableUdharHtml
                                    <tr class="bold-total">
                                        <td>EXPECTED CASH BALANCE TO BE REMITTED</td>
                                        <td class="num">₹${formatDouble(finalNetCash)}</td>
                                    </tr>
                                    <tr>
                                        <td>ACTUAL CASH IN HAND (REMITTED)</td>
                                        <td class="num">₹${formatDouble(actualCashInHand)}</td>
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
                            modifier = Modifier.testTag("ms_header_title")
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("two_nozzle_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to mode selection page",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            clearAllInputs()
                            Toast.makeText(context, "All Fields Reset", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .testTag("ms_clear_all_button")
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
                        .windowInsetsPadding(WindowInsets.navigationBars)
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
                            .testTag("ms_reset_action_bar_button"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val n1Closing = nozzle1Closing.trim()
                            if (n1Closing.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    LanguageManager.translate(
                                        "Please enter closing reading for Nozzle 1 (MS)!",
                                        "कृपया नोजल 1 (MS) के लिए अंतिम रीडिंग दर्ज करें!"
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            if (activeNozzleCount >= 2) {
                                val n2Closing = nozzle2Closing.trim()
                                if (n2Closing.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        LanguageManager.translate(
                                            "Please enter closing reading for Nozzle 2 (HSD)!",
                                            "कृपया नोजल 2 (HSD) के लिए अंतिम रीडिंग दर्ज करें!"
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }
                            }
                            showSaveConfirmDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("ms_save_audit_button"),
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
                text = "Operation: MS & HSD Sales (2 Nozzles)",
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
                    .testTag("two_nozzle_header_info_card"),
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

            // Dynamic Nozzle Count Selector inside calculation page
            Card(
                modifier = Modifier.fillMaxWidth().testTag("nozzle_count_selector_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Active Nozzles in calculation:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2).forEach { count ->
                            val isSelected = activeNozzleCount == count
                            FilterChip(
                                selected = isSelected,
                                onClick = { activeNozzleCount = count },
                                label = { Text("$count Nozzle${if (count > 1) "s" else ""}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("active_nozzles_chip_$count")
                            )
                        }
                    }
                }
            }

            // Nozzle 1: MS
            NozzleCard(
                title = "Nozzle 1 (MS)",
                closingVal = nozzle1Closing,
                openingVal = nozzle1Opening,
                actualSale = n1Sale,
                salesAmount = n1Sale * msRate,
                onClosingChanged = { nozzle1Closing = sanitizeInput(it) },
                onOpeningChanged = { nozzle1Opening = sanitizeInput(it) },
                closingTag = "nozzle1_closing_input",
                openingTag = "nozzle1_opening_input",
                actualSaleTag = "nozzle1_sale_display",
                salesAmountTag = "nozzle1_sales_amount_display",
                badgeColor = MaterialTheme.colorScheme.primary,
                badgeTextColor = MaterialTheme.colorScheme.onPrimary
            )

            // MS Total Summary Card with Rate & Testing Inputs
            TwoNozzleProductSummaryCard(
                title = "Motor Spirit (MS)",
                grossSale = n1Sale,
                testingVal = msTestingValue,
                onTestingChanged = { msTestingValue = sanitizeInput(it) },
                rateVal = msRateValue,
                onRateChanged = { msRateValue = sanitizeInput(it) },
                netSale = msNetSale,
                totalSalesAmount = msSalesAmount,
                badgeColor = MaterialTheme.colorScheme.primary,
                badgeTextColor = MaterialTheme.colorScheme.onPrimary,
                testingTag = "ms_testing_input",
                rateTag = "ms_rate_input",
                netSaleTag = "ms_net_sale_display",
                totalSalesAmountTag = "ms_total_sales_amount_display"
            )

            // Nozzle 2: HSD
            if (activeNozzleCount >= 2) {
                NozzleCard(
                    title = "Nozzle 2 (HSD)",
                    closingVal = nozzle2Closing,
                    openingVal = nozzle2Opening,
                    actualSale = n2Sale,
                    salesAmount = n2Sale * hsdRate,
                    onClosingChanged = { nozzle2Closing = sanitizeInput(it) },
                    onOpeningChanged = { nozzle2Opening = sanitizeInput(it) },
                    closingTag = "nozzle2_closing_input",
                    openingTag = "nozzle2_opening_input",
                    actualSaleTag = "nozzle2_sale_display",
                    salesAmountTag = "nozzle2_sales_amount_display",
                    badgeColor = MaterialTheme.colorScheme.tertiary,
                    badgeTextColor = MaterialTheme.colorScheme.onTertiary
                )

                // HSD Total Summary Card with Rate & Testing Inputs
                TwoNozzleProductSummaryCard(
                    title = "High Speed Diesel (HSD)",
                    grossSale = n2Sale,
                    testingVal = hsdTestingValue,
                    onTestingChanged = { hsdTestingValue = sanitizeInput(it) },
                    rateVal = hsdRateValue,
                    onRateChanged = { hsdRateValue = sanitizeInput(it) },
                    netSale = hsdNetSale,
                    totalSalesAmount = hsdSalesAmount,
                    badgeColor = MaterialTheme.colorScheme.tertiary,
                    badgeTextColor = MaterialTheme.colorScheme.onTertiary,
                    testingTag = "hsd_testing_input",
                    rateTag = "hsd_rate_input",
                    netSaleTag = "hsd_net_sale_display",
                    totalSalesAmountTag = "hsd_total_sales_amount_display"
                )
            }

            // Dynamic Udhari Jama Card below Nozzle 2 HSD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
                                .background(Color(0xFF222222), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "UDHARI JAMA (ADD)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                        Text(
                            text = "CREDIT RECOVERY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF222222).copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Text(
                        text = "Any recovered outstanding credit (Udhari Jama) here will be automatically added to the Sales Grand Total.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    OutlinedTextField(
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
                                                if (!udhariNamesList.contains(name)) {
                                                    val newList = udhariNamesList + name
                                                    udhariNamesList = newList
                                                    coroutineScope.launch {
                                                        com.example.database.FirestoreRepository.saveUdhariNames(adminPhone, newList)
                                                    }
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
                            OutlinedTextField(
                                value = tempUdhariJamaName.ifEmpty { "Select Udhari" },
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Select Udhari", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("two_nozzle_udhari_jama_name_input"),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = {
                                    IconButton(onClick = { udhariDropdownExpanded = true }) {
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
                                    .clickable { udhariDropdownExpanded = true }
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
                                .testTag("two_nozzle_udhari_jama_desc_input"),
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
                                .testTag("two_nozzle_udhari_jama_amt_input"),
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
                                    tempUdhariJamaProduct = "MS"
                                    tempUdhariJamaLitres = ""
                                    tempUdhariJamaRate = ""
                                    tempUdhariJamaDesc = ""
                                    tempUdhariJamaAmount = ""
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF222222), shape = RoundedCornerShape(10.dp))
                                .testTag("two_nozzle_add_udhari_jama_button")
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
                    Text(
                        text = LanguageManager.salesReconciliationTitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            letterSpacing = 0.5.sp
                        )
                    )

                    val baseSalesValue = msSalesAmount + hsdSalesAmount
                    val baseNetSaleLitres = msNetSale + hsdNetSale

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = LanguageManager.totalSalesMsHsd,
                            style = Modifier.testTag("ms_hsd_sales_title_text").let { 
                                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            },
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "₹ " + formatDouble(baseSalesValue),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("ms_hsd_sales_total_display")
                        )
                    }

                    // MS Petrol Row - Designed clearly and with high contrast
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "MS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                            Column {
                                Text(
                                    text = "MS Petrol Sales",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${formatDouble(msNetSale)} L @ ₹${formatDouble(msRate)}/L",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "₹ " + formatDouble(msSalesAmount),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // HSD Diesel Row - Designed clearly and with high contrast
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.tertiary,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "HSD",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiary
                                    )
                                )
                            }
                            Column {
                                Text(
                                    text = "HSD Diesel Sales",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${formatDouble(hsdNetSale)} L @ ₹${formatDouble(hsdRate)}/L",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "₹ " + formatDouble(hsdSalesAmount),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
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
                            text = formatDouble(baseNetSaleLitres) + " L",
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sales Grand Total",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "₹ " + formatDouble(grandTotal),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("ms_grand_total_display")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))

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
                        
                        TwoNozzleDensityInputField(
                            value = phonePeValue,
                            onValueChange = { phonePeValue = sanitizeInput(it) },
                            placeholder = "₹ 0",
                            modifier = Modifier
                                .width(120.dp)
                                .testTag("two_nozzle_phonepe_input")
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
                        
                        TwoNozzleDensityInputField(
                            value = cardsValue,
                            onValueChange = { cardsValue = sanitizeInput(it) },
                            placeholder = "₹ 0",
                            modifier = Modifier
                                .width(120.dp)
                                .testTag("two_nozzle_cards_input")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))

                    // Cash Submitted input row
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
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Cash Submitted",
                                tint = Color(0xFF1A1A1A),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = LanguageManager.translate("Cash Submitted (₹)", "जमा किया गया नकद (₹)"),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1A1A1A)
                            )
                        }
                        
                        TwoNozzleDensityInputField(
                            value = cashSubmittedValue,
                            onValueChange = { cashSubmittedValue = sanitizeInput(it) },
                            placeholder = "₹ 0",
                            modifier = Modifier
                                .width(120.dp)
                                .testTag("two_nozzle_cash_submitted_input")
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
                                style = MaterialTheme.typography.bodyMedium,
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
                        VoiceOutlinedTextField(
                            value = tempKharchDesc,
                            onValueChange = { tempKharchDesc = it },
                            placeholder = { Text("Details (e.g., Tea, Staff)", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.5f)
                                .height(50.dp)
                                .testTag("two_nozzle_kharch_desc_input"),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2D2D2D),
                                unfocusedBorderColor = Color(0xFF2D2D2D).copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        VoiceOutlinedTextField(
                            value = tempKharchAmount,
                            onValueChange = { tempKharchAmount = sanitizeInput(it) },
                            placeholder = { Text("₹ Amt", fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("two_nozzle_kharch_amt_input"),
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
                                .testTag("two_nozzle_add_kharch_button")
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
                                    OutlinedTextField(
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
                                                if (!udhariNamesList.contains(name)) {
                                                    val newList = udhariNamesList + name
                                                    udhariNamesList = newList
                                                    coroutineScope.launch {
                                                        com.example.database.FirestoreRepository.saveUdhariNames(adminPhone, newList)
                                                    }
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
                            OutlinedTextField(
                                value = tempUdharName.ifEmpty { "Select Udhari" },
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Select Udhari", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("two_nozzle_udhar_name_input"),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = {
                                    IconButton(onClick = { udharNameDropdownExpanded = true }) {
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
                                    .clickable { udharNameDropdownExpanded = true }
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
                                .testTag("two_nozzle_udhar_receipt_input"),
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var productDropdownExpanded by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.weight(1.2f)) {
                            OutlinedTextField(
                                value = tempUdharProduct.ifEmpty { "Select Product" },
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Product", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("two_nozzle_udhar_product_input"),
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
                                listOf("MS", "HSD", "Cash").forEach { prod ->
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
                                                val newRate = when (prod) {
                                                    "MS" -> msRateValue
                                                    "HSD" -> hsdRateValue
                                                    else -> ""
                                                }
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
                                .testTag("two_nozzle_udhar_litres_input"),
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
                                .testTag("two_nozzle_udhar_rate_input"),
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
                                .testTag("two_nozzle_udhar_desc_input"),
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
                                .testTag("two_nozzle_udhar_amt_input"),
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
                                    tempUdharProduct = "MS"
                                    tempUdharLitres = ""
                                    tempUdharRate = ""
                                    tempUdharDesc = ""
                                    tempUdharAmount = ""
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF333333), shape = RoundedCornerShape(10.dp))
                                .testTag("two_nozzle_add_udhar_button")
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

            // PHYSICAL CASH COUNTER & TALLY SHEET
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "PHYSICAL CASH COUNTER & TALLY",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Reconciliation summary of flows
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Base Net Cash Balance", style = MaterialTheme.typography.bodyMedium)
                            Text("₹ " + formatDouble(finalNetCash), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                        }
                    }

                    // Currency Notes Inputs Matrix
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Physical Currency Count (Denominations)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val denoFieldsList = listOf(
                            Triple("₹ 500 notes", notes500, { v: String -> notes500 = sanitizeInteger(v) }),
                            Triple("₹ 200 notes", notes200, { v: String -> notes200 = sanitizeInteger(v) }),
                            Triple("₹ 100 notes", notes100, { v: String -> notes100 = sanitizeInteger(v) }),
                            Triple("₹ 50 notes", notes50, { v: String -> notes50 = sanitizeInteger(v) }),
                            Triple("₹ 20 notes", notes20, { v: String -> notes20 = sanitizeInteger(v) }),
                            Triple("₹ 10 notes", notes10, { v: String -> notes10 = sanitizeInteger(v) }),
                            Triple("₹ 5 notes", notes5, { v: String -> notes5 = sanitizeInteger(v) })
                        )

                        denoFieldsList.forEach { (label, valueState, onValChange) ->
                            val multiplier = label.replace("₹ ", "").replace(" notes", "").toIntOrNull() ?: 0
                            val count = valueState.toIntOrNull() ?: 0
                            val rowTotal = count * multiplier

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "₹ $multiplier",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.width(55.dp)
                                )
                                Text(
                                    text = "x",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                OutlinedTextField(
                                    value = valueState,
                                    onValueChange = onValChange,
                                    placeholder = { Text("0", fontSize = 12.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .width(90.dp)
                                        .height(48.dp)
                                        .testTag("two_nozzle_deno_${multiplier}"),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = TextAlign.Center
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Text(
                                    text = " = ₹ ${formatDouble(rowTotal.toDouble())}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        // Coins & Misc Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Coins/Misc",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.width(65.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            OutlinedTextField(
                                value = coinsInput,
                                onValueChange = { coinsInput = sanitizeInput(it) },
                                placeholder = { Text("0", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(48.dp)
                                    .testTag("two_nozzle_deno_coins"),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Text(
                                text = " = ₹ ${formatDouble(coinsAmt)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    // Real-time Actual vs Expected and shortage/over check
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = when {
                                    cashDiscrepancy == 0.0 -> Color(0xFFFAFAFA)
                                    cashDiscrepancy < 0.0 -> Color(0xFFFAFAFA)
                                    else -> Color(0xFFFAFAFA)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = when {
                                    cashDiscrepancy == 0.0 -> Color(0xFFD3D3D3)
                                    cashDiscrepancy < 0.0 -> Color(0xFFD3D3D3)
                                    else -> Color(0xFFD3D3D3)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Actual Cash Collected:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = Color.Black
                            )
                            Text(
                                text = "₹ " + formatDouble(actualCashInHand),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            )
                        }

                        if (cashSubmittedAmount > 0.0) {
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Cash Submitted:",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = Color.Black
                                )
                                Text(
                                    text = "₹ " + formatDouble(cashSubmittedAmount),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tally Difference:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.Black
                            )
                            val diffText = when {
                                cashDiscrepancy == 0.0 -> "₹ 0 (Perfect Match)"
                                cashDiscrepancy < 0.0 -> "- ₹ " + formatDouble(-cashDiscrepancy) + " [Shortage]"
                                else -> "+ ₹ " + formatDouble(cashDiscrepancy) + " [Surplus]"
                            }
                            
                            // Dynamic font size calculation to prevent overflowing
                            val baseFontSize = 15.sp
                            val dynamicFontSize = when {
                                diffText.length > 30 -> 10.sp
                                diffText.length > 25 -> 11.sp
                                diffText.length > 20 -> 12.sp
                                diffText.length > 15 -> 13.sp
                                else -> baseFontSize
                            }

                            Text(
                                text = diffText,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = dynamicFontSize,
                                    color = when {
                                        cashDiscrepancy == 0.0 -> Color(0xFF15803D) // green-700
                                        cashDiscrepancy < 0.0 -> Color(0xFFB91C1C) // red-700
                                        else -> Color(0xFFC2410C) // orange-700
                                    }
                                ),
                                maxLines = 1
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
                        
                        // Execute Save logic
                        val report = getSummaryText()
                        val htmlReport = generateA4HtmlReport()
                        historyViewModel.saveAudit(
                            adminPhone = adminPhone,
                            date = date,
                            caName = caName,
                            meterNo = meterNo,
                            auditType = "Shift Report & Tally",
                            summaryText = report,
                            htmlContent = htmlReport,
                            cashSubmitted = cashSubmittedAmount,
                            actualCashCollected = actualCashInHand
                        )

                        // Save individual MS & HSD nozzle readings to the database
                        val n1Op = nozzle1Opening.toDoubleOrNull() ?: 0.0
                        val n1Cl = nozzle1Closing.toDoubleOrNull() ?: 0.0
                        val msReading = MsNozzleReading(
                            ownerAdminPhone = adminPhone,
                            nozzleLabel = "Noz 1 (MS)",
                            openingReading = minOf(n1Op, n1Cl),
                            closingReading = maxOf(n1Op, n1Cl),
                            testing = msTestingValue.toDoubleOrNull() ?: 0.0,
                            caName = caName,
                            phone = phone,
                            udhar = udharList.sumOf { it.amount },
                            kharch = kharchList.sumOf { it.second },
                            udhariJama = udhariJamaList.sumOf { it.amount },
                            msSales = msSalesAmount,
                            hsdSales = 0.0,
                            date = date
                        )
                        historyViewModel.insertMsNozzleReading(msReading)

                        if (activeNozzleCount >= 2) {
                            val n2Op = nozzle2Opening.toDoubleOrNull() ?: 0.0
                            val n2Cl = nozzle2Closing.toDoubleOrNull() ?: 0.0
                            val hsdReading = HsdNozzleReading(
                                ownerAdminPhone = adminPhone,
                                nozzleLabel = "Noz 2 (HSD)",
                                openingReading = minOf(n2Op, n2Cl),
                                closingReading = maxOf(n2Op, n2Cl),
                                testing = hsdTestingValue.toDoubleOrNull() ?: 0.0,
                                caName = caName,
                                phone = phone,
                                udhar = udharList.sumOf { it.amount },
                                kharch = kharchList.sumOf { it.second },
                                udhariJama = udhariJamaList.sumOf { it.amount },
                                msSales = 0.0,
                                hsdSales = hsdSalesAmount,
                                date = date
                            )
                            historyViewModel.insertHsdNozzleReading(hsdReading)
                        }

                        Toast.makeText(context, "Report Saved to History!", Toast.LENGTH_LONG).show()

                        // Reset all fields
                        nozzle1Closing = ""
                        nozzle1Opening = ""
                        msRateValue = ""
                        msTestingValue = ""
                        nozzle2Closing = ""
                        nozzle2Opening = ""
                        hsdRateValue = ""
                        hsdTestingValue = ""
                        phonePeValue = ""
                        cardsValue = ""
                        cashSubmittedValue = ""
                        notes500 = ""
                        notes200 = ""
                        notes100 = ""
                        notes50 = ""
                        notes20 = ""
                        notes10 = ""
                        notes5 = ""
                        coinsInput = ""
                        udhariJamaRawString = ""
                        tempUdhariJamaDesc = ""
                        tempUdhariJamaAmount = ""
                        kharchRawString = ""
                        tempKharchDesc = ""
                        tempKharchAmount = ""
                        udharRawString = ""
                        tempUdharName = ""
                        tempUdharReceiptNo = ""
                        tempUdharProduct = "MS"
                        tempUdharLitres = ""
                        tempUdharRate = ""
                        tempUdharDesc = ""
                        tempUdharAmount = ""

                        onSaveSuccess?.invoke()
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



