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
                    text = "ACTIVE AUDIT",
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

            // Sub-Results Row (Actual sale, sales amount)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ACTUAL SALE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = formatDouble(actualSale) + " L",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color.Black,
                        modifier = Modifier.testTag(actualSaleTag)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "SALES AMOUNT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            color = Color(0xFF222222),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Text(
                        text = "₹ " + formatDouble(salesAmount),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF222222)
                        ),
                        modifier = Modifier.testTag(salesAmountTag)
                    )
                }
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
    msNozzleCount: Int = 2,
    hsdNozzleCount: Int = 2,
    msNozzleLabels: List<String> = emptyList(),
    hsdNozzleLabels: List<String> = emptyList(),
    phone: String = "",
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val historyViewModel: HistoryViewModel = viewModel()
    var msClosing1 by rememberSaveable { mutableStateOf("") }
    var msOpening1 by rememberSaveable { mutableStateOf("") }
    var msClosing2 by rememberSaveable { mutableStateOf("") }
    var msOpening2 by rememberSaveable { mutableStateOf("") }
    var msClosing3 by rememberSaveable { mutableStateOf("") }
    var msOpening3 by rememberSaveable { mutableStateOf("") }
    var msClosing4 by rememberSaveable { mutableStateOf("") }
    var msOpening4 by rememberSaveable { mutableStateOf("") }
    var msClosing5 by rememberSaveable { mutableStateOf("") }
    var msOpening5 by rememberSaveable { mutableStateOf("") }
    var msClosing6 by rememberSaveable { mutableStateOf("") }
    var msOpening6 by rememberSaveable { mutableStateOf("") }
    var msClosing7 by rememberSaveable { mutableStateOf("") }
    var msOpening7 by rememberSaveable { mutableStateOf("") }
    var msClosing8 by rememberSaveable { mutableStateOf("") }
    var msOpening8 by rememberSaveable { mutableStateOf("") }
    var msClosing9 by rememberSaveable { mutableStateOf("") }
    var msOpening9 by rememberSaveable { mutableStateOf("") }
    var msClosing10 by rememberSaveable { mutableStateOf("") }
    var msOpening10 by rememberSaveable { mutableStateOf("") }

    var hsdClosing1 by rememberSaveable { mutableStateOf("") }
    var hsdOpening1 by rememberSaveable { mutableStateOf("") }
    var hsdClosing2 by rememberSaveable { mutableStateOf("") }
    var hsdOpening2 by rememberSaveable { mutableStateOf("") }
    var hsdClosing3 by rememberSaveable { mutableStateOf("") }
    var hsdOpening3 by rememberSaveable { mutableStateOf("") }
    var hsdClosing4 by rememberSaveable { mutableStateOf("") }
    var hsdOpening4 by rememberSaveable { mutableStateOf("") }
    var hsdClosing5 by rememberSaveable { mutableStateOf("") }
    var hsdOpening5 by rememberSaveable { mutableStateOf("") }
    var hsdClosing6 by rememberSaveable { mutableStateOf("") }
    var hsdOpening6 by rememberSaveable { mutableStateOf("") }
    var hsdClosing7 by rememberSaveable { mutableStateOf("") }
    var hsdOpening7 by rememberSaveable { mutableStateOf("") }
    var hsdClosing8 by rememberSaveable { mutableStateOf("") }
    var hsdOpening8 by rememberSaveable { mutableStateOf("") }
    var hsdClosing9 by rememberSaveable { mutableStateOf("") }
    var hsdOpening9 by rememberSaveable { mutableStateOf("") }
    var hsdClosing10 by rememberSaveable { mutableStateOf("") }
    var hsdOpening10 by rememberSaveable { mutableStateOf("") }

    // Common testing states
    var msTestingValue by rememberSaveable { mutableStateOf("") }
    var hsdTestingValue by rememberSaveable { mutableStateOf("") }

    // Common dynamic rates per litre
    var msRateValue by rememberSaveable { mutableStateOf("") }
    var hsdRateValue by rememberSaveable { mutableStateOf("") }

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
    val sharedPrefs = remember(context) { context.getSharedPreferences("pump_manager_prefs", android.content.Context.MODE_PRIVATE) }
    var udhariNamesList by remember {
        mutableStateOf(
            run {
                val saved = sharedPrefs.getString("udhari_names_list", "") ?: ""
                val list = if (saved.isBlank()) {
                    listOf("Miscellaneous")
                } else {
                    saved.split(";").filter { it.isNotBlank() }
                }
                if (!list.contains("Miscellaneous")) {
                    listOf("Miscellaneous") + list
                } else {
                    list
                }
            }
        )
    }
    var showAddUdhariDialog by remember { mutableStateOf(false) }
    var newUdhariNameInput by remember { mutableStateOf("") }

    var showSaveConfirmDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(date) {
        val sharedPrefs = context.getSharedPreferences("pump_manager_prefs", android.content.Context.MODE_PRIVATE)
        val rateMs = sharedPrefs.getString("rate_ms_$date", "") ?: ""
        val rateHsd = sharedPrefs.getString("rate_hsd_$date", "") ?: ""
        msRateValue = rateMs
        hsdRateValue = rateHsd
    }

    LaunchedEffect(phone) {
        if (phone.isNotEmpty()) {
            for (i in 0 until msNozzleCount) {
                val label = msNozzleLabels.getOrNull(i) ?: "MS Nozzle ${i + 1}"
                val lastMs = historyViewModel.getLatestMsReadingForNozzle(label, phone)
                val initialVal = historyViewModel.getInitialReadingForNozzle(label, phone)
                val openingVal = lastMs?.closingReading?.toString() ?: if (initialVal % 1.0 == 0.0) initialVal.toLong().toString() else initialVal.toString()
                when (i) {
                    0 -> msOpening1 = openingVal
                    1 -> msOpening2 = openingVal
                    2 -> msOpening3 = openingVal
                    3 -> msOpening4 = openingVal
                    4 -> msOpening5 = openingVal
                    5 -> msOpening6 = openingVal
                    6 -> msOpening7 = openingVal
                    7 -> msOpening8 = openingVal
                    8 -> msOpening9 = openingVal
                    9 -> msOpening10 = openingVal
                }
            }

            for (j in 0 until hsdNozzleCount) {
                val label = hsdNozzleLabels.getOrNull(j) ?: "HSD Nozzle ${j + 1}"
                val lastHsd = historyViewModel.getLatestHsdReadingForNozzle(label, phone)
                val initialVal = historyViewModel.getInitialReadingForNozzle(label, phone)
                val openingVal = lastHsd?.closingReading?.toString() ?: if (initialVal % 1.0 == 0.0) initialVal.toLong().toString() else initialVal.toString()
                when (j) {
                    0 -> hsdOpening1 = openingVal
                    1 -> hsdOpening2 = openingVal
                    2 -> hsdOpening3 = openingVal
                    3 -> hsdOpening4 = openingVal
                    4 -> hsdOpening5 = openingVal
                    5 -> hsdOpening6 = openingVal
                    6 -> hsdOpening7 = openingVal
                    7 -> hsdOpening8 = openingVal
                    8 -> hsdOpening9 = openingVal
                    9 -> hsdOpening10 = openingVal
                }
            }
        }
    }

    // Helpers to easily access they by index
    val msClosings = listOf(msClosing1, msClosing2, msClosing3, msClosing4, msClosing5, msClosing6, msClosing7, msClosing8, msClosing9, msClosing10)
    val msOpenings = listOf(msOpening1, msOpening2, msOpening3, msOpening4, msOpening5, msOpening6, msOpening7, msOpening8, msOpening9, msOpening10)
    val hsdClosings = listOf(hsdClosing1, hsdClosing2, hsdClosing3, hsdClosing4, hsdClosing5, hsdClosing6, hsdClosing7, hsdClosing8, hsdClosing9, hsdClosing10)
    val hsdOpenings = listOf(hsdOpening1, hsdOpening2, hsdOpening3, hsdOpening4, hsdOpening5, hsdOpening6, hsdOpening7, hsdOpening8, hsdOpening9, hsdOpening10)

    fun updateMsClosing(index: Int, value: String) {
        when (index) {
            0 -> msClosing1 = value
            1 -> msClosing2 = value
            2 -> msClosing3 = value
            3 -> msClosing4 = value
            4 -> msClosing5 = value
            5 -> msClosing6 = value
            6 -> msClosing7 = value
            7 -> msClosing8 = value
            8 -> msClosing9 = value
            9 -> msClosing10 = value
        }
    }

    fun updateMsOpening(index: Int, value: String) {
        when (index) {
            0 -> msOpening1 = value
            1 -> msOpening2 = value
            2 -> msOpening3 = value
            3 -> msOpening4 = value
            4 -> msOpening5 = value
            5 -> msOpening6 = value
            6 -> msOpening7 = value
            7 -> msOpening8 = value
            8 -> msOpening9 = value
            9 -> msOpening10 = value
        }
    }

    fun updateHsdClosing(index: Int, value: String) {
        when (index) {
            0 -> hsdClosing1 = value
            1 -> hsdClosing2 = value
            2 -> hsdClosing3 = value
            3 -> hsdClosing4 = value
            4 -> hsdClosing5 = value
            5 -> hsdClosing6 = value
            6 -> hsdClosing7 = value
            7 -> hsdClosing8 = value
            8 -> hsdClosing9 = value
            9 -> hsdClosing10 = value
        }
    }

    fun updateHsdOpening(index: Int, value: String) {
        when (index) {
            0 -> hsdOpening1 = value
            1 -> hsdOpening2 = value
            2 -> hsdOpening3 = value
            3 -> hsdOpening4 = value
            4 -> hsdOpening5 = value
            5 -> hsdOpening6 = value
            6 -> hsdOpening7 = value
            7 -> hsdOpening8 = value
            8 -> hsdOpening9 = value
            9 -> hsdOpening10 = value
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
    val activeMsNozzles = (0 until msNozzleCount).map { i ->
        val closing = msClosings[i].toDoubleOrNull() ?: 0.0
        val opening = msOpenings[i].toDoubleOrNull() ?: 0.0
        val sale = closing - opening
        Triple(closing, opening, sale)
    }
    val msGrossSale = activeMsNozzles.sumOf { it.third }
    val msRate = msRateValue.toDoubleOrNull() ?: 0.0
    val msTesting = msTestingValue.toDoubleOrNull() ?: 0.0
    val msNetSale = msGrossSale - msTesting
    val msSalesAmount = msNetSale * msRate

    val activeHsdNozzles = (0 until hsdNozzleCount).map { i ->
        val closing = hsdClosings[i].toDoubleOrNull() ?: 0.0
        val opening = hsdOpenings[i].toDoubleOrNull() ?: 0.0
        val sale = closing - opening
        Triple(closing, opening, sale)
    }
    val hsdGrossSale = activeHsdNozzles.sumOf { it.third }
    val hsdRate = hsdRateValue.toDoubleOrNull() ?: 0.0
    val hsdTesting = hsdTestingValue.toDoubleOrNull() ?: 0.0
    val hsdNetSale = hsdGrossSale - hsdTesting
    val hsdSalesAmount = hsdNetSale * hsdRate

    val grandActualTotal = msGrossSale + hsdGrossSale
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
        msClosing1 = ""; msOpening1 = ""; msClosing2 = ""; msOpening2 = ""; msClosing3 = ""; msOpening3 = ""; msClosing4 = ""; msOpening4 = ""; msClosing5 = ""; msOpening5 = ""; msClosing6 = ""; msOpening6 = ""; msClosing7 = ""; msOpening7 = ""; msClosing8 = ""; msOpening8 = ""; msClosing9 = ""; msOpening9 = ""; msClosing10 = ""; msOpening10 = ""
        hsdClosing1 = ""; hsdOpening1 = ""; hsdClosing2 = ""; hsdOpening2 = ""; hsdClosing3 = ""; hsdOpening3 = ""; hsdClosing4 = ""; hsdOpening4 = ""; hsdClosing5 = ""; hsdOpening5 = ""; hsdClosing6 = ""; hsdOpening6 = ""; hsdClosing7 = ""; hsdOpening7 = ""; hsdClosing8 = ""; hsdOpening8 = ""; hsdClosing9 = ""; hsdOpening9 = ""; hsdClosing10 = ""; hsdOpening10 = ""
        msRateValue = ""
        hsdRateValue = ""
        msTestingValue = ""
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
        val sb = java.lang.StringBuilder()
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
        
        activeMsNozzles.forEachIndexed { index, (closing, opening, sale) ->
            val label = msNozzleLabels.getOrNull(index) ?: "MS Noz ${index + 1}"
            sb.append("$label: Op:${formatDouble(opening)} Cl:${formatDouble(closing)}\n")
            sb.append("  Gross Sale: ${formatDouble(sale)} L\n")
        }
        
        sb.append("MS TOTALS:\n")
        sb.append("  MS Gross Sale: ${formatDouble(msGrossSale)} L\n")
        if (msTesting > 0.0) {
            sb.append("  MS Testing Deduction: -${formatDouble(msTesting)} L\n")
        }
        sb.append("  MS Net Sales: ${formatDouble(msNetSale)} L @ ₹${formatDouble(msRate)}/L\n")
        sb.append("  MS Sales Value: ₹${formatDouble(msSalesAmount)}\n\n")
        
        activeHsdNozzles.forEachIndexed { index, (closing, opening, sale) ->
            val label = hsdNozzleLabels.getOrNull(index) ?: "HSD Noz ${index + 1}"
            sb.append("$label: Op:${formatDouble(opening)} Cl:${formatDouble(closing)}\n")
            sb.append("  Gross Sale: ${formatDouble(sale)} L\n")
        }
        
        sb.append("HSD TOTALS:\n")
        sb.append("  HSD Gross Sale: ${formatDouble(hsdGrossSale)} L\n")
        if (hsdTesting > 0.0) {
            sb.append("  HSD Testing Deduction: -${formatDouble(hsdTesting)} L\n")
        }
        sb.append("  HSD Net Sales: ${formatDouble(hsdNetSale)} L @ ₹${formatDouble(hsdRate)}/L\n")
        sb.append("  HSD Sales Value: ₹${formatDouble(hsdSalesAmount)}\n")
        
        sb.append("-----------------------------\n")
        sb.append("Grand Total Vol (Gross): ${formatDouble(grandActualTotal)} L\n")
        if (totalTesting > 0.0) {
            sb.append("Total Testing Deduction: -${formatDouble(totalTesting)} L\n")
        }
        sb.append("Net Sales Volume (Final): ${formatDouble(finalResult)} L\n")
        sb.append("-----------------------------\n")
        sb.append("MS Total Sales Value: ₹${formatDouble(msSalesAmount)}\n")
        sb.append("HSD Total Sales Value: ₹${formatDouble(hsdSalesAmount)}\n")
        sb.append("Total Sales (MS+HSD): ₹${formatDouble(msSalesAmount + hsdSalesAmount)}\n")
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

        val sharedPrefs = context.getSharedPreferences("pump_manager_prefs", android.content.Context.MODE_PRIVATE)
        val densityMs = sharedPrefs.getString("density_ms_$date", "") ?: ""
        val densityHsd = sharedPrefs.getString("density_hsd_$date", "") ?: ""
        val stockMs = sharedPrefs.getString("stock_ms_$date", "") ?: ""
        val stockHsd = sharedPrefs.getString("stock_hsd_$date", "") ?: ""
        val receiptMs = sharedPrefs.getString("receipt_ms_$date", "") ?: ""
        val receiptHsd = sharedPrefs.getString("receipt_hsd_$date", "") ?: ""
        
        val rMs = if (msRateValue.isNotBlank()) "₹$msRateValue" else (sharedPrefs.getString("rate_ms_$date", "")?.let { if (it.isNotBlank()) "₹$it" else "N/A" } ?: "N/A")
        val rHsd = if (hsdRateValue.isNotBlank()) "₹$hsdRateValue" else (sharedPrefs.getString("rate_hsd_$date", "")?.let { if (it.isNotBlank()) "₹$it" else "N/A" } ?: "N/A")

        val msRowsHtml = activeMsNozzles.mapIndexed { index, (closing, opening, sale) ->
            val label = msNozzleLabels.getOrNull(index) ?: "Noz ${index + 1} (MS)"
            """
            <tr>
                <td>$label</td>
                <td>${formatDouble(opening)}</td>
                <td>${formatDouble(closing)}</td>
                <td class="bold">${formatDouble(sale)} L</td>
                <td class="num">&mdash;</td>
            </tr>
            """.trimIndent()
        }.joinToString("")

        val hsdRowsHtml = activeHsdNozzles.mapIndexed { index, (closing, opening, sale) ->
            val label = hsdNozzleLabels.getOrNull(index) ?: "Noz ${index + 1} (HSD)"
            """
            <tr>
                <td>$label</td>
                <td>${formatDouble(opening)}</td>
                <td>${formatDouble(closing)}</td>
                <td class="bold">${formatDouble(sale)} L</td>
                <td class="num">&mdash;</td>
            </tr>
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
                                        <th style="text-align:right;">Value</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    $msRowsHtml
                                    $hsdRowsHtml
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
                                        <td class="num">${formatDouble(msGrossSale)} L</td>
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
                                        <td class="num">${formatDouble(hsdGrossSale)} L</td>
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
                            .testTag("four_nozzle_reset_action_bar_button"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            for (i in 0 until msNozzleCount) {
                                val closingStr = msClosings.getOrNull(i)?.trim().orEmpty()
                                if (closingStr.isEmpty()) {
                                    val label = msNozzleLabels.getOrNull(i) ?: "MS Nozzle ${i + 1}"
                                    Toast.makeText(
                                        context,
                                        LanguageManager.translate(
                                            "Please enter closing reading for $label!",
                                            "कृपया $label के लिए अंतिम रीडिंग दर्ज करें!"
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }
                            }
                            for (j in 0 until hsdNozzleCount) {
                                val closingStr = hsdClosings.getOrNull(j)?.trim().orEmpty()
                                if (closingStr.isEmpty()) {
                                    val label = hsdNozzleLabels.getOrNull(j) ?: "HSD Nozzle ${j + 1}"
                                    Toast.makeText(
                                        context,
                                        LanguageManager.translate(
                                            "Please enter closing reading for $label!",
                                            "कृपया $label के लिए अंतिम रीडिंग दर्ज करें!"
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
                            .testTag("four_nozzle_save_audit_button"),
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

            if (msNozzleCount > 0) {
                // Section Title for MS Nozzles
                Text(
                    text = "Motor Spirit (MS) Nozzles ($msNozzleCount)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                (0 until msNozzleCount).forEach { index ->
                    val closing = msClosings[index]
                    val opening = msOpenings[index]
                    val triple = activeMsNozzles.getOrNull(index) ?: Triple(0.0, 0.0, 0.0)
                    val actualSale = triple.third
                    val salesAmount = actualSale * (msRateValue.toDoubleOrNull() ?: 0.0)

                    FourNozzleCard(
                        title = msNozzleLabels.getOrNull(index) ?: "MS Nozzle ${index + 1}",
                        closingVal = closing,
                        openingVal = opening,
                        actualSale = actualSale,
                        salesAmount = salesAmount,
                        onClosingChanged = { updateMsClosing(index, sanitizeInput(it)) },
                        onOpeningChanged = { updateMsOpening(index, sanitizeInput(it)) },
                        closingTag = "ms_nozzle_${index + 1}_closing_input",
                        openingTag = "ms_nozzle_${index + 1}_opening_input",
                        actualSaleTag = "ms_nozzle_${index + 1}_sale_display",
                        salesAmountTag = "ms_nozzle_${index + 1}_sales_amount_display",
                        badgeColor = MaterialTheme.colorScheme.primary,
                        badgeTextColor = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // MS Testing & Total Summary
                ProductTestingSummaryCard(
                    title = "Motor Spirit (MS)",
                    grossSale = msGrossSale,
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
            }

            if (hsdNozzleCount > 0) {
                // Section Title for HSD Nozzles
                Text(
                    text = "High Speed Diesel (HSD) Nozzles ($hsdNozzleCount)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )

                (0 until hsdNozzleCount).forEach { index ->
                    val closing = hsdClosings[index]
                    val opening = hsdOpenings[index]
                    val triple = activeHsdNozzles.getOrNull(index) ?: Triple(0.0, 0.0, 0.0)
                    val actualSale = triple.third
                    val salesAmount = actualSale * (hsdRateValue.toDoubleOrNull() ?: 0.0)

                    FourNozzleCard(
                        title = hsdNozzleLabels.getOrNull(index) ?: "HSD Nozzle ${index + 1}",
                        closingVal = closing,
                        openingVal = opening,
                        actualSale = actualSale,
                        salesAmount = salesAmount,
                        onClosingChanged = { updateHsdClosing(index, sanitizeInput(it)) },
                        onOpeningChanged = { updateHsdOpening(index, sanitizeInput(it)) },
                        closingTag = "hsd_nozzle_${index + 1}_closing_input",
                        openingTag = "hsd_nozzle_${index + 1}_opening_input",
                        actualSaleTag = "hsd_nozzle_${index + 1}_sale_display",
                        salesAmountTag = "hsd_nozzle_${index + 1}_sales_amount_display",
                        badgeColor = MaterialTheme.colorScheme.tertiary,
                        badgeTextColor = MaterialTheme.colorScheme.onTertiary
                    )
                }

                // HSD Testing & Total Summary
                ProductTestingSummaryCard(
                    title = "High Speed Diesel (HSD)",
                    grossSale = hsdGrossSale,
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
                                                    sharedPrefs.edit().putString("udhari_names_list", newList.joinToString(";")).apply()
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
                                    .testTag("four_nozzle_udhari_jama_name_input"),
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

                    if (msNozzleCount > 0) {
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
                    }

                    if (hsdNozzleCount > 0) {
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
                            modifier = Modifier.testTag("four_nozzle_grand_total_display")
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
                        
                        FourNozzleDensityInputField(
                            value = cashSubmittedValue,
                            onValueChange = { cashSubmittedValue = sanitizeInput(it) },
                            placeholder = "₹ 0",
                            modifier = Modifier
                                .width(120.dp)
                                .testTag("four_nozzle_cash_submitted_input")
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
                                .testTag("four_nozzle_kharch_desc_input"),
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
                                                    sharedPrefs.edit().putString("udhari_names_list", newList.joinToString(";")).apply()
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
                                    .testTag("four_nozzle_udhar_name_input"),
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
                                        .testTag("four_nozzle_deno_${multiplier}"),
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
                                    .testTag("four_nozzle_deno_coins"),
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
                            date = date,
                            caName = caName,
                            meterNo = meterNo,
                            auditType = "Shift Report & Tally",
                            summaryText = report,
                            htmlContent = htmlReport,
                            cashSubmitted = cashSubmittedAmount,
                            actualCashCollected = actualCashInHand
                        )

                        // Save individual MS nozzle readings to the database
                        val msReadingsToSave = (0 until msNozzleCount).map { i ->
                            val label = msNozzleLabels.getOrNull(i) ?: "MS Nozzle ${i + 1}"
                            val opening = msOpenings.getOrNull(i)?.toDoubleOrNull() ?: 0.0
                            val closing = msClosings.getOrNull(i)?.toDoubleOrNull() ?: 0.0
                            MsNozzleReading(
                                nozzleLabel = label,
                                openingReading = minOf(opening, closing),
                                closingReading = maxOf(opening, closing),
                                testing = msTesting,
                                caName = caName,
                                phone = phone,
                                udhar = totalUdhar,
                                kharch = totalKharch,
                                udhariJama = totalUdhariJama,
                                msSales = msSalesAmount,
                                hsdSales = 0.0,
                                date = date
                            )
                        }
                        historyViewModel.insertMsNozzleReadings(msReadingsToSave)

                        // Save individual HSD nozzle readings to the database
                        val hsdReadingsToSave = (0 until hsdNozzleCount).map { j ->
                            val label = hsdNozzleLabels.getOrNull(j) ?: "HSD Nozzle ${j + 1}"
                            val opening = hsdOpenings.getOrNull(j)?.toDoubleOrNull() ?: 0.0
                            val closing = hsdClosings.getOrNull(j)?.toDoubleOrNull() ?: 0.0
                            HsdNozzleReading(
                                nozzleLabel = label,
                                openingReading = minOf(opening, closing),
                                closingReading = maxOf(opening, closing),
                                testing = hsdTesting,
                                caName = caName,
                                phone = phone,
                                udhar = totalUdhar,
                                kharch = totalKharch,
                                udhariJama = totalUdhariJama,
                                msSales = 0.0,
                                hsdSales = hsdSalesAmount,
                                date = date
                            )
                        }
                        historyViewModel.insertHsdNozzleReadings(hsdReadingsToSave)

                        Toast.makeText(context, "Report Saved to History!", Toast.LENGTH_LONG).show()

                        // Reset all input fields
                        msClosing1 = ""
                        msOpening1 = ""
                        msClosing2 = ""
                        msOpening2 = ""
                        msClosing3 = ""
                        msOpening3 = ""
                        msClosing4 = ""
                        msOpening4 = ""
                        msClosing5 = ""
                        msOpening5 = ""
                        msClosing6 = ""
                        msOpening6 = ""
                        msClosing7 = ""
                        msOpening7 = ""
                        msClosing8 = ""
                        msOpening8 = ""
                        msClosing9 = ""
                        msOpening9 = ""
                        msClosing10 = ""
                        msOpening10 = ""

                        hsdClosing1 = ""
                        hsdOpening1 = ""
                        hsdClosing2 = ""
                        hsdOpening2 = ""
                        hsdClosing3 = ""
                        hsdOpening3 = ""
                        hsdClosing4 = ""
                        hsdOpening4 = ""
                        hsdClosing5 = ""
                        hsdOpening5 = ""
                        hsdClosing6 = ""
                        hsdOpening6 = ""
                        hsdClosing7 = ""
                        hsdOpening7 = ""
                        hsdClosing8 = ""
                        hsdOpening8 = ""
                        hsdClosing9 = ""
                        hsdOpening9 = ""
                        hsdClosing10 = ""
                        hsdOpening10 = ""

                        msTestingValue = ""
                        hsdTestingValue = ""
                        msRateValue = ""
                        hsdRateValue = ""
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


