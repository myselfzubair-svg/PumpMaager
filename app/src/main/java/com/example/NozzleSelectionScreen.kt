package com.example

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.RegisteredNozzle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NozzleSelectionScreen(
    onNavigateToCalculator: (List<RegisteredNozzle>, date: String, caName: String) -> Unit,
    onNavigateToFullDay: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    initialMsCount: Int = 2,
    initialHsdCount: Int = 2,
    username: String = "",
    adminPhone: String = "",
    isCaModule: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    val coroutineScope = rememberCoroutineScope()

    // Screen State
    var nozzlesList by remember { mutableStateOf<List<RegisteredNozzle>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Step 2 State: Date
    var selectedDate by rememberSaveable { mutableStateOf(sdf.format(Date())) }

    // Step 3 State: Nozzle Selection
    var selectedNozzleIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var nozzleSearchQuery by rememberSaveable { mutableStateOf("") }

    // Legacy/Manager States
    val legacyProductCounts = remember { mutableStateMapOf<String, String>() }
    var stationProducts by remember { mutableStateOf<List<String>>(emptyList()) }
    
    var showError by remember { mutableStateOf(false) }

    // Fetch Nozzles every time screen is opened
    LaunchedEffect(adminPhone) {
        if (adminPhone.isNotBlank()) {
            isLoading = true
            errorMessage = null
            try {
                val list = withContext(Dispatchers.IO) {
                    com.example.database.FirestoreRepository.getRegisteredNozzles(adminPhone)
                }
                nozzlesList = list
                
                val pumpInfo = withContext(Dispatchers.IO) {
                    com.example.database.FirestoreRepository.getPumpInfo(adminPhone)
                }
                stationProducts = pumpInfo?.productNames?.split(",")?.filter { it.isNotBlank() } ?: listOf("MS", "HSD")
                
                stationProducts.forEach { product ->
                    if (!legacyProductCounts.containsKey(product)) {
                        legacyProductCounts[product] = "1"
                    }
                }

                isLoading = false
            } catch (_: Exception) {
                errorMessage = LanguageManager.errorLoadingNozzles
                isLoading = false
            }
        }
    }

    // Filter active nozzles from DB
    val activeNozzles = remember(nozzlesList) {
        nozzlesList.filter { it.isActive }
    }

    val filteredNozzles = remember(activeNozzles, nozzleSearchQuery) {
        if (nozzleSearchQuery.isBlank()) {
            activeNozzles
        } else {
            activeNozzles.filter {
                it.label.contains(nozzleSearchQuery, ignoreCase = true) ||
                it.nozzleName.contains(nozzleSearchQuery, ignoreCase = true) ||
                it.tankName.contains(nozzleSearchQuery, ignoreCase = true) ||
                it.nozzleType.contains(nozzleSearchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isCaModule) LanguageManager.caConfigTitle else LanguageManager.translate("Configure Nozzles", "नोज़ल कॉन्फ़िगर करें"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("nozzle_selection_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = LanguageManager.selectionBackDesc
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
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = { 
                            // Retry logic
                            isLoading = true
                            errorMessage = null
                            coroutineScope.launch {
                                try {
                                    val list = withContext(Dispatchers.IO) {
                                        com.example.database.FirestoreRepository.getRegisteredNozzles(adminPhone)
                                    }
                                    nozzlesList = list
                                } catch (e: Exception) {
                                    errorMessage = LanguageManager.errorLoadingNozzles
                                } finally {
                                    isLoading = false
                                }
                            }
                        }) {
                            Text(LanguageManager.translate("Retry", "पुनः प्रयास करें"))
                        }
                    }
                }
            } else if (isCaModule) {
                // CA MODULE STEP-BY-STEP WORKFLOW

                // STEP 1: CA NAME (READ ONLY)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = LanguageManager.stepCaName,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "$username ${LanguageManager.caNameAutoFilled}",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // STEP 2: SELECT DATE
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val parts = selectedDate.split("-")
                        if (parts.size == 3) {
                            calendar.set(Calendar.DAY_OF_MONTH, parts[0].toInt())
                            calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                            calendar.set(Calendar.YEAR, parts[2].toInt())
                        }
                        val dialog = DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                selectedDate = String.format("%02d-%02d-%04d", day, month + 1, year)
                            },
                            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
                        )
                        dialog.datePicker.maxDate = System.currentTimeMillis() // Restrict future dates
                        dialog.show()
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    text = LanguageManager.stepSelectDate,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(text = selectedDate, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                        Text(
                            text = LanguageManager.translate("Change", "बदलें"),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // STEP 3: SELECT NOZZLES
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = LanguageManager.stepSelectNozzles,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = LanguageManager.selectNozzlesDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (activeNozzles.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = LanguageManager.noNozzlesFound,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Search & Bulk Select
                        OutlinedTextField(
                            value = nozzleSearchQuery,
                            onValueChange = { nozzleSearchQuery = it },
                            placeholder = { Text(LanguageManager.searchNozzles) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { selectedNozzleIds = activeNozzles.map { it.nozzleId }.toSet() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(LanguageManager.selectAll)
                            }
                            TextButton(
                                onClick = { selectedNozzleIds = emptySet() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(LanguageManager.clearSelection)
                            }
                        }

                        // Nozzle List
                        filteredNozzles.forEach { nozzle ->
                            val isSelected = selectedNozzleIds.contains(nozzle.nozzleId)
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedNozzleIds = if (isSelected) selectedNozzleIds - nozzle.nozzleId else selectedNozzleIds + nozzle.nozzleId
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedNozzleIds = if (checked) selectedNozzleIds + nozzle.nozzleId else selectedNozzleIds - nozzle.nozzleId
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = nozzle.label,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${nozzle.nozzleType} | ${nozzle.tankName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (nozzle.nozzleNumber.isNotBlank()) {
                                        Surface(
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = CircleShape,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = nozzle.nozzleNumber,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Proceed Button
                Button(
                    onClick = {
                        if (selectedNozzleIds.isEmpty()) {
                            showError = true
                        } else {
                            val nozzles = activeNozzles.filter { selectedNozzleIds.contains(it.nozzleId) }
                            onNavigateToCalculator(nozzles, selectedDate, username)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("start_calculation_button"),
                    shape = RoundedCornerShape(28.dp),
                    enabled = selectedNozzleIds.isNotEmpty()
                ) {
                    Text(text = LanguageManager.startCalculation, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                }

            } else {
                // LEGACY / MANAGER CONFIGURATION (Dynamic count based)
                Text(
                    text = LanguageManager.translate("Nozzle Configuration", "नोज़ल कॉन्फ़िगरेशन"),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = LanguageManager.translate(
                        "Please specify the active number of MS and HSD nozzles (up to 10 each) to dynamically customize your shift calculation.",
                        "कृपया अपनी शिफ्ट गणना को गतिशील रूप से अनुकूलित करने के लिए MS और HSD नोज़ल (प्रत्येक 10 तक) की सक्रिय संख्या निर्दिष्ट करें।"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                if (showError) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = LanguageManager.translate(
                                "Please select at least one active nozzle to proceed.",
                                "आगे बढ़ने के लिए कृपया कम से कम एक सक्रिय नोज़ल चुनें।"
                            ),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Dynamic Legacy Sections per Product
                stationProducts.forEach { product ->
                    val isMs = isSameProduct(product, "MS")
                    val isHsd = isSameProduct(product, "HSD")
                    val badgeColor = when {
                        isMs -> MaterialTheme.colorScheme.primary
                        isHsd -> MaterialTheme.colorScheme.tertiary
                        else -> Color(0xFF8B5CF6)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "$product Nozzles",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = badgeColor
                            )
                            VoiceOutlinedTextField(
                                value = legacyProductCounts[product] ?: "",
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }
                                    if (filtered.isEmpty()) legacyProductCounts[product] = ""
                                    else if (filtered.toInt() in 0..15) legacyProductCounts[product] = filtered
                                },
                                label = { Text("$product Count (0-15)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val allCounts = legacyProductCounts.values.map { it.toIntOrNull() ?: 0 }
                        if (allCounts.sum() == 0) showError = true
                        else {
                            val dummyList = mutableListOf<RegisteredNozzle>()
                            stationProducts.forEach { product ->
                                val count = legacyProductCounts[product]?.toIntOrNull() ?: 0
                                for (i in 1..count) {
                                    dummyList.add(RegisteredNozzle(
                                        mobileNumber = adminPhone,
                                        nozzleType = product,
                                        productId = "PROD_${product.uppercase().replace(" ", "_")}",
                                        label = "$product Nozzle $i",
                                        nozzleName = "$product Nozzle $i",
                                        nozzleNumber = i.toString(),
                                        nozzleIndex = dummyList.size
                                    ))
                                }
                            }
                            onNavigateToCalculator(dummyList, selectedDate, username)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text(text = LanguageManager.translate("PROCEED TO DETAILS", "विवरण पर जाएं"), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}
