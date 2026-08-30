package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      var isDarkTheme by remember { mutableStateOf(false) }
      MyApplicationTheme(darkTheme = isDarkTheme) {
        MainNavigationFlow(isDarkTheme = isDarkTheme, onThemeChange = { isDarkTheme = it })
      }
    }
  }
}

enum class Screen {
  Login, Welcome, ModuleSelection, NozzleSelection, 
  Calculator, FullDayCalculator, History, ManagerDashboard, 
  DailySalesReport, TtReceiptEntry, TtEntryReport, MonthlyExpensesReport, 
  MonthlyCreditReport, MonthlyUdhariJamaReport, CustomerUdhariLedgerReport,
  DailyPumpData, StaffManagement
}

@Composable
fun MainNavigationFlow(
  isDarkTheme: Boolean,
  onThemeChange: (Boolean) -> Unit
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

  val sharedPrefs = remember { context.getSharedPreferences("pump_manager_prefs", android.content.Context.MODE_PRIVATE) }
  val savedPumpName = remember { sharedPrefs.getString("session_pump_name", "") ?: "" }
  val savedUsername = remember { sharedPrefs.getString("session_username", "") ?: "" }
  val savedMobile = remember { sharedPrefs.getString("session_mobile", "") ?: "" }
  val savedAdminPhone = remember { sharedPrefs.getString("session_admin_phone", "") ?: "" }
  val savedAccountId = remember { sharedPrefs.getString("session_account_id", "") ?: "" }
  val savedRole = remember { sharedPrefs.getString("session_role", "") ?: "" }

  val currentDate = remember {
    java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())
  }
  var currentScreen by remember { mutableStateOf(if (savedPumpName.isNotEmpty()) Screen.Welcome else Screen.Login) }
  var selectedDate by remember { mutableStateOf(currentDate) }
  var selectedCaName by remember { mutableStateOf("") }
  var loggedInUsername by remember { mutableStateOf(savedUsername) }
  var loggedInMobileNumber by remember { mutableStateOf(savedMobile) }
  var loggedInAdminPhone by remember { mutableStateOf(savedAdminPhone) }
  var loggedInAccountId by remember { mutableStateOf(savedAccountId) }
  var userRole by remember { mutableStateOf(savedRole) }
  var loggedInPumpName by remember { mutableStateOf(if (savedPumpName.isNotEmpty()) savedPumpName else "D R Inamdar Petroleum") }
  var allRegisteredNozzles by remember { mutableStateOf(emptyList<com.example.database.RegisteredNozzle>()) }
  var selectedNozzleList by remember { mutableStateOf(emptyList<com.example.database.RegisteredNozzle>()) }
  var isManagersModuleFlow by remember { mutableStateOf(savedRole.isNotEmpty() && savedRole.uppercase() != "CA") }

  LaunchedEffect(loggedInAdminPhone) {
    if (loggedInAdminPhone.isNotEmpty()) {
      try {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          val registeredList = com.example.database.SupabaseRepository.getRegisteredNozzles(loggedInAdminPhone)
          kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            allRegisteredNozzles = registeredList
          }
        }
      } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error in nozzle prefetch: ${e.message}")
      }
    }
  }
  
  var appLanguage by remember { mutableStateOf(AppLanguage.English) }
  LanguageManager.currentLanguage = appLanguage

  val performLogout: () -> Unit = {
    sharedPrefs.edit().apply {
      remove("session_username"); remove("session_pump_name"); remove("session_mobile")
      remove("session_admin_phone"); remove("session_account_id"); remove("session_role"); apply()
    }
    currentScreen = Screen.Login
  }

  if (currentScreen == Screen.Login) {
    LoginScreen(
      onLoginSuccess = { username, pumpName, msLabels, hsdLabels, mobileNumber, adminPhone, role, accountId ->
        loggedInUsername = username; loggedInMobileNumber = mobileNumber
        loggedInAdminPhone = adminPhone; userRole = role; loggedInPumpName = pumpName
        loggedInAccountId = accountId
        sharedPrefs.edit().apply {
          putString("session_username", username); putString("session_pump_name", pumpName)
          putString("session_mobile", mobileNumber); putString("session_admin_phone", adminPhone)
          putString("session_account_id", accountId); putString("session_role", role); apply()
        }
        
        // Always land on Home (Welcome) screen after login as requested
        isManagersModuleFlow = role.uppercase() != "CA"
        currentScreen = Screen.Welcome
      },
      onSkipLogin = { loggedInMobileNumber = ""; currentScreen = Screen.Welcome },
      isDarkTheme = isDarkTheme, onThemeChange = onThemeChange
    )
    return
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet(
        drawerContainerColor = Color.White,
        drawerContentColor = Color(0xFF0F172A),
        modifier = Modifier.width(320.dp)
      ) {
        Box(
          modifier = Modifier.fillMaxWidth().height(200.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF2563EB), Color(0xFF1E3A8A))))
            .padding(24.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = Color.White.copy(alpha = 0.2f), shape = CircleShape, modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            }
            Column {
              Text("Welcome to", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
              Text(loggedInPumpName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
              Spacer(Modifier.height(4.dp))
              Text("Active User: $loggedInUsername", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
              Text(if (userRole.isEmpty()) "Administrator" else userRole, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
          }
          Surface(color = Color(0xFF10B981), shape = CircleShape, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 12.dp)) {
              Text("● Online", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
          }
        }
        Spacer(Modifier.height(16.dp))
        val menuItems = remember(userRole) {
            val list = mutableListOf(
              "Home" to Icons.Default.Home
            )
            
            if (userRole == "ADMIN" || userRole == "MANAGER" || userRole.isEmpty()) {
              list.add("Morning Density" to Icons.Default.Opacity)
              list.add("Fuel Rates" to Icons.Default.CurrencyRupee)
              list.add("Opening Stock" to Icons.Default.Storage)
              list.add("TT Receipt" to Icons.Default.LocalShipping)
            }
            
            list.add("CA Module" to Icons.Default.LocalGasStation)
            
            if (userRole == "ADMIN" || userRole == "MANAGER" || userRole.isEmpty()) {
              list.add("Manager Module" to Icons.Default.Person)
              list.add("Daily Audit" to Icons.Default.Assessment)
              list.add("Settings" to Icons.Default.Settings)
            }
            
            list.add("Logout" to Icons.Default.Logout)
            list
        }

        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
          menuItems.forEach { (label, icon) ->
            NavigationDrawerItem(
              label = { Text(label, fontWeight = FontWeight.SemiBold) },
              icon = { Icon(icon, null, modifier = Modifier.size(22.dp)) },
              selected = false,
              onClick = {
                coroutineScope.launch { drawerState.close() }
                if (label == "Logout") performLogout()
                else if (label == "Home") currentScreen = Screen.Welcome
                else if (label == "Manager Module") { isManagersModuleFlow = true; currentScreen = Screen.ManagerDashboard }
                else if (label == "CA Module") { isManagersModuleFlow = false; currentScreen = Screen.NozzleSelection }
                else if (label == "Daily Audit") { isManagersModuleFlow = true; currentScreen = Screen.FullDayCalculator }
                else if (label == "Morning Density" || label == "Fuel Rates" || label == "Opening Stock") { currentScreen = Screen.DailyPumpData }
                else if (label == "TT Receipt") { isManagersModuleFlow = true; currentScreen = Screen.TtReceiptEntry }
              },
              shape = RoundedCornerShape(12.dp),
              colors = NavigationDrawerItemDefaults.colors(
                unselectedContainerColor = Color.Transparent, unselectedIconColor = Color(0xFF64748B), unselectedTextColor = Color(0xFF1E293B)
              )
            )
          }
        }
      }
    }
  ) {
    Scaffold(
      bottomBar = {
        Surface(
          modifier = Modifier.padding(16.dp).fillMaxWidth().height(72.dp),
          shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 8.dp
        ) {
          Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            BottomNavItem("Home", Icons.Default.Home, currentScreen == Screen.Welcome) { currentScreen = Screen.Welcome }
            BottomNavItem("Reports", Icons.Default.Assessment, currentScreen == Screen.ManagerDashboard) { isManagersModuleFlow = true; currentScreen = Screen.ManagerDashboard }
            BottomNavItem("Profile", Icons.Default.Person, false) {}
          }
        }
      }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            MainNavigationContent(
                currentScreen = currentScreen,
                onScreenChange = { currentScreen = it },
                loggedInUsername = loggedInUsername,
                loggedInPumpName = loggedInPumpName,
                loggedInMobileNumber = loggedInMobileNumber,
                loggedInAdminPhone = loggedInAdminPhone,
                loggedInAccountId = loggedInAccountId,
                userRole = userRole,
                selectedDate = selectedDate,
                onDateChange = { selectedDate = it },
                selectedCaName = selectedCaName,
                onCaNameChange = { selectedCaName = it },
                appLanguage = appLanguage,
                onLanguageChange = { appLanguage = it },
                isDarkTheme = isDarkTheme,
                onThemeChange = onThemeChange,
                performLogout = performLogout,
                onDrawerOpen = { coroutineScope.launch { drawerState.open() } },
                onNavigateToHistory = { currentScreen = Screen.History },
                onNavigateToReports = { isManagersModuleFlow = true; currentScreen = Screen.ManagerDashboard },
                onNavigateToDailySalesReport = { isManagersModuleFlow = true; currentScreen = Screen.DailySalesReport },
                onNavigateToAllModules = { currentScreen = Screen.ModuleSelection },
                onNavigateToDailyPumpData = { currentScreen = Screen.DailyPumpData },
                isManagersModuleFlow = isManagersModuleFlow,
                onManagersModuleFlowChange = { isManagersModuleFlow = it },
                selectedNozzleList = selectedNozzleList,
                onNozzleListChange = { selectedNozzleList = it }
            )
        }
    }
  }
}

