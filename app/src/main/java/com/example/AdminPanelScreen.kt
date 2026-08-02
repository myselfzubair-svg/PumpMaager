package com.example

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Table data states
    var users by remember { mutableStateOf(emptyList<User>()) }
    var loginInfos by remember { mutableStateOf(emptyList<LoginInfo>()) }
    var savedAudits by remember { mutableStateOf(emptyList<SavedAudit>()) }
    var pumpInfos by remember { mutableStateOf(emptyList<PumpInfo>()) }
    var registeredNozzles by remember { mutableStateOf(emptyList<RegisteredNozzle>()) }
    var msReadings by remember { mutableStateOf(emptyList<MsNozzleReading>()) }
    var hsdReadings by remember { mutableStateOf(emptyList<HsdNozzleReading>()) }
    var phoneAccesses by remember { mutableStateOf(emptyList<PhoneAccess>()) }
    var staffList by remember { mutableStateOf(emptyList<Staff>()) }

    var showDeleteConfirmAll by remember { mutableStateOf<Int?>(null) } // null or tab index
    var showDeleteAllDatabaseConfirm by remember { mutableStateOf(false) } // show dialog to delete all tables at once
    var selectedRowDetails by remember { mutableStateOf<Any?>(null) } // Generic object for popup detail dialog

    fun reloadAll() {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val u = db.userDao().getAllUsers()
                val l = db.loginInfoDao().getAllLoginInfo()
                val p = db.pumpInfoDao().getAllPumpInfo()
                val r = db.registeredNozzleDao().getAllRegisteredNozzles()
                
                val a = try {
                    db.savedAuditDao().getAllAudits().first()
                } catch (e: Exception) {
                    emptyList()
                }
                
                val ms = try {
                    db.msNozzleReadingDao().getAllMsNozzleReadings().first()
                } catch (e: Exception) {
                    emptyList()
                }

                val hsd = try {
                    db.hsdNozzleReadingDao().getAllHsdNozzleReadings().first()
                } catch (e: Exception) {
                    emptyList()
                }

                val pa = try {
                    db.phoneAccessDao().getAllAccess()
                } catch (e: Exception) {
                    emptyList()
                }

                val s = try {
                    db.staffDao().getAllStaff()
                } catch (e: Exception) {
                    emptyList()
                }

                withContext(Dispatchers.Main) {
                    users = u
                    loginInfos = l
                    pumpInfos = p
                    registeredNozzles = r
                    savedAudits = a
                    msReadings = ms
                    hsdReadings = hsd
                    phoneAccesses = pa
                    staffList = s
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    Toast.makeText(context, "Reload error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadAll()
    }

    val tablesList = listOf(
        "Users" to users.size,
        "Login Info" to loginInfos.size,
        "Audits" to savedAudits.size,
        "Pumps" to pumpInfos.size,
        "Nozzles" to registeredNozzles.size,
        "MS Readings" to msReadings.size,
        "HSD Readings" to hsdReadings.size,
        "Phone Access" to phoneAccesses.size,
        "Staff" to staffList.size
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Admin icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Database Admin Panel",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Inspect and edit live Room SQLite tables",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { reloadAll() }, modifier = Modifier.testTag("admin_refresh_button")) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh tables"
                        )
                    }
                    IconButton(
                        onClick = { showDeleteAllDatabaseConfirm = true },
                        modifier = Modifier.testTag("admin_clear_all_db_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear entire database",
                            tint = MaterialTheme.colorScheme.error
                        )
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
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Clear active table
                    OutlinedButton(
                        onClick = { showDeleteConfirmAll = selectedTab },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().testTag("admin_clear_table_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Clear table icon", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Table", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search & Filter Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter table rows...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search text")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("admin_search_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Scrollable tabs for all 7 tables
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp,
                modifier = Modifier.testTag("admin_tabs_row")
            ) {
                tablesList.forEachIndexed { index, (tableName, count) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            searchQuery = ""
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val tabIcon = when (index) {
                                    0 -> Icons.Default.Person
                                    1 -> Icons.Default.Phone
                                    2 -> Icons.Default.Assessment
                                    3 -> Icons.Default.Place
                                    4 -> Icons.Default.Pin
                                    5 -> Icons.Default.GasMeter
                                    6 -> Icons.Default.LocalGasStation
                                    else -> Icons.Default.Shield
                                }
                                Icon(tabIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "$tableName ($count)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        },
                        modifier = Modifier.testTag("admin_tab_$index")
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> { // Users
                        val filteredUsers = users.filter {
                            it.username.contains(searchQuery, ignoreCase = true) ||
                            it.passwordHash.contains(searchQuery, ignoreCase = true)
                        }
                        if (filteredUsers.isEmpty()) {
                            EmptyPlaceholder("No users found")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredUsers) { user ->
                                    UserAdminRow(
                                        user = user,
                                        onDelete = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                db.userDao().deleteUserByUsername(user.username)
                                                withContext(Dispatchers.Main) { reloadAll() }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    1 -> { // Login Info
                        val filteredLoginInfos = loginInfos.filter {
                            it.username.contains(searchQuery, ignoreCase = true) ||
                            it.mobileNumber.contains(searchQuery, ignoreCase = true)
                        }
                        if (filteredLoginInfos.isEmpty()) {
                            EmptyPlaceholder("No login info records found")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredLoginInfos) { info ->
                                    LoginInfoAdminRow(
                                        info = info,
                                        onDelete = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                db.loginInfoDao().deleteLoginInfoByMobile(info.mobileNumber)
                                                withContext(Dispatchers.Main) { reloadAll() }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    2 -> { // Saved Audits
                        val filteredAudits = savedAudits.filter {
                            it.caName.contains(searchQuery, ignoreCase = true) ||
                            it.meterNo.contains(searchQuery, ignoreCase = true) ||
                            it.auditType.contains(searchQuery, ignoreCase = true) ||
                            it.summaryText.contains(searchQuery, ignoreCase = true) ||
                            it.date.contains(searchQuery, ignoreCase = true)
                        }
                        if (filteredAudits.isEmpty()) {
                            EmptyPlaceholder("No saved audits found")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredAudits) { audit ->
                                    SavedAuditAdminRow(
                                        audit = audit,
                                        onClick = { selectedRowDetails = audit },
                                        onDelete = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                db.savedAuditDao().deleteAuditById(audit.id)
                                                withContext(Dispatchers.Main) { reloadAll() }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    3 -> { // Pumps Info
                        val filteredPumps = pumpInfos.filter {
                            it.pumpName.contains(searchQuery, ignoreCase = true) ||
                            it.mobileNumber.contains(searchQuery, ignoreCase = true) ||
                            it.msNozzleLabels.contains(searchQuery, ignoreCase = true) ||
                            it.hsdNozzleLabels.contains(searchQuery, ignoreCase = true)
                        }
                        if (filteredPumps.isEmpty()) {
                            EmptyPlaceholder("No pumps configured")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredPumps) { pump ->
                                    PumpInfoAdminRow(
                                        pump = pump,
                                        onDelete = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                db.pumpInfoDao().deletePumpInfoByMobile(pump.mobileNumber)
                                                withContext(Dispatchers.Main) { reloadAll() }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    4 -> { // Registered Nozzles
                        val filteredNozzles = registeredNozzles.filter {
                            it.label.contains(searchQuery, ignoreCase = true) ||
                            it.mobileNumber.contains(searchQuery, ignoreCase = true) ||
                            it.nozzleType.contains(searchQuery, ignoreCase = true)
                        }
                        if (filteredNozzles.isEmpty()) {
                            EmptyPlaceholder("No nozzles registered")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredNozzles) { nozzle ->
                                    RegisteredNozzleAdminRow(
                                        nozzle = nozzle,
                                        onDelete = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                db.registeredNozzleDao().deleteNozzlesByPumpMobile(nozzle.mobileNumber)
                                                withContext(Dispatchers.Main) { reloadAll() }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    5 -> { // MS Readings
                        val filteredMs = msReadings.filter {
                            it.nozzleLabel.contains(searchQuery, ignoreCase = true) ||
                            it.caName.contains(searchQuery, ignoreCase = true) ||
                            it.phone.contains(searchQuery, ignoreCase = true) ||
                            it.date.contains(searchQuery, ignoreCase = true)
                        }
                        if (filteredMs.isEmpty()) {
                            EmptyPlaceholder("No MS readings found")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredMs) { reading ->
                                    MsReadingAdminRow(
                                        reading = reading,
                                        onClick = { selectedRowDetails = reading },
                                        onDelete = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                db.msNozzleReadingDao().deleteMsNozzleReadingById(reading.id)
                                                withContext(Dispatchers.Main) { reloadAll() }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    6 -> { // HSD Readings
                        val filteredHsd = hsdReadings.filter {
                            it.nozzleLabel.contains(searchQuery, ignoreCase = true) ||
                            it.caName.contains(searchQuery, ignoreCase = true) ||
                            it.phone.contains(searchQuery, ignoreCase = true) ||
                            it.date.contains(searchQuery, ignoreCase = true)
                        }
                        if (filteredHsd.isEmpty()) {
                            EmptyPlaceholder("No HSD readings found")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredHsd) { reading ->
                                    HsdReadingAdminRow(
                                        reading = reading,
                                        onClick = { selectedRowDetails = reading },
                                        onDelete = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                db.hsdNozzleReadingDao().deleteHsdNozzleReadingById(reading.id)
                                                withContext(Dispatchers.Main) { reloadAll() }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    7 -> { // Phone Access Controls
                        PhoneAccessTabContent(
                            phoneAccesses = phoneAccesses,
                            searchQuery = searchQuery,
                            onAddAccess = { phone, role ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    db.phoneAccessDao().insertAccess(PhoneAccess(phone, role))
                                    withContext(Dispatchers.Main) {
                                        reloadAll()
                                    }
                                }
                            },
                            onDelete = { access ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    db.phoneAccessDao().deleteAccessByPhone(access.phone)
                                    db.loginInfoDao().deleteLoginInfoByMobile(access.phone)
                                    db.staffDao().deleteStaffByPhone(access.phone)
                                    withContext(Dispatchers.Main) { reloadAll() }
                                }
                            }
                        )
                    }
                    8 -> { // Staff List
                        StaffTabContent(
                            staffList = staffList,
                            searchQuery = searchQuery,
                            onAddStaff = { name, phone, role ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    val cleanedPhone = phone.filter { it.isDigit() || it == '+' }
                                    val cleanedName = name.trim()
                                    val firstFour = if (cleanedName.length >= 4) cleanedName.take(4) else cleanedName
                                    val lastSix = if (cleanedPhone.length >= 6) cleanedPhone.takeLast(6) else cleanedPhone
                                    val staffPassword = cleanedPhone

                                    // Insert phone access
                                    db.phoneAccessDao().insertAccess(PhoneAccess(cleanedPhone, role))
                                    // Insert staff
                                    db.staffDao().insertStaff(Staff(cleanedPhone, cleanedName, staffPassword, role))
                                    // Insert login info
                                    db.loginInfoDao().insertLoginInfo(LoginInfo(cleanedPhone, cleanedPhone, staffPassword))
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Staff added! Username & Password are set to: $staffPassword", Toast.LENGTH_LONG).show()
                                        reloadAll()
                                    }
                                }
                            },
                            onDelete = { staff ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    db.phoneAccessDao().deleteAccessByPhone(staff.phone)
                                    db.loginInfoDao().deleteLoginInfoByMobile(staff.phone)
                                    db.staffDao().deleteStaffByPhone(staff.phone)
                                    withContext(Dispatchers.Main) { reloadAll() }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Drop Table Confirmation Dialog
        if (showDeleteConfirmAll != null) {
            val tabIdx = showDeleteConfirmAll!!
            val tableName = when (tabIdx) {
                0 -> "USERS"
                1 -> "LOGIN_INFO"
                2 -> "SAVED_AUDITS"
                3 -> "PUMP_INFO"
                4 -> "REGISTERED_NOZZLES"
                5 -> "MS_NOZZLE_READINGS"
                6 -> "HSD_NOZZLE_READINGS"
                7 -> "PHONE_ACCESS"
                else -> "STAFF"
            }
            AlertDialog(
                onDismissRequest = { showDeleteConfirmAll = null },
                title = { Text("Reset Table: $tableName", fontWeight = FontWeight.Bold) },
                text = { Text("Are you absolutely sure you want to delete ALL records from the $tableName table? This operation cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                when (tabIdx) {
                                    0 -> db.userDao().clearAllUsers()
                                    1 -> db.loginInfoDao().clearAllLoginInfo()
                                    2 -> db.savedAuditDao().clearAllAudits()
                                    3 -> db.pumpInfoDao().clearAllPumpInfo()
                                    4 -> db.registeredNozzleDao().clearAllNozzles()
                                    5 -> db.msNozzleReadingDao().clearAllMsNozzleReadings()
                                    6 -> db.hsdNozzleReadingDao().clearAllHsdNozzleReadings()
                                    7 -> db.phoneAccessDao().clearAllAccess()
                                    8 -> db.staffDao().clearAllStaff()
                                }
                                withContext(Dispatchers.Main) {
                                    showDeleteConfirmAll = null
                                    reloadAll()
                                    Toast.makeText(context, "Table $tableName cleared successfully!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("admin_delete_confirm_button")
                    ) {
                        Text("Delete All", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmAll = null }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Drop All Tables Confirmation Dialog
        if (showDeleteAllDatabaseConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteAllDatabaseConfirm = false },
                title = { Text("Delete All Tables / Reset Database", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                text = { Text("Are you absolutely sure you want to delete ALL rows and data from ALL tables in the database? This will reset the entire application state. This action is irreversible.") },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    db.clearAllTables()
                                    withContext(Dispatchers.Main) {
                                        showDeleteAllDatabaseConfirm = false
                                        reloadAll()
                                        Toast.makeText(context, "All database tables cleared completely!", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        showDeleteAllDatabaseConfirm = false
                                        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("admin_clear_all_db_confirm_button")
                    ) {
                        Text("Reset Entire Database", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllDatabaseConfirm = false }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Expanded Row Details Dialog for Rich Entities
        if (selectedRowDetails != null) {
            val item = selectedRowDetails!!
            AlertDialog(
                onDismissRequest = { selectedRowDetails = null },
                title = {
                    Text(
                        text = when (item) {
                            is SavedAudit -> "Audit: ${item.caName}"
                            is MsNozzleReading -> "MS Nozzle Reading"
                            is HsdNozzleReading -> "HSD Nozzle Reading"
                            else -> "Details"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (item) {
                            is SavedAudit -> {
                                DetailField("Audit ID", item.id.toString())
                                DetailField("Date", item.date)
                                DetailField("Cashier Name", item.caName)
                                DetailField("Meter Reference No", item.meterNo)
                                DetailField("Audit Type", item.auditType)
                                DetailField("Summary Text", item.summaryText)
                                DetailField("HTML Template Bytes", "${item.htmlContent.length} chars")
                            }
                            is MsNozzleReading -> {
                                DetailField("ID", item.id.toString())
                                DetailField("Nozzle Label", item.nozzleLabel)
                                DetailField("Date", item.date)
                                DetailField("Opening Reading", item.openingReading.toString())
                                DetailField("Closing Reading", item.closingReading.toString())
                                DetailField("Testing Volume", "${item.testing} L")
                                DetailField("Cashier (CA)", item.caName)
                                DetailField("Phone Number", item.phone)
                                DetailField("Udhar (Credit)", item.udhar.toString())
                                DetailField("Kharch (Expenses)", item.kharch.toString())
                                DetailField("Udhari Jama", item.udhariJama.toString())
                                DetailField("MS Sales", "${item.msSales} L")
                                DetailField("HSD Sales", "${item.hsdSales} L")
                                DetailField("Timestamp", SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)))
                            }
                            is HsdNozzleReading -> {
                                DetailField("ID", item.id.toString())
                                DetailField("Nozzle Label", item.nozzleLabel)
                                DetailField("Date", item.date)
                                DetailField("Opening Reading", item.openingReading.toString())
                                DetailField("Closing Reading", item.closingReading.toString())
                                DetailField("Testing Volume", "${item.testing} L")
                                DetailField("Cashier (CA)", item.caName)
                                DetailField("Phone Number", item.phone)
                                DetailField("Udhar (Credit)", item.udhar.toString())
                                DetailField("Kharch (Expenses)", item.kharch.toString())
                                DetailField("Udhari Jama", item.udhariJama.toString())
                                DetailField("MS Sales", "${item.msSales} L")
                                DetailField("HSD Sales", "${item.hsdSales} L")
                                DetailField("Timestamp", SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { selectedRowDetails = null }) {
                        Text("Close")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun DetailField(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun UserAdminRow(user: User, onDelete: () -> Unit) {
    var showPassword by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(text = user.username, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (showPassword) "Pass: ${user.passwordHash}" else "Pass: ••••••••",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password visibility",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { showPassword = !showPassword }
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete user", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun LoginInfoAdminRow(info: LoginInfo, onDelete: () -> Unit) {
    val dateStr = remember(info.verifiedAt) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(info.verifiedAt))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
                Column {
                    Text(text = info.mobileNumber, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "Linked to: @${info.username}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (info.passwordHash.isNotEmpty()) {
                        Text(
                            text = "Password: ${info.passwordHash}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Text(
                        text = "Verified: $dateStr",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun SavedAuditAdminRow(audit: SavedAudit, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = audit.caName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text(audit.auditType, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    Text(
                        text = "Date: ${audit.date} | Meter: ${audit.meterNo}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = audit.summaryText,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun PumpInfoAdminRow(pump: PumpInfo, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(text = pump.pumpName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "Mobile: ${pump.mobileNumber}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "MS Tanks: ${pump.numMsTanks} | HSD Tanks: ${pump.numHsdTanks}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        text = "MS Nozzles (${pump.numMsNozzles}): ${pump.msNozzleLabels}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "HSD Nozzles (${pump.numHsdNozzles}): ${pump.hsdNozzleLabels}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun RegisteredNozzleAdminRow(nozzle: RegisteredNozzle, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
                Column {
                    Text(text = nozzle.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "Type: ${nozzle.nozzleType} | Index: ${nozzle.nozzleIndex}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Pump Mobile: ${nozzle.mobileNumber}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun MsReadingAdminRow(reading: MsNozzleReading, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GasMeter, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = reading.nozzleLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text("MS", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    Text(
                        text = "CA: ${reading.caName} | Date: ${reading.date}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        text = "Sales: ${reading.msSales} L | Open: ${reading.openingReading} | Close: ${reading.closingReading}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun HsdReadingAdminRow(reading: HsdNozzleReading, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = reading.nozzleLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text("HSD", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    Text(
                        text = "CA: ${reading.caName} | Date: ${reading.date}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        text = "Sales: ${reading.hsdSales} L | Open: ${reading.openingReading} | Close: ${reading.closingReading}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun PhoneAccessTabContent(
    phoneAccesses: List<PhoneAccess>,
    searchQuery: String,
    onAddAccess: (String, String) -> Unit,
    onDelete: (PhoneAccess) -> Unit
) {
    var newPhone by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("MANAGER") } // "MANAGER", "CA", "BOTH"
    var expandedDropdown by remember { mutableStateOf(false) }

    val filteredAccesses = phoneAccesses.filter {
        it.phone.contains(searchQuery, ignoreCase = true) ||
        it.role.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Add Access Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("add_access_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Grant Module Access",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("+919876543210") },
                        modifier = Modifier.weight(1f).testTag("access_phone_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Box(modifier = Modifier.wrapContentSize()) {
                        OutlinedButton(
                            onClick = { expandedDropdown = true },
                            modifier = Modifier.height(56.dp).testTag("role_dropdown_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(selectedRole)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select role")
                        }
                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("MANAGER") },
                                onClick = {
                                    selectedRole = "MANAGER"
                                    expandedDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("CA") },
                                onClick = {
                                    selectedRole = "CA"
                                    expandedDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("BOTH") },
                                onClick = {
                                    selectedRole = "BOTH"
                                    expandedDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("ADMIN") },
                                onClick = {
                                    selectedRole = "ADMIN"
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        if (newPhone.isNotBlank()) {
                            onAddAccess(newPhone.trim(), selectedRole)
                            newPhone = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("add_access_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Access Permission")
                }
            }
        }

        HorizontalDivider()

        if (filteredAccesses.isEmpty()) {
            EmptyPlaceholder("No phone access configurations found")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredAccesses) { access ->
                    PhoneAccessAdminRow(
                        access = access,
                        onDelete = { onDelete(access) }
                    )
                }
            }
        }
    }
}

@Composable
fun PhoneAccessAdminRow(access: PhoneAccess, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                }
                Column {
                    Text(
                        text = access.phone,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Access Role: ${access.role}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete access", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun StaffAdminRow(staff: Staff, onDelete: () -> Unit) {
    var showPassword by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(
                        text = staff.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Phone: ${staff.phone} | Role: ${staff.role}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (showPassword) "Pass: ${staff.passwordHash}" else "Pass: ••••••••",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password visibility",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { showPassword = !showPassword }
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete staff", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun StaffTabContent(
    staffList: List<Staff>,
    searchQuery: String,
    onAddStaff: (String, String, String) -> Unit,
    onDelete: (Staff) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("MANAGER") }
    var expandedDropdown by remember { mutableStateOf(false) }

    val filteredStaff = staffList.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.phone.contains(searchQuery, ignoreCase = true) ||
        it.role.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().testTag("add_staff_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add New Staff",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Staff Name") },
                    placeholder = { Text("E.g. John Doe") },
                    modifier = Modifier.fillMaxWidth().testTag("staff_name_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("E.g. 9876543210") },
                        modifier = Modifier.weight(1f).testTag("staff_phone_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Box(modifier = Modifier.wrapContentSize()) {
                        OutlinedButton(
                            onClick = { expandedDropdown = true },
                            modifier = Modifier.height(56.dp).testTag("staff_role_dropdown_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(selectedRole)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select role")
                        }
                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false }
                        ) {
                            listOf("MANAGER", "CA", "BOTH", "ADMIN").forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role) },
                                    onClick = {
                                        selectedRole = role
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (newName.isNotBlank() && newPhone.isNotBlank()) {
                            onAddStaff(newName.trim(), newPhone.trim(), selectedRole)
                            newName = ""
                            newPhone = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("add_staff_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Staff Member")
                }
            }
        }

        HorizontalDivider()

        if (filteredStaff.isEmpty()) {
            EmptyPlaceholder("No staff members found")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredStaff) { staff ->
                    StaffAdminRow(
                        staff = staff,
                        onDelete = { onDelete(staff) }
                    )
                }
            }
        }
    }
}


