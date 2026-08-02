package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditDetailsScreen(
    isFourNozzle: Boolean,
    selectedDate: String,
    onBack: () -> Unit,
    onProceed: (date: String, caName: String, meterNo: String) -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dateInput by remember { mutableStateOf(selectedDate) }
    var caNameInput by remember { mutableStateOf("") }
    
    val meterOptions = if (isFourNozzle) {
        listOf("Meter No 1, 2, 3 & 4")
    } else {
        listOf("Meter No 1 & 2", "Meter No 3 & 4")
    }
    
    var meterNoInput by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isFourNozzle) LanguageManager.audit4Config else LanguageManager.audit2Config,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("audit_details_back_button")
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
            Spacer(modifier = Modifier.height(10.dp))

            // Beautiful header illustration/card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = LanguageManager.auditSessionInit,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = LanguageManager.recordDetailsText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Form Fields
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. DATE INPUT
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = LanguageManager.auditDate,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    VoiceOutlinedTextField(
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audit_details_date_input"),
                        leadingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = "Date icon", tint = MaterialTheme.colorScheme.primary)
                        },
                        placeholder = { Text("dd-mm-yyyy") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // 2. CA NAME INPUT
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = LanguageManager.caNameLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    VoiceOutlinedTextField(
                        value = caNameInput,
                        onValueChange = { 
                            caNameInput = it 
                            if (it.trim().isNotEmpty()) {
                                  showError = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audit_details_ca_name_input"),
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = "Person icon", tint = MaterialTheme.colorScheme.primary)
                        },
                        placeholder = { Text(LanguageManager.caNamePlaceholder) },
                        singleLine = true,
                        isError = showError && caNameInput.trim().isEmpty(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (showError && caNameInput.trim().isEmpty()) {
                        Text(
                            text = LanguageManager.caNameError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            Button(
                onClick = {
                    if (caNameInput.trim().isEmpty()) {
                        showError = true
                    } else {
                        onProceed(dateInput.trim(), caNameInput.trim(), "")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("audit_details_proceed_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = LanguageManager.proceedToCalculator,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Proceed to calculation screen"
                    )
                }
            }
        }
    }
}
