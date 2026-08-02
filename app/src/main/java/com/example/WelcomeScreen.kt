package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.example.database.AppDatabase
import com.example.database.PhoneAccess
import com.example.database.LoginInfo
import com.example.database.User
import com.example.database.RegisteredNozzle
import com.example.database.MsNozzleReading
import com.example.database.HsdNozzleReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onNavigateToCalculator: () -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    pumpName: String = "D R Inamdar Petroleum",
    loggedInMobileNumber: String = "",
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val coroutineScope = rememberCoroutineScope()

    var userRole by remember { mutableStateOf<String?>(null) }

    var isSyncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf("") }
    var lastSyncTime by remember { mutableStateOf<String?>(null) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(loggedInMobileNumber) {
        if (loggedInMobileNumber.isNotEmpty()) {
            coroutineScope.launch {
                isSyncing = true
                syncStatus = "Initiating cloud sync..."
                com.example.database.FirestoreSyncManager.syncAll(context, loggedInMobileNumber) { progress ->
                    syncStatus = progress
                }.onSuccess {
                    isSyncing = false
                    val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    lastSyncTime = sdf.format(java.util.Date())
                    syncStatus = "Cloud sync complete"
                }.onFailure { e ->
                    isSyncing = false
                    syncStatus = "Sync failed: ${e.localizedMessage}"
                }
            }
        }
    }

    LaunchedEffect(loggedInMobileNumber) {
        if (loggedInMobileNumber.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                val access = db.phoneAccessDao().getAccessByPhone(loggedInMobileNumber)
                val isPumpOwner = db.pumpInfoDao().getPumpInfoByMobile(loggedInMobileNumber) != null
                withContext(Dispatchers.Main) {
                    userRole = access?.role ?: if (isPumpOwner) "ADMIN" else null
                }
            }
        } else {
            userRole = null
        }
    }

    val todayDate = remember {
        java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())
    }

    var densityMs by remember { mutableStateOf("") }
    var densityHsd by remember { mutableStateOf("") }
    var rateMs by remember { mutableStateOf("") }
    var rateHsd by remember { mutableStateOf("") }
    var stockMs by remember { mutableStateOf("") }
    var stockHsd by remember { mutableStateOf("") }
    var msNozzleLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var hsdNozzleLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var todayTtReceiptsCount by remember { mutableStateOf(0) }

    LaunchedEffect(loggedInMobileNumber, todayDate) {
        coroutineScope.launch(Dispatchers.IO) {
            val sharedPrefs = context.getSharedPreferences("pump_manager_prefs", android.content.Context.MODE_PRIVATE)
            val dMs = sharedPrefs.getString("density_ms_$todayDate", "") ?: ""
            val dHsd = sharedPrefs.getString("density_hsd_$todayDate", "") ?: ""
            val rMs = sharedPrefs.getString("rate_ms_$todayDate", "") ?: ""
            val rHsd = sharedPrefs.getString("rate_hsd_$todayDate", "") ?: ""
            val sMs = sharedPrefs.getString("stock_ms_$todayDate", "") ?: ""
            val sHsd = sharedPrefs.getString("stock_hsd_$todayDate", "") ?: ""
            
            val registeredList = if (loggedInMobileNumber.isNotEmpty()) {
                db.registeredNozzleDao().getNozzlesByPumpMobile(loggedInMobileNumber)
            } else {
                emptyList()
            }
            val msLabels = registeredList.filter { it.nozzleType == "MS" }.sortedBy { it.nozzleIndex }.map { it.label }
            val hsdLabels = registeredList.filter { it.nozzleType == "HSD" }.sortedBy { it.nozzleIndex }.map { it.label }

            val todayTts = db.ttReceiptEntryDao().getEntriesByDate(todayDate)

            withContext(Dispatchers.Main) {
                densityMs = dMs
                densityHsd = dHsd
                rateMs = rMs
                rateHsd = rHsd
                stockMs = sMs
                stockHsd = sHsd
                msNozzleLabels = msLabels
                hsdNozzleLabels = hsdLabels
                todayTtReceiptsCount = todayTts.size
            }
        }
    }

    val allAuditsState = db.savedAuditDao().getAllAudits().collectAsState(initial = emptyList())
    val todayAuditsCount = remember(allAuditsState.value, todayDate) {
        allAuditsState.value.count { it.date == todayDate }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = LanguageManager.translate("Fuel Station", "फ्यूल स्टेशन"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
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
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
        // Aesthetic Language and Theme Customizer at Top Center
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Language Selector Row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isEnglish = language == AppLanguage.English
                Button(
                    onClick = { onLanguageChange(AppLanguage.English) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEnglish) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (isEnglish) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(36.dp).testTag("lang_en_button")
                ) {
                    Text("English", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(
                    onClick = { onLanguageChange(AppLanguage.Hindi) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isEnglish) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (!isEnglish) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(36.dp).testTag("lang_hi_button")
                ) {
                    Text("हिन्दी (Hindi)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Theme Selector Row (Toggle Light / Dark)
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onThemeChange(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isDarkTheme) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (!isDarkTheme) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(36.dp).testTag("theme_light_button")
                ) {
                    Text(
                        text = LanguageManager.translate("Light Theme", "लाइट थीम"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = { onThemeChange(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkTheme) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (isDarkTheme) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(36.dp).testTag("theme_dark_button")
                ) {
                    Text(
                        text = LanguageManager.translate("Dark Theme", "डार्क थीम"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(top = 100.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Active Session Indicator
            if (loggedInMobileNumber.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .testTag("session_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active Session",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = LanguageManager.translate(
                                "Active Session: $pumpName",
                                "सक्रिय सत्र: $pumpName"
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Cloud Synchronization Indicator & Trigger
            if (loggedInMobileNumber.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSyncing) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        } else if (syncStatus.startsWith("Sync failed")) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .testTag("cloud_sync_card"),
                    border = BorderStroke(
                        1.dp, 
                        if (isSyncing) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else if (syncStatus.startsWith("Sync failed")) MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = if (syncStatus.startsWith("Sync failed")) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                    contentDescription = "Cloud Status",
                                    tint = if (syncStatus.startsWith("Sync failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = if (isSyncing) LanguageManager.translate("Syncing with Cloud...", "क्लाउड से सिंक हो रहा है...")
                                           else if (syncStatus.startsWith("Sync failed")) LanguageManager.translate("Cloud Sync Failed", "क्लाउड सिंक विफल रहा")
                                           else LanguageManager.translate("Cloud Storage Connected", "क्लाउड स्टोरेज कनेक्टेड"),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (syncStatus.startsWith("Sync failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isSyncing) syncStatus
                                           else if (syncStatus.startsWith("Sync failed")) syncStatus
                                           else if (lastSyncTime != null) LanguageManager.translate("Last synced: $lastSyncTime", "अंतिम सिंक: $lastSyncTime")
                                           else LanguageManager.translate("Multi-device backup is active", "मल्टी-डिवाइस बैकअप सक्रिय है"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isSyncing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (syncStatus.startsWith("Sync failed")) {
                                    IconButton(
                                        onClick = { showDiagnosticsDialog = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Troubleshoot",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            isSyncing = true
                                            syncStatus = "Initiating cloud sync..."
                                            com.example.database.FirestoreSyncManager.syncAll(context, loggedInMobileNumber) { progress ->
                                                syncStatus = progress
                                            }.onSuccess {
                                                isSyncing = false
                                                val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                                                lastSyncTime = sdf.format(java.util.Date())
                                                syncStatus = "Cloud sync complete"
                                                Toast.makeText(context, "Cloud sync complete!", Toast.LENGTH_SHORT).show()
                                            }.onFailure { e ->
                                                isSyncing = false
                                                syncStatus = "Sync failed: ${e.localizedMessage}"
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync Now",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showDiagnosticsDialog) {
                val appCheckDebugToken = remember { MyApplication.getAppCheckDebugToken(context) }
                AlertDialog(
                    onDismissRequest = { showDiagnosticsDialog = false },
                    icon = { Icon(Icons.Default.BugReport, contentDescription = "Diagnostics", tint = MaterialTheme.colorScheme.primary) },
                    title = { Text("Firebase Connection Troubleshooter") },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "If your sync is failing, it's typically caused by App Check or Security Rules configuration on the Firebase Console:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            Divider()
                            
                            Text(
                                "1. Register your App Check Debug Token (Crucial)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "App Check is now fully implemented on the client! Because you have enforced App Check in the Firebase Console, you must register your debug token so Firestore accepts incoming connections:\n" +
                                "• Open Firebase Console -> Build -> App Check -> Apps tab.\n" +
                                "• Click the 3 vertical dots next to your Android App, select 'Manage debug tokens', and click 'Add debug token' to register it.\n" +
                                "• Alternatively, you can temporarily disable enforcement under the APIs tab while debugging.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            if (appCheckDebugToken != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            "Your App Check Debug Token:",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        androidx.compose.foundation.text.selection.SelectionContainer {
                                            Text(
                                                appCheckDebugToken,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                        Button(
                                            onClick = {
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(appCheckDebugToken))
                                            },
                                            modifier = Modifier.align(Alignment.End),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary,
                                                contentColor = MaterialTheme.colorScheme.onSecondary
                                            )
                                        ) {
                                            Text("Copy to Clipboard")
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Debug token has not been generated or cached yet. Make sure you are in a Debug build and try triggering a sync.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(12.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Text(
                                "2. Configure Firestore Security Rules",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Ensure your Firestore database rules allow read/write access:\n" +
                                "• Go to Firebase Console -> Firestore Database -> Rules.\n" +
                                "• Change the rules to allow read/write access:\n" +
                                "  allow read, write: if true;",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                "3. Offline Mode Status",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Don't worry! All audits, readings, and entries are fully saved locally in your device's SQLite database. You can work completely offline, and the app will automatically sync back with your Firebase cloud as soon as a connection is restored.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDiagnosticsDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            // Welcome message with monochromatic themed text color
            Text(
                text = LanguageManager.translate(
                    "Welcome to\n$pumpName",
                    "$pumpName\nमें आपका स्वागत है"
                ),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 0.5.sp,
                    lineHeight = 34.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .testTag("welcome_station_name")
            )

            // Today's Setup Overview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
                    .testTag("todays_setup_overview_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Row with Date Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = LanguageManager.translate("Today's Setup Overview", "आज का सेटअप विवरण"),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        // Date pill
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = todayDate,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // Grid or Columns for parameters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left Column: Fuel Densities
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = LanguageManager.translate("MORNING DENSITIES", "सुबह की डेंसिटी"),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // MS Petrol Density
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFE57373), shape = RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = "MS: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (densityMs.isNotBlank()) "$densityMs" else LanguageManager.translate("Not Set", "दर्ज नहीं"),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (densityMs.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                )
                            }

                            // HSD Diesel Density
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF81C784), shape = RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = "HSD: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (densityHsd.isNotBlank()) "$densityHsd" else LanguageManager.translate("Not Set", "दर्ज नहीं"),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (densityHsd.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Middle Column: Fuel Rates
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = LanguageManager.translate("FUEL RATES", "ईंधन दरें"),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // MS Petrol Rate
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFE57373), shape = RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = "MS: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (rateMs.isNotBlank()) "₹$rateMs" else LanguageManager.translate("Not Set", "दर्ज नहीं"),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (rateMs.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                )
                            }

                            // HSD Diesel Rate
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF81C784), shape = RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = "HSD: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (rateHsd.isNotBlank()) "₹$rateHsd" else LanguageManager.translate("Not Set", "दर्ज नहीं"),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (rateHsd.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Right Column: Opening Stock
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = LanguageManager.translate("OPENING STOCK", "प्रारंभिक स्टॉक"),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // MS Petrol Stock
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFE57373), shape = RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = "MS: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (stockMs.isNotBlank()) "$stockMs L" else LanguageManager.translate("Not Set", "दर्ज नहीं"),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (stockMs.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                )
                            }

                            // HSD Diesel Stock
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF81C784), shape = RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = "HSD: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (stockHsd.isNotBlank()) "$stockHsd L" else LanguageManager.translate("Not Set", "दर्ज नहीं"),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (stockHsd.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Minimalistic Next Button styled in elegant monochromatic primary color
            Button(
                onClick = onNavigateToCalculator,
                modifier = Modifier
                    .width(220.dp)
                    .height(56.dp)
                    .testTag("start_calculations_button"),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 6.dp
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = LanguageManager.next,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            letterSpacing = 1.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Navigate to calculations button",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

        }

        // Beautiful, fancy partner logo / credits card in black and white theme
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .testTag("powered_by_fancy_badge"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = LanguageManager.poweredBy,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                        fontSize = 11.sp
                    ),
                    textAlign = TextAlign.Center
                )
                
                // Fancy layout for the company logo text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Modern styled dual-pulse dynamic visual mark
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), shape = RoundedCornerShape(3.dp))
                        )
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.5.dp))
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), shape = RoundedCornerShape(3.dp))
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GenovaCare",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.2.sp,
                                fontSize = 15.sp
                            )
                        )
                        Text(
                            text = "™",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 9.sp
                            ),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Innovations",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.2.sp,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
}
