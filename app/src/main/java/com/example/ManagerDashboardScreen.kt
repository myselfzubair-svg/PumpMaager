package com.example

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerDashboardScreen(
    onBack: () -> Unit,
    onNavigateToDailyAudit: (String) -> Unit,
    onNavigateToDailySalesReport: () -> Unit,
    onNavigateToTtReceiptEntry: () -> Unit,
    onNavigateToTtEntryReport: () -> Unit,
    onNavigateToMonthlyExpensesReport: () -> Unit,
    onNavigateToMonthlyCreditReport: () -> Unit,
    onNavigateToMonthlyUdhariJamaReport: () -> Unit,
    onNavigateToCustomerUdhariLedgerReport: () -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("pump_manager_prefs", Context.MODE_PRIVATE) }

    // Selected date for manager configurations
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

    // Input States (Reload when date changes)
    var densityMs by remember { mutableStateOf("") }
    var densityHsd by remember { mutableStateOf("") }
    var rateMs by remember { mutableStateOf("") }
    var rateHsd by remember { mutableStateOf("") }
    var stockMs by remember { mutableStateOf("") }
    var stockHsd by remember { mutableStateOf("") }
    var receiptMs by remember { mutableStateOf("") }
    var receiptHsd by remember { mutableStateOf("") }

    // Load saved values helper
    fun loadSavedData(date: String) {
        densityMs = sharedPrefs.getString("density_ms_$date", "") ?: ""
        densityHsd = sharedPrefs.getString("density_hsd_$date", "") ?: ""
        rateMs = sharedPrefs.getString("rate_ms_$date", "") ?: ""
        rateHsd = sharedPrefs.getString("rate_hsd_$date", "") ?: ""
        stockMs = sharedPrefs.getString("stock_ms_$date", "") ?: ""
        stockHsd = sharedPrefs.getString("stock_hsd_$date", "") ?: ""
        receiptMs = sharedPrefs.getString("receipt_ms_$date", "") ?: ""
        receiptHsd = sharedPrefs.getString("receipt_hsd_$date", "") ?: ""
    }

    // Trigger load on start and date changes
    LaunchedEffect(selectedDate) {
        loadSavedData(selectedDate)
    }

    // Collapsible Section States
    var isDensityExpanded by rememberSaveable { mutableStateOf(false) }
    var isRateExpanded by rememberSaveable { mutableStateOf(false) }
    var isStockExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = LanguageManager.translate("Managers Dashboard", "मैनेजर्स डैशबोर्ड"),
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
                        modifier = Modifier.testTag("manager_dashboard_back")
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
            // DATE SELECTOR CARD
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
                                } catch (e: Exception) {
                                    // fallback to current
                                }
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
                                    text = LanguageManager.translate("Selected Configuration Date", "चयनित कॉन्फ़िगरेशन तिथि"),
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



            // DATA ENTRY SECTION (Outside Reports Center)
            item {
                Text(
                    text = LanguageManager.translate("Active Station Data Entry", "सक्रिय स्टेशन डेटा प्रविष्टि"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        ReportRowItem(
                            title = LanguageManager.translate("TT Receipt Entry", "टीटी रसीद प्रविष्टि"),
                            subtitle = LanguageManager.translate(
                                "Record tanker invoice details, pre/post density, pre/post dip levels, and shortages.",
                                "टैंकर इनवॉइस विवरण, पूर्व/पश्चात डेंसिटी, पूर्व/पश्चात डिप स्तर और कमी दर्ज करें।"
                            ),
                            icon = Icons.Default.LocalShipping,
                            iconColor = MaterialTheme.colorScheme.primary,
                            onClick = { onNavigateToTtReceiptEntry() },
                            testTag = "manager_option_tt_receipt_entry"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Morning Density
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isDensityExpanded = !isDensityExpanded }
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
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = Icons.Default.Opacity,
                                                contentDescription = "Morning Density",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = LanguageManager.translate("Morning Density", "सुबह की डेंसिटी"),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = LanguageManager.translate("Record daily fuel density", "दैनिक ईंधन डेंसिटी दर्ज करें"),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (isDensityExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Section",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(
                                visible = isDensityExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = densityMs,
                                            onValueChange = { densityMs = it },
                                            label = { Text(LanguageManager.translate("MS Density (Petrol)", "एमएस डेंसिटी (पेट्रोल)")) },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = densityHsd,
                                            onValueChange = { densityHsd = it },
                                            label = { Text(LanguageManager.translate("HSD Density (Diesel)", "एचएसडी डेंसिटी (डीजल)")) },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            sharedPrefs.edit()
                                                .putString("density_ms_$selectedDate", densityMs)
                                                .putString("density_hsd_$selectedDate", densityHsd)
                                                .apply()
                                            Toast.makeText(
                                                context,
                                                LanguageManager.translate(
                                                    "Morning Density saved successfully!",
                                                    "सुबह की डेंसिटी सफलतापूर्वक सहेजी गई!"
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            isDensityExpanded = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = "Save Icon")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = LanguageManager.translate("SAVE MORNING DENSITY", "सुबह की डेंसिटी सहेजें"),
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Daily Fuel Rates
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isRateExpanded = !isRateExpanded }
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
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = Icons.Default.CurrencyRupee,
                                                contentDescription = "Daily Fuel Rate",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = LanguageManager.translate("Daily Fuel Rates", "दैनिक ईंधन दरें"),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = LanguageManager.translate("Set fuel price per Litre", "प्रति लीटर ईंधन की कीमत निर्धारित करें"),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (isRateExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Section",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(
                                visible = isRateExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = rateMs,
                                            onValueChange = { rateMs = it },
                                            label = { Text(LanguageManager.translate("MS Petrol Rate (₹)", "एमएस पेट्रोल दर (₹)")) },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = rateHsd,
                                            onValueChange = { rateHsd = it },
                                            label = { Text(LanguageManager.translate("HSD Diesel Rate (₹)", "एचएसडी डीजल दर (₹)")) },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            sharedPrefs.edit()
                                                .putString("rate_ms_$selectedDate", rateMs)
                                                .putString("rate_hsd_$selectedDate", rateHsd)
                                                .apply()
                                            Toast.makeText(
                                                context,
                                                LanguageManager.translate(
                                                    "Daily fuel rates saved successfully!",
                                                    "दैनिक ईंधन दरें सफलतापूर्वक सहेजी गईं!"
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            isRateExpanded = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = "Save Icon")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = LanguageManager.translate("SAVE FUEL RATES", "ईंधन दरें सहेजें"),
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Stocks & Receipts
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isStockExpanded = !isStockExpanded }
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
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = Icons.Default.Storage,
                                                contentDescription = "Opening Stock",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = LanguageManager.translate("Stocks & Receipts (Litres)", "स्टॉक और रसीदें (लीटर)"),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = LanguageManager.translate("Define opening stock and receipts / new inventory", "ओपनिंग स्टॉक और रसीदें / नई इन्वेंट्री दर्ज करें"),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (isStockExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Section",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(
                                visible = isStockExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = stockMs,
                                            onValueChange = { stockMs = it },
                                            label = { Text(LanguageManager.translate("MS Opening Stock (L)", "एमएस ओपनिंग स्टॉक (लीटर)")) },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = stockHsd,
                                            onValueChange = { stockHsd = it },
                                            label = { Text(LanguageManager.translate("HSD Opening Stock (L)", "एचएसडी ओपनिंग स्टॉक (लीटर)")) },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = receiptMs,
                                            onValueChange = { receiptMs = it },
                                            label = { Text(LanguageManager.translate("MS Receipts / New Inv (L)", "एमएस रसीदें / नई इन्वेंट्री (लीटर)")) },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = receiptHsd,
                                            onValueChange = { receiptHsd = it },
                                            label = { Text(LanguageManager.translate("HSD Receipts / New Inv (L)", "एचएसडी रसीदें / नई इन्वेंट्री (लीटर)")) },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            sharedPrefs.edit()
                                                .putString("stock_ms_$selectedDate", stockMs)
                                                .putString("stock_hsd_$selectedDate", stockHsd)
                                                .putString("receipt_ms_$selectedDate", receiptMs)
                                                .putString("receipt_hsd_$selectedDate", receiptHsd)
                                                .apply()
                                            Toast.makeText(
                                                context,
                                                LanguageManager.translate(
                                                    "Stocks and Receipts saved successfully!",
                                                    "स्टॉक और रसीदें सफलतापूर्वक सहेज ली गईं!"
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            isStockExpanded = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = "Save Icon")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = LanguageManager.translate("SAVE STOCKS & RECEIPTS", "स्टॉक और रसीदें सहेजें"),
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // REPORTS CENTER (Create Reports)
            item {
                Text(
                    text = LanguageManager.translate("Reports Center", "रिपोर्ट्स केंद्र"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        // 1. Daily Sales Audit
                        ReportRowItem(
                            title = LanguageManager.translate("Daily Sales Audit Report", "दैनिक ऑडिट रिपोर्ट"),
                            subtitle = LanguageManager.translate(
                                "Reconcile net sales and print daily sales audit PDF.",
                                "शुद्ध बिक्री का मिलान करें और दैनिक बिक्री ऑडिट पीडीएफ प्रिंट करें।"
                            ),
                            icon = Icons.Default.Assessment,
                            iconColor = MaterialTheme.colorScheme.primary,
                            onClick = { onNavigateToDailyAudit(selectedDate) },
                            testTag = "manager_option_daily_audit"
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        
                        // 2. Monthly Daily Sales Report
                        ReportRowItem(
                            title = LanguageManager.translate("Monthly Daily Sales Report", "मासिक दैनिक बिक्री रिपोर्ट"),
                            subtitle = LanguageManager.translate(
                                "View daily stocks, receipts, rate/L and sales amount for entire days of the month.",
                                "महीने के पूरे दिनों के लिए दैनिक स्टॉक, रसीदें, दर/लीटर और बिक्री राशि देखें।"
                            ),
                            icon = Icons.Default.LocalGasStation,
                            iconColor = MaterialTheme.colorScheme.secondary,
                            onClick = { onNavigateToDailySalesReport() },
                            testTag = "manager_option_monthly_sales_report"
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        
                        // 3. TT Entry Report
                        ReportRowItem(
                            title = LanguageManager.translate("TT Entry Report", "टीटी प्रविष्टि रिपोर्ट"),
                            subtitle = LanguageManager.translate(
                                "View monthly list and total amounts of recorded TT receipt invoices.",
                                "दर्ज टीटी रसीद इनवॉइस की मासिक सूची और कुल राशि देखें।"
                            ),
                            icon = Icons.Default.Assessment,
                            iconColor = MaterialTheme.colorScheme.primary,
                            onClick = { onNavigateToTtEntryReport() },
                            testTag = "manager_option_tt_entry_report"
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        
                        // 4. Monthly Expenses Report
                        ReportRowItem(
                            title = LanguageManager.translate("Monthly Expenses Report", "मासिक खर्च रिपोर्ट"),
                            subtitle = LanguageManager.translate(
                                "View categorized summaries, key metrics, and details of daily station expenses.",
                                "दैनिक स्टेशन खर्चों के वर्गीकृत सारांश, प्रमुख विनिर्देश और विवरण देखें।"
                            ),
                            icon = Icons.Default.TrendingDown,
                            iconColor = MaterialTheme.colorScheme.secondary,
                            onClick = { onNavigateToMonthlyExpensesReport() },
                            testTag = "manager_option_monthly_expenses"
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        
                        // 5. Monthly Credit (Udhar) Report
                        ReportRowItem(
                            title = LanguageManager.translate("Monthly Credit Report", "मासिक उधार रिपोर्ट"),
                            subtitle = LanguageManager.translate(
                                "View total credits (udharis), account-wise party details and print reports.",
                                "कुल उधार (उधारियों), खाता-वार पार्टी विवरण देखें और रिपोर्ट प्रिंट करें।"
                            ),
                            icon = Icons.Default.TrendingUp,
                            iconColor = MaterialTheme.colorScheme.primary,
                            onClick = { onNavigateToMonthlyCreditReport() },
                            testTag = "manager_option_monthly_credit"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // 6. Monthly Udhari Jama Report
                        ReportRowItem(
                            title = LanguageManager.translate("Monthly Udhari Jama Report", "मासिक उधारी जमा रिपोर्ट"),
                            subtitle = LanguageManager.translate(
                                "View total recovered outstanding credits, party-wise deposit details and print reports.",
                                "कुल जमा पुरानी उधारी (वसूली), खाता-वार जमा विवरण देखें और रिपोर्ट प्रिंट करें।"
                            ),
                            icon = Icons.Default.TrendingUp,
                            iconColor = Color(0xFF2E7D32),
                            onClick = { onNavigateToMonthlyUdhariJamaReport() },
                            testTag = "manager_option_monthly_udhari_jama"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // 7. Customer Udhari Ledger Report
                        ReportRowItem(
                            title = LanguageManager.translate("Customer Credit Ledger", "ग्राहक क्रेडिट लेजर"),
                            subtitle = LanguageManager.translate(
                                "Track credits given vs. recovered and running balances for a particular customer.",
                                "किसी विशेष ग्राहक के लिए दिए गए और वसूल किए गए उधार और चालू खाते के शेष को ट्रैक करें।"
                            ),
                            icon = Icons.Default.Receipt,
                            iconColor = Color(0xFFE65100),
                            onClick = { onNavigateToCustomerUdhariLedgerReport() },
                            testTag = "manager_option_customer_credit_ledger"
                        )
                    }
                }
            }


        }
    }
}

@Composable
private fun ReportRowItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            color = iconColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Navigate",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}
