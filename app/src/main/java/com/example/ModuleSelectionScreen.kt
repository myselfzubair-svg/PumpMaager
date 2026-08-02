package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.AppDatabase
import com.example.database.PhoneAccess
import com.example.database.LoginInfo
import com.example.database.Staff
import com.example.database.MsNozzleReading
import com.example.database.HsdNozzleReading
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.lazy.LazyColumn
import com.example.PdfGenerator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalClipboardManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleSelectionScreen(
    loggedInMobileNumber: String,
    onNavigateToCaModule: () -> Unit,
    onNavigateToManagersModule: () -> Unit,
    onNavigateToAdminPanel: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var deniedMessage by remember { mutableStateOf("") }

    var userRole by remember { mutableStateOf<String?>(null) }
    var isPumpOwnerState by remember { mutableStateOf(false) }

    var showUserCreationPanel by remember { mutableStateOf(false) }
    var staffList by remember { mutableStateOf<List<com.example.database.Staff>>(emptyList()) }

    LaunchedEffect(loggedInMobileNumber, userRole, showUserCreationPanel) {
        if (loggedInMobileNumber.isEmpty() || userRole == "ADMIN") {
            coroutineScope.launch(Dispatchers.IO) {
                val list = db.staffDao().getAllStaff()
                withContext(Dispatchers.Main) {
                    staffList = list
                }
            }
        } else {
            staffList = emptyList()
        }
    }

    LaunchedEffect(loggedInMobileNumber) {
        if (loggedInMobileNumber.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                val isPumpOwner = db.pumpInfoDao().getPumpInfoByMobile(loggedInMobileNumber) != null
                val record = db.phoneAccessDao().getAccessByPhone(loggedInMobileNumber)
                withContext(Dispatchers.Main) {
                    isPumpOwnerState = isPumpOwner
                    userRole = record?.role ?: if (isPumpOwner) "ADMIN" else null
                }
            }
        } else {
            userRole = null
            isPumpOwnerState = false
        }
    }

    val showCaCard = loggedInMobileNumber.isEmpty() || userRole == "ADMIN" || userRole == "CA" || userRole == "BOTH"
    val showManagerCard = loggedInMobileNumber.isEmpty() || userRole == "ADMIN" || userRole == "MANAGER" || userRole == "BOTH"
    val showAdminCard = loggedInMobileNumber.isEmpty() || userRole == "ADMIN"

    fun checkAccess(requiredRole: String, onGranted: () -> Unit) {
        if (loggedInMobileNumber.isEmpty()) {
            // Skipped login / Guest Mode: allow both modules for frictionless exploration
            onGranted()
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            val isPumpOwner = db.pumpInfoDao().getPumpInfoByMobile(loggedInMobileNumber) != null
            val allAccess = db.phoneAccessDao().getAllAccess()
            val record = allAccess.find { it.phone == loggedInMobileNumber }
            
            val isUserAdmin = if (record != null) {
                record.role == "ADMIN"
            } else {
                isPumpOwner
            }

            val hasAccess = if (isUserAdmin) {
                // Admin has full access to CA, MANAGER, and ADMIN
                true
            } else if (record != null) {
                when (requiredRole) {
                    "CA" -> record.role == "CA" || record.role == "BOTH"
                    "MANAGER" -> record.role == "MANAGER" || record.role == "BOTH"
                    "ADMIN" -> false
                    else -> false
                }
            } else {
                // No access restrictions seeded and not owner: let them in if allAccess is empty
                allAccess.isEmpty()
            }

            withContext(Dispatchers.Main) {
                if (hasAccess) {
                    onGranted()
                } else {
                    val userRoleText = if (isUserAdmin) "ADMIN" else (record?.role ?: "GUEST")
                    deniedMessage = LanguageManager.translate(
                        "Access Denied. Your phone number ($loggedInMobileNumber) has role '$userRoleText', which does not have access to the $requiredRole module.",
                        "पहुंच अस्वीकृत। आपके फोन नंबर ($loggedInMobileNumber) की भूमिका '$userRoleText' है, जिसके पास $requiredRole मॉड्यूल का उपयोग करने की अनुमति नहीं है।"
                    )
                    showPermissionDeniedDialog = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = LanguageManager.translate("Select Module", "मॉड्यूल चुनें"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("module_selection_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = LanguageManager.selectionBackDesc
                        )
                    }
                },
                actions = {
                    if (showAdminCard) {
                        IconButton(
                            onClick = { checkAccess("ADMIN", onNavigateToAdminPanel) },
                            modifier = Modifier.testTag("admin_panel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Database Admin",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section
            Text(
                text = LanguageManager.translate("Choose Your Workspace", "अपना कार्यक्षेत्र चुनें"),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = LanguageManager.translate(
                    "Select the module corresponding to your role to begin shift auditing and calculations.",
                    "गणना और शिफ्ट ऑडिट शुरू करने के लिए अपनी भूमिका के अनुसार उपयुक्त मॉड्यूल का चयन करें।"
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // Logged-in status badge/card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (loggedInMobileNumber.isEmpty()) 
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) 
                    else 
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("access_status_badge")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (loggedInMobileNumber.isEmpty()) Icons.Default.Info else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (loggedInMobileNumber.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (loggedInMobileNumber.isEmpty()) {
                            LanguageManager.translate(
                                "Guest Mode: Strict phone authorization is disabled. All modules available for testing.",
                                "अतिथि मोड: कड़ा फोन प्रमाणीकरण अक्षम है। परीक्षण के लिए सभी मॉड्यूल उपलब्ध हैं।"
                            )
                        } else {
                            LanguageManager.translate(
                                "Authorized User: Role checking enabled for $loggedInMobileNumber",
                                "अधिकृत उपयोगकर्ता: $loggedInMobileNumber के लिए भूमिका जांच सक्रिय है"
                            )
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = if (loggedInMobileNumber.isEmpty()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. CA Module Card
            if (showCaCard) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = { checkAccess("CA", onNavigateToCaModule) })
                        .testTag("select_ca_module_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "CA Icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = LanguageManager.translate("CA Module", "सीए (CA) मॉड्यूल"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = LanguageManager.translate(
                                    "For Customer Assistants. Configure MS & HSD nozzles and reconcile shift sales.",
                                    "कैशियर/सीए सहायकों के लिए। MS और HSD नोज़ल कॉन्फ़िगर करें और शिफ्ट बिक्री का मिलान करें।"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Navigate to CA",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 2. Managers Module Card
            if (showManagerCard) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = { checkAccess("MANAGER", onNavigateToManagersModule) })
                        .testTag("select_managers_module_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Manager Icon",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = LanguageManager.translate("Managers Module", "मैनेजर्स मॉड्यूल"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = LanguageManager.translate(
                                    "For Station Managers. Full day audit with mobilization, dry stock store sales, and cash desk tally.",
                                    "स्टेशन प्रबंधकों के लिए। ड्राई स्टॉक बिक्री, मोबिलाइजेशन और पूर्ण कैश डेस्क मिलान।"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Navigate to Manager",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // 4. Saved Reports Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onNavigateToHistory)
                    .testTag("select_saved_reports_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Saved Reports Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = LanguageManager.translate("Saved Reports", "सहेजे गए रिपोर्ट्स"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = LanguageManager.translate(
                                "Access, share, and manage previous shift audit reports.",
                                "पिछले शिफ्ट ऑडिट रिपोर्ट्स को एक्सेस, साझा और प्रबंधित करें।"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Navigate to Saved Reports",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 5. Add Staff Card
            if (showAdminCard) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = { showUserCreationPanel = true })
                        .testTag("select_add_staff_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = "Add Staff Icon",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = LanguageManager.translate("Add Staff", "कर्मचारी जोड़ें"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = LanguageManager.translate(
                                    "Authorize station staff, view registered members, and assign roles.",
                                    "स्टेशन कर्मचारियों को अधिकृत करें, पंजीकृत सदस्यों को देखें और भूमिकाएं सौंपें।"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Navigate to Add Staff",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }

    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = {
                Text(
                    text = LanguageManager.translate("Access Restricted", "पहुंच प्रतिबंधित"),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(text = deniedMessage)
            },
            confirmButton = {
                Button(
                    onClick = { showPermissionDeniedDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(LanguageManager.translate("OK", "ठीक है"))
                }
            }
        )
    }

    if (showUserCreationPanel) {
        var activeTab by remember { mutableStateOf(0) } // 0: View Staff, 1: Add New Staff
        var staffName by remember { mutableStateOf("") }
        var staffPhone by remember { mutableStateOf("") }
        var selectedRole by remember { mutableStateOf("CA") } // "CA", "MANAGER" (only two roles)
        var isSubmitting by remember { mutableStateOf(false) }

        var nameError by remember { mutableStateOf<String?>(null) }
        var phoneError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showUserCreationPanel = false },
            title = {
                Text(
                    text = LanguageManager.translate("Staff Management", "कर्मचारी प्रबंधन"),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TabRow(
                        selectedTabIndex = activeTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text(LanguageManager.translate("Registered Staff", "पंजीकृत कर्मचारी")) },
                            icon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text(LanguageManager.translate("Add Staff", "कर्मचारी जोड़ें")) },
                            icon = { Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }

                    if (activeTab == 0) {
                        // View Staff tab: "when clicked should show all registered staff"
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (staffList.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = LanguageManager.translate("No staff registered yet.", "अभी तक कोई कर्मचारी पंजीकृत नहीं है।"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                staffList.forEach { staff ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = staff.name,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = "${LanguageManager.translate("Phone", "फ़ोन")}: ${staff.phone}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${LanguageManager.translate("Password", "पासवर्ड")}: ${staff.passwordHash}",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val roleLabel = when (staff.role) {
                                                    "CA" -> LanguageManager.translate("CA", "सीए")
                                                    "MANAGER" -> LanguageManager.translate("Manager", "प्रबंधक")
                                                    else -> staff.role
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            color = MaterialTheme.colorScheme.primaryContainer,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = roleLabel,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        coroutineScope.launch(Dispatchers.IO) {
                                                            db.staffDao().deleteStaffByPhone(staff.phone)
                                                            db.phoneAccessDao().deleteAccessByPhone(staff.phone)
                                                            db.loginInfoDao().deleteLoginInfoByMobile(staff.phone)
                                                            val updatedList = db.staffDao().getAllStaff()
                                                            withContext(Dispatchers.Main) {
                                                                staffList = updatedList
                                                                Toast.makeText(context, "Staff member deleted successfully", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Staff",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Add New Staff tab
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = LanguageManager.translate(
                                    "Authorizing a phone number allows them to sign in and auto-configures their app with your pump's settings.",
                                    "एक फोन नंबर को अधिकृत करने से वे लॉगिन कर सकते हैं और उनका ऐप स्वचालित रूप से आपके पंप सेटिंग्स के साथ कॉन्फ़िगर हो जाएगा।"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Staff Name Input
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = staffName,
                                    onValueChange = {
                                        staffName = it
                                        nameError = null
                                    },
                                    label = { Text(LanguageManager.translate("Staff Name", "कर्मचारी का नाम")) },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    isError = nameError != null,
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("staff_name_input")
                                )
                                if (nameError != null) {
                                    Text(
                                        text = nameError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // Mobile Number Input
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = staffPhone,
                                    onValueChange = {
                                        phoneError = null
                                        staffPhone = it
                                    },
                                    label = { Text(LanguageManager.translate("Mobile Number", "मोबाइल नंबर")) },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                    isError = phoneError != null,
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier.fillMaxWidth().testTag("staff_phone_input")
                                )
                                if (phoneError != null) {
                                    Text(
                                        text = phoneError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // Role selection using horizontal Row of Input Chips - ONLY CA AND MANAGER ROLES ALLOWED
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = LanguageManager.translate("Assigned Role:", "सौंपी गई भूमिका:"),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf("CA", "MANAGER").forEach { role ->
                                        val isSelected = selectedRole == role
                                        val labelText = when (role) {
                                            "CA" -> LanguageManager.translate("CA", "सीए")
                                            "MANAGER" -> LanguageManager.translate("Manager", "प्रबंधक")
                                            else -> role
                                        }
                                        InputChip(
                                            selected = isSelected,
                                            onClick = { selectedRole = role },
                                            label = { Text(labelText) },
                                            leadingIcon = if (isSelected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            } else null,
                                            modifier = Modifier.weight(1f).testTag("role_chip_$role")
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    var hasError = false
                                    val cleanedPhone = staffPhone.trim().filter { it.isDigit() || it == '+' }
                                    val nameCleaned = staffName.trim()

                                    if (nameCleaned.isEmpty()) {
                                        nameError = "Name cannot be empty"
                                        hasError = true
                                    } else if (nameCleaned.length < 3) {
                                        nameError = "Name must be at least 3 characters"
                                        hasError = true
                                    }

                                    if (cleanedPhone.isEmpty()) {
                                        phoneError = "Phone number cannot be empty"
                                        hasError = true
                                    } else if (cleanedPhone.length < 10 || cleanedPhone.length > 15) {
                                        phoneError = "Please enter a valid 10-15 digit mobile number"
                                        hasError = true
                                    }

                                    if (!hasError) {
                                        isSubmitting = true
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val existingPhoneAccess = db.phoneAccessDao().getAccessByPhone(cleanedPhone)
                                                if (existingPhoneAccess != null) {
                                                    withContext(Dispatchers.Main) {
                                                        phoneError = "This phone number is already authorized"
                                                        isSubmitting = false
                                                    }
                                                    return@launch
                                                }

                                                val firstFour = if (nameCleaned.length >= 4) nameCleaned.take(4) else nameCleaned
                                                val lastSix = if (cleanedPhone.length >= 6) cleanedPhone.takeLast(6) else cleanedPhone
                                                val staffPassword = firstFour + lastSix

                                                db.phoneAccessDao().insertAccess(
                                                    PhoneAccess(phone = cleanedPhone, role = selectedRole)
                                                )

                                                val newStaff = Staff(
                                                    phone = cleanedPhone,
                                                    name = nameCleaned,
                                                    passwordHash = staffPassword,
                                                    role = selectedRole
                                                )
                                                val newLoginInfo = LoginInfo(
                                                    mobileNumber = cleanedPhone,
                                                    username = cleanedPhone,
                                                    passwordHash = staffPassword
                                                )
                                                db.staffDao().insertStaff(newStaff)
                                                db.loginInfoDao().insertLoginInfo(newLoginInfo)

                                                if (loggedInMobileNumber.isNotBlank()) {
                                                    val creatorPump = db.pumpInfoDao().getPumpInfoByMobile(loggedInMobileNumber)
                                                    if (creatorPump != null) {
                                                        db.pumpInfoDao().insertPumpInfo(
                                                            creatorPump.copy(mobileNumber = cleanedPhone)
                                                        )
                                                    }

                                                    val creatorNozzles = db.registeredNozzleDao().getNozzlesByPumpMobile(loggedInMobileNumber)
                                                    if (creatorNozzles.isNotEmpty()) {
                                                        val newNozzles = creatorNozzles.map {
                                                            it.copy(id = 0, mobileNumber = cleanedPhone)
                                                        }
                                                        db.registeredNozzleDao().insertNozzles(newNozzles)

                                                        val initialMsReadings = newNozzles.filter { it.nozzleType == "MS" }.map { rNozzle ->
                                                            MsNozzleReading(
                                                                nozzleLabel = rNozzle.label,
                                                                openingReading = rNozzle.initialReading,
                                                                closingReading = rNozzle.initialReading,
                                                                testing = 0.0,
                                                                caName = "Initial Setup",
                                                                phone = cleanedPhone,
                                                                udhar = 0.0,
                                                                kharch = 0.0,
                                                                udhariJama = 0.0,
                                                                msSales = 0.0,
                                                                hsdSales = 0.0,
                                                                timestamp = System.currentTimeMillis() - 1000,
                                                                date = "Initial Setup"
                                                            )
                                                        }
                                                        val initialHsdReadings = newNozzles.filter { it.nozzleType == "HSD" }.map { rNozzle ->
                                                            HsdNozzleReading(
                                                                nozzleLabel = rNozzle.label,
                                                                openingReading = rNozzle.initialReading,
                                                                closingReading = rNozzle.initialReading,
                                                                testing = 0.0,
                                                                caName = "Initial Setup",
                                                                phone = cleanedPhone,
                                                                udhar = 0.0,
                                                                kharch = 0.0,
                                                                udhariJama = 0.0,
                                                                msSales = 0.0,
                                                                hsdSales = 0.0,
                                                                timestamp = System.currentTimeMillis() - 1000,
                                                                date = "Initial Setup"
                                                            )
                                                        }
                                                        db.msNozzleReadingDao().insertMsNozzleReadings(initialMsReadings)
                                                        db.hsdNozzleReadingDao().insertHsdNozzleReadings(initialHsdReadings)
                                                    }
                                                }

                                                val list = db.staffDao().getAllStaff()
                                                withContext(Dispatchers.Main) {
                                                    staffList = list
                                                    isSubmitting = false
                                                    staffName = ""
                                                    staffPhone = ""
                                                    activeTab = 0 // Switch back to view list of registered staff
                                                    val firstFourVal = if (nameCleaned.length >= 4) nameCleaned.take(4) else nameCleaned
                                                    val lastSixVal = if (cleanedPhone.length >= 6) cleanedPhone.takeLast(6) else cleanedPhone
                                                    val staffPasswordVal = firstFourVal + lastSixVal
                                                    Toast.makeText(
                                                        context,
                                                        "Staff account created successfully for $nameCleaned! Password: $staffPasswordVal",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    isSubmitting = false
                                                    Toast.makeText(
                                                        context,
                                                        "Error creating user: ${e.localizedMessage}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier.fillMaxWidth().testTag("submit_user_creation_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(LanguageManager.translate("Authorize Staff", "कर्मचारी अधिकृत करें"))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showUserCreationPanel = false },
                    enabled = !isSubmitting,
                    modifier = Modifier.testTag("cancel_user_creation_button")
                ) {
                    Text(LanguageManager.translate("Close", "बंद करें"))
                }
            }
        )
    }


}
