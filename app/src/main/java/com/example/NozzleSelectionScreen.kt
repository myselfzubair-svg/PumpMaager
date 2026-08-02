package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NozzleSelectionScreen(
    onNavigateToCalculator: (msCount: Int, hsdCount: Int, msLabels: List<String>, hsdLabels: List<String>) -> Unit,
    onNavigateToFullDay: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    initialMsCount: Int = 2,
    initialHsdCount: Int = 2,
    username: String = "",
    isCaModule: Boolean = false,
    msNozzleLabels: List<String> = emptyList(),
    hsdNozzleLabels: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    var msNozzleCountText by rememberSaveable { mutableStateOf(initialMsCount.toString()) }
    var hsdNozzleCountText by rememberSaveable { mutableStateOf(initialHsdCount.toString()) }
    var showError by remember { mutableStateOf(false) }

    var selectedMsNozzles by remember(msNozzleLabels) { mutableStateOf(emptySet<String>()) }
    var selectedHsdNozzles by remember(hsdNozzleLabels) { mutableStateOf(emptySet<String>()) }

    val isDbRestricted = isCaModule && username.isNotBlank() && (msNozzleLabels.isNotEmpty() || hsdNozzleLabels.isNotEmpty())

    LaunchedEffect(isDbRestricted, msNozzleLabels, hsdNozzleLabels, selectedMsNozzles, selectedHsdNozzles) {
        if (isDbRestricted) {
            msNozzleCountText = selectedMsNozzles.size.toString()
            hsdNozzleCountText = selectedHsdNozzles.size.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = LanguageManager.translate("Configure Nozzles", "नोज़ल कॉन्फ़िगर करें"),
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
            // Greeting & Instruction
            Text(
                text = LanguageManager.translate("Nozzle Configuration", "नोज़ल कॉन्फ़िगरेशन"),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                textAlign = TextAlign.Center
            )

            if (isDbRestricted) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = username.take(1).uppercase(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        Column {
                            Text(
                                text = LanguageManager.translate("Active User: $username", "सक्रिय उपयोगकर्ता: $username"),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = LanguageManager.translate(
                                    "Showing only registered nozzles in database.",
                                    "डेटाबेस में केवल पंजीकृत नोज़ल दिखा रहा है।"
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            } else {
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
            }

            if (showError) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = LanguageManager.translate(
                            "Please select at least one active nozzle (MS or HSD) to proceed.",
                            "कृपया आगे बढ़ने के लिए कम से कम एक सक्रिय नोज़ल (MS या HSD) चुनें।"
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 1. Motor Spirit (MS) Petrol Nozzles Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = LanguageManager.translate("Motor Spirit (MS) Nozzles", "मोटर स्पिरिट (MS) नोज़ल"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (isDbRestricted) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = LanguageManager.translate(
                                    "Select Active MS Nozzles:",
                                    "सक्रिय MS नोज़ल चुनें:"
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            msNozzleLabels.forEach { label ->
                                val isSelected = selectedMsNozzles.contains(label)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                            else Color.Transparent, 
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            selectedMsNozzles = if (isSelected) {
                                                selectedMsNozzles - label
                                            } else {
                                                selectedMsNozzles + label
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedMsNozzles = if (checked) {
                                                selectedMsNozzles + label
                                            } else {
                                                selectedMsNozzles - label
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else {
                        VoiceOutlinedTextField(
                            value = msNozzleCountText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }
                                val num = filtered.toIntOrNull()
                                if (num == null) {
                                    msNozzleCountText = ""
                                } else if (num in 0..10) {
                                    msNozzleCountText = filtered
                                }
                            },
                            label = {
                                Text(LanguageManager.translate("MS Nozzle Count (0-10)", "MS नोज़ल संख्या (0-10)"))
                            },
                            placeholder = {
                                Text("e.g. 2 or 4")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ms_nozzle_count_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Quick select options for MS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("0", "1", "2", "3", "4", "6", "8", "10").forEach { count ->
                                val isSelected = msNozzleCountText == count
                                OutlinedButton(
                                    onClick = { msNozzleCountText = count },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("quick_select_ms_$count"),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text(
                                        text = count,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. High Speed Diesel (HSD) Diesel Nozzles Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = LanguageManager.translate("High Speed Diesel (HSD) Nozzles", "हाई स्पीड डीजल (HSD) नोज़ल"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.tertiary
                    )

                    if (isDbRestricted) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = LanguageManager.translate(
                                    "Select Active HSD Nozzles:",
                                    "सक्रिय HSD नोज़ल चुनें:"
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            hsdNozzleLabels.forEach { label ->
                                val isSelected = selectedHsdNozzles.contains(label)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                                            else Color.Transparent, 
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            selectedHsdNozzles = if (isSelected) {
                                                selectedHsdNozzles - label
                                            } else {
                                                selectedHsdNozzles + label
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedHsdNozzles = if (checked) {
                                                selectedHsdNozzles + label
                                            } else {
                                                selectedHsdNozzles - label
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else {
                        VoiceOutlinedTextField(
                            value = hsdNozzleCountText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }
                                val num = filtered.toIntOrNull()
                                if (num == null) {
                                    hsdNozzleCountText = ""
                                } else if (num in 0..10) {
                                    hsdNozzleCountText = filtered
                                }
                            },
                            label = {
                                Text(LanguageManager.translate("HSD Nozzle Count (0-10)", "HSD नोज़ल संख्या (0-10)"))
                            },
                            placeholder = {
                                Text("e.g. 2 or 4")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hsd_nozzle_count_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Quick select options for HSD
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("0", "1", "2", "3", "4", "6", "8", "10").forEach { count ->
                                val isSelected = hsdNozzleCountText == count
                                OutlinedButton(
                                    onClick = { hsdNozzleCountText = count },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("quick_select_hsd_$count"),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text(
                                        text = count,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
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
                    val msCountVal = msNozzleCountText.toIntOrNull() ?: 0
                    val hsdCountVal = hsdNozzleCountText.toIntOrNull() ?: 0
                    if (msCountVal == 0 && hsdCountVal == 0) {
                        showError = true
                    } else {
                        showError = false
                        if (isDbRestricted) {
                            val activeMs = msNozzleLabels.filter { selectedMsNozzles.contains(it) }
                            val activeHsd = hsdNozzleLabels.filter { selectedHsdNozzles.contains(it) }
                            onNavigateToCalculator(activeMs.size, activeHsd.size, activeMs, activeHsd)
                        } else {
                            onNavigateToCalculator(msCountVal, hsdCountVal, emptyList(), emptyList())
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("proceed_nozzle_selection"),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = LanguageManager.translate("PROCEED TO DETAILS", "विवरण पर जाएं"),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Proceed arrow"
                    )
                }
            }
        }
    }
}