@Composable
fun BottomNavItem(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Column(
      modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (isSelected) Color(0xFF2563EB) else Color(0xFF94A3B8), modifier = Modifier.size(24.dp))
        Text(label, fontSize = 10.sp, color = if (isSelected) Color(0xFF2563EB) else Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MainNavigationContent(
  currentScreen: Screen,
  onScreenChange: (Screen) -> Unit,
  loggedInUsername: String,
  loggedInPumpName: String,
  loggedInMobileNumber: String,
  loggedInAdminPhone: String,
  loggedInAccountId: String,
  userRole: String,
  selectedDate: String,
  onDateChange: (String) -> Unit,
  selectedCaName: String,
  onCaNameChange: (String) -> Unit,
  appLanguage: AppLanguage,
  onLanguageChange: (AppLanguage) -> Unit,
  isDarkTheme: Boolean,
  onThemeChange: (Boolean) -> Unit,
  performLogout: () -> Unit,
  onDrawerOpen: () -> Unit,
  onNavigateToHistory: () -> Unit,
  onNavigateToReports: () -> Unit,
  onNavigateToDailySalesReport: () -> Unit,
  onNavigateToAllModules: () -> Unit,
  onNavigateToDailyPumpData: () -> Unit,
  isManagersModuleFlow: Boolean,
  onManagersModuleFlowChange: (Boolean) -> Unit,
  selectedNozzleList: List<com.example.database.RegisteredNozzle>,
  onNozzleListChange: (List<com.example.database.RegisteredNozzle>) -> Unit
) {
  when (currentScreen) {
    Screen.Welcome -> {
      WelcomeScreen(
        onNavigateToCalculator = { 
            onManagersModuleFlowChange(false)
            onScreenChange(Screen.NozzleSelection) 
        },
        language = appLanguage,
        onLanguageChange = onLanguageChange,
        isDarkTheme = isDarkTheme,
        onThemeChange = onThemeChange,
        pumpName = loggedInPumpName,
        loggedInUsername = loggedInUsername,
        loggedInMobileNumber = loggedInMobileNumber,
        adminPhone = loggedInAdminPhone,
        userRoleFromSession = userRole,
        onLogout = performLogout,
        onDrawerOpen = onDrawerOpen,
        onNavigateToHistory = onNavigateToHistory,
        onNavigateToReports = onNavigateToReports,
        onNavigateToDailySalesReport = onNavigateToDailySalesReport,
        onNavigateToAllModules = onNavigateToAllModules,
        onNavigateToDailyPumpData = onNavigateToDailyPumpData,
        onNavigateToStaffManagement = { onScreenChange(Screen.StaffManagement) }
      )
    }
    Screen.ModuleSelection -> {
      ModuleSelectionScreen(
        loggedInMobileNumber = loggedInMobileNumber,
        adminPhone = loggedInAdminPhone,
        pumpName = loggedInPumpName,
        userRoleFromSession = userRole,
        onNavigateToCaModule = { onScreenChange(Screen.NozzleSelection) },
        onNavigateToManagersModule = { onScreenChange(Screen.ManagerDashboard) },
        onNavigateToHistory = { onScreenChange(Screen.History) },
        onBack = { onScreenChange(Screen.Welcome) },
        onLogout = performLogout
      )
    }
    Screen.NozzleSelection -> {
        NozzleSelectionScreen(
            username = loggedInUsername,
            adminPhone = loggedInAdminPhone,
            onNavigateToCalculator = { nozzles, date, caName ->
                onNozzleListChange(nozzles)
                onDateChange(date)
                onCaNameChange(caName)
                
                if (isManagersModuleFlow) {
                    onScreenChange(Screen.FullDayCalculator)
                } else {
                    onScreenChange(Screen.Calculator)
                }
            },
            onNavigateToFullDay = { 
                onScreenChange(Screen.FullDayCalculator)
            },
            onBack = { onScreenChange(Screen.Welcome) },
            onLogout = performLogout
        )
    }
    Screen.Calculator -> {
        CalculatorScreen(
            onBack = { onScreenChange(Screen.NozzleSelection) },
            onSaveSuccess = { onScreenChange(Screen.Welcome) },
            date = selectedDate,
            caName = selectedCaName,
            meterNo = "",
            selectedNozzles = selectedNozzleList,
            phone = loggedInUsername,
            adminPhone = loggedInAdminPhone,
            onLogout = performLogout
        )
    }
    Screen.FullDayCalculator -> {
        FullDayCalculatorScreen(
            onBack = { onScreenChange(Screen.Welcome) },
            date = selectedDate,
            adminPhone = loggedInAdminPhone,
            onLogout = performLogout
        )
    }
    Screen.ManagerDashboard -> {
      ManagerDashboardScreen(
        adminPhone = loggedInAdminPhone,
        onBack = { onScreenChange(Screen.Welcome) },
        onNavigateToDailyAudit = { dateStr ->
            onDateChange(dateStr)
            onScreenChange(Screen.FullDayCalculator)
        },
        onNavigateToDailySalesReport = { onScreenChange(Screen.DailySalesReport) },
        onNavigateToTtEntryReport = { onScreenChange(Screen.TtEntryReport) },
        onNavigateToMonthlyExpensesReport = { onScreenChange(Screen.MonthlyExpensesReport) },
        onNavigateToMonthlyCreditReport = { onScreenChange(Screen.MonthlyCreditReport) },
        onNavigateToMonthlyUdhariJamaReport = { onScreenChange(Screen.MonthlyUdhariJamaReport) },
        onNavigateToCustomerUdhariLedgerReport = { onScreenChange(Screen.CustomerUdhariLedgerReport) },
        onLogout = performLogout
      )
    }
    Screen.History -> {
      HistoryScreen(adminPhone = loggedInAdminPhone, onBack = { onScreenChange(Screen.Welcome) }, onLogout = performLogout)
    }
    Screen.DailySalesReport -> {
        DailySalesReportScreen(onBack = { onScreenChange(Screen.ManagerDashboard) }, adminPhone = loggedInAdminPhone, onLogout = performLogout)
    }
    Screen.TtReceiptEntry -> {
        TtReceiptEntryScreen(adminPhone = loggedInAdminPhone, onBack = { onScreenChange(Screen.ManagerDashboard) }, onLogout = performLogout)
    }
    Screen.TtEntryReport -> {
        TtEntryReportScreen(adminPhone = loggedInAdminPhone, onBack = { onScreenChange(Screen.ManagerDashboard) }, onLogout = performLogout)
    }
    Screen.MonthlyExpensesReport -> {
        MonthlyExpensesReportScreen(adminPhone = loggedInAdminPhone, onBack = { onScreenChange(Screen.ManagerDashboard) }, onLogout = performLogout)
    }
    Screen.MonthlyCreditReport -> {
        MonthlyCreditReportScreen(adminPhone = loggedInAdminPhone, onBack = { onScreenChange(Screen.ManagerDashboard) }, onLogout = performLogout)
    }
    Screen.MonthlyUdhariJamaReport -> {
        MonthlyUdhariJamaReportScreen(adminPhone = loggedInAdminPhone, onBack = { onScreenChange(Screen.ManagerDashboard) }, onLogout = performLogout)
    }
    Screen.CustomerUdhariLedgerReport -> {
        CustomerUdhariLedgerReportScreen(adminPhone = loggedInAdminPhone, onBack = { onScreenChange(Screen.ManagerDashboard) }, onLogout = performLogout)
    }
    Screen.DailyPumpData -> {
        DailyPumpDataScreen(adminPhone = loggedInAdminPhone, enteredBy = loggedInUsername, onBack = { onScreenChange(Screen.Welcome) })
    }
    Screen.StaffManagement -> {
        StaffManagementScreen(adminPhone = loggedInAdminPhone, pumpName = loggedInPumpName, accountId = loggedInAccountId, onBack = { onScreenChange(Screen.Welcome) })
    }
    else -> { /* Handle others if needed */ }
  }
}

@Composable
fun EditDateDialog(isOpen: Boolean, currentDate: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    if (!isOpen) return
    var textState by remember { mutableStateOf(currentDate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Set Session Date", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(text = "Please verify or modify the active shift and reconciliation date.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = textState, onValueChange = { textState = it }, label = { Text("Date (dd-mm-yyyy)") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("dialog_date_input"))
            }
        },
        confirmButton = { Button(onClick = { onConfirm(textState.trim()) }, modifier = Modifier.testTag("dialog_date_confirm_button")) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("dialog_date_cancel_button")) { Text("Cancel") } },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
