package com.example

import android.util.Log
import android.app.DatePickerDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.example.database.RegisteredNozzle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
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
    username: String = "",
    adminPhone: String = "",
    userRole: String = "CA",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    val coroutineScope = rememberCoroutineScope()

    // Screen State
    var nozzlesList by remember { mutableStateOf<List<RegisteredNozzle>>(emptyList()) }
    var staffList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // State: CA Name Selection
    var selectedCaName by rememberSaveable { mutableStateOf(username) }
    var isCaDropdownExpanded by remember { mutableStateOf(false) }

    // State: Date
    var selectedDate by rememberSaveable { mutableStateOf(sdf.format(Date())) }

    // State: Nozzle Selection
    var selectedNozzleIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var nozzleSearchQuery by rememberSaveable { mutableStateOf("") }
    
    var showError by remember { mutableStateOf(false) }

    val loadData = {
        if (adminPhone.isNotBlank()) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
                try {
                    nozzlesList = com.example.database.SupabaseRepository.getRegisteredNozzles(adminPhone)
                    
                    if (userRole.uppercase() != "CA") {
                        com.example.database.SupabaseRepository.getStaffMembersFlow(adminPhone).take(1).collect { members ->
                            val names = (listOf(username) + members.map { it.name }).distinct().sorted()
                            staffList = names
                        }
                    }
                } catch (e: Exception) {
                    errorMessage = LanguageManager.errorLoadingNozzles
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Fetch Staff Names (Continuous updates)
    LaunchedEffect(adminPhone) {
        if (adminPhone.isNotBlank() && userRole.uppercase() != "CA") {
            com.example.database.SupabaseRepository.getStaffMembersFlow(adminPhone).collect { members ->
                val names = (listOf(username) + members.map { it.name }).distinct().sorted()
                staffList = names
            }
        }
    }

    // Initial Load
    LaunchedEffect(adminPhone) {
        loadData()
    }

    // Filter active nozzles from DB
    val activeNozzles = nozzlesList

    val filteredNozzles = remember(activeNozzles, nozzleSearchQuery) {
        if (nozzleSearchQuery.isBlank()) {
            activeNozzles
        } else {
            activeNozzles.filter {
                it.label.contains(nozzleSearchQuery, ignoreCase = true) ||
                it.nozzleName.contains(nozzleSearchQuery, ignoreCase = true) ||
                it.nozzleType.contains(nozzleSearchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = LanguageManager.caConfigTitle,
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
                        TextButton(onClick = { loadData() }) {
                            Text(LanguageManager.translate("Retry", "पुनः प्रयास करें"))
                        }
                    }
                }
            } else {
                // STEP 1: CA NAME (Dropdown for Managers, Read-only for CA)
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
                        
                        if (userRole.uppercase() == "CA" || staffList.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "$username ${LanguageManager.caNameAutoFilled}",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            // DROPDOWN for Admin/Manager
                            Box {
                                VoiceOutlinedTextField(
                                    value = selectedCaName,
                                    onValueChange = { selectedCaName = it },
                                    label = { Text(LanguageManager.selectCaName) },
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) },
                                    trailingIcon = {
                                        IconButton(onClick = { isCaDropdownExpanded = true }) {
                                            Icon(Icons.Default.ArrowDropDown, null)
                                        }
                                    },
                                    readOnly = true // Forced selection from list
                                )
                                DropdownMenu(
                                    expanded = isCaDropdownExpanded,
                                    onDismissRequest = { isCaDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.8f)
                                ) {
                                    staffList.forEach { name ->
                                        DropdownMenuItem(
                                            text = { Text(name, fontWeight = if (name == username) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                selectedCaName = name
                                                isCaDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
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
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = LanguageManager.noNozzlesFound,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { loadData() }) {
                                    Icon(Icons.Default.Refresh, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Retry Fetch")
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Debug Info: Admin Phone = [$adminPhone]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "Raw List Size: ${nozzlesList.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                                if (errorMessage != null) {
                                    Text(
                                        text = "Last Error: $errorMessage",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    } else {
                        // Search & Bulk Select
                        VoiceOutlinedTextField(
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

                        // Nozzle List Grouped by Product Type and Sorted by Number
                        val groupedNozzles = filteredNozzles.groupBy { it.nozzleType }
                        
                        groupedNozzles.forEach { (productType, nozzles) ->
                            Text(
                                text = if (productType.isNullOrBlank()) "Other Products" else productType,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                            
                            nozzles.sortedBy { it.nozzleNumber.toIntOrNull() ?: Int.MAX_VALUE }.forEach { nozzle ->
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
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Proceed Button
                Button(
                    onClick = {
                        if (selectedNozzleIds.isEmpty()) {
                            showError = true
                        } else {
                            val nozzles = activeNozzles.filter { selectedNozzleIds.contains(it.nozzleId) }
                            onNavigateToCalculator(nozzles, selectedDate, selectedCaName)
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
            }
        }
    }
}
