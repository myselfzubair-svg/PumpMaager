package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.MyApplicationTheme

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
  Login,
  Welcome,
  ModuleSelection,
  NozzleSelection,
  TwoNozzleDetails,
  TwoNozzleCalculator,
  FourNozzleDetails,
  Calculator,
  FullDayCalculator,
  History,
  ManagerDashboard,
  AdminPanel,
  DailySalesReport,
  TtReceiptEntry,
  TtEntryReport,
  MonthlyExpensesReport,
  MonthlyCreditReport,
  MonthlyUdhariJamaReport,
  CustomerUdhariLedgerReport
}

@Composable
fun MainNavigationFlow(
  isDarkTheme: Boolean,
  onThemeChange: (Boolean) -> Unit
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val sharedPrefs = remember { context.getSharedPreferences("pump_manager_prefs", android.content.Context.MODE_PRIVATE) }
  val savedPumpName = remember { sharedPrefs.getString("session_pump_name", "") ?: "" }
  val savedUsername = remember { sharedPrefs.getString("session_username", "") ?: "" }
  val savedMobile = remember { sharedPrefs.getString("session_mobile", "") ?: "" }

  val currentDate = remember {
    java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())
  }
  var currentScreen by remember { mutableStateOf(if (savedPumpName.isNotEmpty()) Screen.Welcome else Screen.Login) }
  var selectedDate by remember { mutableStateOf(currentDate) }
  var selectedCaName by remember { mutableStateOf("") }
  var selectedMeterNo by remember { mutableStateOf("") }
  var selectedNozzleCount by remember { mutableStateOf(4) }
  var selectedMsNozzleCount by remember { mutableStateOf(2) }
  var selectedHsdNozzleCount by remember { mutableStateOf(2) }
  var loggedInUsername by remember { mutableStateOf(savedUsername) }
  var loggedInMobileNumber by remember { mutableStateOf(savedMobile) }
  var loggedInPumpName by remember { mutableStateOf(if (savedPumpName.isNotEmpty()) savedPumpName else "D R Inamdar Petroleum") }
  var loggedInMsLabels by remember { mutableStateOf(emptyList<String>()) }
  var loggedInHsdLabels by remember { mutableStateOf(emptyList<String>()) }
  var activeMsLabels by remember { mutableStateOf(emptyList<String>()) }
  var activeHsdLabels by remember { mutableStateOf(emptyList<String>()) }
  var isManagersModuleFlow by remember { mutableStateOf(false) }

  // Automatically load labels if we have a saved session
  LaunchedEffect(savedMobile) {
    if (savedMobile.isNotEmpty()) {
      kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val db = com.example.database.AppDatabase.getDatabase(context)
        val registeredList = db.registeredNozzleDao().getNozzlesByPumpMobile(savedMobile)
        val finalMsLabels = registeredList.filter { it.nozzleType == "MS" }.sortedBy { it.nozzleIndex }.map { it.label }
        val finalHsdLabels = registeredList.filter { it.nozzleType == "HSD" }.sortedBy { it.nozzleIndex }.map { it.label }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
          loggedInMsLabels = finalMsLabels
          loggedInHsdLabels = finalHsdLabels
          activeMsLabels = finalMsLabels
          activeHsdLabels = finalHsdLabels
          if (finalMsLabels.isNotEmpty()) {
            selectedMsNozzleCount = finalMsLabels.size
          }
          if (finalHsdLabels.isNotEmpty()) {
            selectedHsdNozzleCount = finalHsdLabels.size
          }
        }
      }
    }
  }
  
  var appLanguage by remember { mutableStateOf(AppLanguage.English) }
  LanguageManager.currentLanguage = appLanguage

  val performLogout: () -> Unit = {
    sharedPrefs.edit().apply {
      remove("session_username")
      remove("session_pump_name")
      remove("session_mobile")
      apply()
    }
    currentScreen = Screen.Login
  }

  var showDateEditDialog by remember { mutableStateOf(false) }
  var pendingFullDayConfirm by remember { mutableStateOf(false) }

  if (showDateEditDialog) {
    EditDateDialog(
      isOpen = true,
      currentDate = selectedDate,
      onDismiss = {
        showDateEditDialog = false
        pendingFullDayConfirm = false
      },
      onConfirm = { newDate ->
        selectedDate = newDate
        showDateEditDialog = false
        if (pendingFullDayConfirm) {
          pendingFullDayConfirm = false
          currentScreen = Screen.FullDayCalculator
        }
      }
    )
  }

  when (currentScreen) {
    Screen.Login -> {
      LoginScreen(
        onLoginSuccess = { username, pumpName, msLabels, hsdLabels, mobileNumber ->
          loggedInUsername = username
          loggedInMobileNumber = mobileNumber
          loggedInPumpName = pumpName
          loggedInMsLabels = msLabels
          loggedInHsdLabels = hsdLabels
          activeMsLabels = msLabels
          activeHsdLabels = hsdLabels
          if (msLabels.isNotEmpty()) {
            selectedMsNozzleCount = msLabels.size
          }
          if (hsdLabels.isNotEmpty()) {
            selectedHsdNozzleCount = hsdLabels.size
          }
          
          // Save session
          sharedPrefs.edit().apply {
            putString("session_username", username)
            putString("session_pump_name", pumpName)
            putString("session_mobile", mobileNumber)
            apply()
          }
          
          currentScreen = Screen.Welcome
        },
        onSkipLogin = {
          loggedInMobileNumber = "" // Guest Mode
          currentScreen = Screen.Welcome
        },
        isDarkTheme = isDarkTheme,
        onThemeChange = onThemeChange
      )
    }
    Screen.Welcome -> {
      androidx.activity.compose.BackHandler {
        performLogout()
      }
      WelcomeScreen(
        onNavigateToCalculator = { currentScreen = Screen.ModuleSelection },
        language = appLanguage,
        onLanguageChange = { appLanguage = it },
        isDarkTheme = isDarkTheme,
        onThemeChange = onThemeChange,
        pumpName = loggedInPumpName,
        loggedInMobileNumber = loggedInMobileNumber,
        onLogout = performLogout
      )
    }
    Screen.ModuleSelection -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.Welcome
      }
      ModuleSelectionScreen(
        loggedInMobileNumber = loggedInMobileNumber,
        onNavigateToCaModule = {
          isManagersModuleFlow = false
          currentScreen = Screen.NozzleSelection
        },
        onNavigateToManagersModule = {
          isManagersModuleFlow = true
          currentScreen = Screen.ManagerDashboard
        },
        onNavigateToAdminPanel = { currentScreen = Screen.AdminPanel },
        onNavigateToHistory = { currentScreen = Screen.History },
        onBack = { currentScreen = Screen.Welcome },
        onLogout = performLogout
      )
    }
    Screen.NozzleSelection -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.ModuleSelection
      }
      NozzleSelectionScreen(
        initialMsCount = selectedMsNozzleCount,
        initialHsdCount = selectedHsdNozzleCount,
        username = loggedInUsername,
        isCaModule = !isManagersModuleFlow,
        msNozzleLabels = loggedInMsLabels,
        hsdNozzleLabels = loggedInHsdLabels,
        onNavigateToCalculator = { msCount, hsdCount, msLabels, hsdLabels ->
          selectedMsNozzleCount = msCount
          selectedHsdNozzleCount = hsdCount
          activeMsLabels = msLabels
          activeHsdLabels = hsdLabels
          if (isManagersModuleFlow) {
            currentScreen = Screen.FullDayCalculator
          } else {
            currentScreen = Screen.FourNozzleDetails
          }
        },
        onNavigateToFullDay = { 
          selectedMsNozzleCount = 2
          selectedHsdNozzleCount = 2
          activeMsLabels = emptyList()
          activeHsdLabels = emptyList()
          currentScreen = Screen.FullDayCalculator
        },
        onBack = { currentScreen = Screen.ModuleSelection },
        onLogout = performLogout
      )
    }
    Screen.TwoNozzleDetails -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.NozzleSelection
      }
      AuditDetailsScreen(
        isFourNozzle = false,
        selectedDate = selectedDate,
        onBack = { currentScreen = Screen.NozzleSelection },
        onProceed = { date, caName, meterNo ->
          selectedDate = date
          selectedCaName = caName
          selectedMeterNo = meterNo
          currentScreen = Screen.TwoNozzleCalculator
        },
        onLogout = performLogout
      )
    }
    Screen.TwoNozzleCalculator -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.TwoNozzleDetails
      }
      TwoNozzleCalculatorScreen(
        onBack = { currentScreen = Screen.TwoNozzleDetails },
        onSaveSuccess = { currentScreen = Screen.ModuleSelection },
        date = selectedDate,
        caName = selectedCaName,
        meterNo = selectedMeterNo,
        nozzleCount = selectedNozzleCount,
        phone = loggedInUsername,
        onLogout = performLogout
      )
    }
    Screen.FourNozzleDetails -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.NozzleSelection
      }
      AuditDetailsScreen(
        isFourNozzle = true,
        selectedDate = selectedDate,
        onBack = { currentScreen = Screen.NozzleSelection },
        onProceed = { date, caName, meterNo ->
          selectedDate = date
          selectedCaName = caName
          selectedMeterNo = meterNo
          currentScreen = Screen.Calculator
        },
        onLogout = performLogout
      )
    }
    Screen.Calculator -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.FourNozzleDetails
      }
      CalculatorScreen(
        onBack = { currentScreen = Screen.FourNozzleDetails },
        onSaveSuccess = { currentScreen = Screen.ModuleSelection },
        date = selectedDate,
        caName = selectedCaName,
        meterNo = selectedMeterNo,
        msNozzleCount = selectedMsNozzleCount,
        hsdNozzleCount = selectedHsdNozzleCount,
        msNozzleLabels = activeMsLabels,
        hsdNozzleLabels = activeHsdLabels,
        phone = loggedInUsername,
        onLogout = performLogout
      )
    }
    Screen.FullDayCalculator -> {
      androidx.activity.compose.BackHandler {
        if (isManagersModuleFlow) {
          currentScreen = Screen.ManagerDashboard
        } else {
          currentScreen = Screen.ModuleSelection
        }
      }
      FullDayCalculatorScreen(
        onBack = {
          if (isManagersModuleFlow) {
            currentScreen = Screen.ManagerDashboard
          } else {
            currentScreen = Screen.ModuleSelection
          }
        },
        date = selectedDate,
        onDateClick = { showDateEditDialog = true },
        msNozzleCount = selectedMsNozzleCount,
        hsdNozzleCount = selectedHsdNozzleCount,
        msNozzleLabels = activeMsLabels,
        hsdNozzleLabels = activeHsdLabels,
        phone = loggedInUsername,
        onLogout = performLogout
      )
    }
    Screen.ManagerDashboard -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.ModuleSelection
      }
      ManagerDashboardScreen(
        onBack = { currentScreen = Screen.ModuleSelection },
        onNavigateToDailyAudit = { dateStr ->
          selectedDate = dateStr
          currentScreen = Screen.FullDayCalculator
        },
        onNavigateToDailySalesReport = {
          currentScreen = Screen.DailySalesReport
        },
        onNavigateToTtReceiptEntry = {
          currentScreen = Screen.TtReceiptEntry
        },
        onNavigateToTtEntryReport = {
          currentScreen = Screen.TtEntryReport
        },
        onNavigateToMonthlyExpensesReport = {
          currentScreen = Screen.MonthlyExpensesReport
        },
        onNavigateToMonthlyCreditReport = {
          currentScreen = Screen.MonthlyCreditReport
        },
        onNavigateToMonthlyUdhariJamaReport = {
          currentScreen = Screen.MonthlyUdhariJamaReport
        },
        onNavigateToCustomerUdhariLedgerReport = {
          currentScreen = Screen.CustomerUdhariLedgerReport
        },
        onLogout = performLogout
      )
    }
    Screen.CustomerUdhariLedgerReport -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.ManagerDashboard
      }
      CustomerUdhariLedgerReportScreen(
        onBack = { currentScreen = Screen.ManagerDashboard },
        onLogout = performLogout
      )
    }
    Screen.MonthlyCreditReport -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.ManagerDashboard
      }
      MonthlyCreditReportScreen(
        onBack = { currentScreen = Screen.ManagerDashboard },
        onLogout = performLogout
      )
    }
    Screen.MonthlyUdhariJamaReport -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.ManagerDashboard
      }
      MonthlyUdhariJamaReportScreen(
        onBack = { currentScreen = Screen.ManagerDashboard },
        onLogout = performLogout
      )
    }
    Screen.MonthlyExpensesReport -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.ManagerDashboard
      }
      MonthlyExpensesReportScreen(
        onBack = { currentScreen = Screen.ManagerDashboard },
        onLogout = performLogout
      )
    }
    Screen.TtReceiptEntry -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.ManagerDashboard
      }
      TtReceiptEntryScreen(
        onBack = { currentScreen = Screen.ManagerDashboard },
        onLogout = performLogout
      )
    }
    Screen.TtEntryReport -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.ManagerDashboard
      }
      TtEntryReportScreen(
        onBack = { currentScreen = Screen.ManagerDashboard },
        onLogout = performLogout
      )
    }
    Screen.DailySalesReport -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.ManagerDashboard
      }
      DailySalesReportScreen(
        onBack = { currentScreen = Screen.ManagerDashboard },
        onLogout = performLogout
      )
    }
    Screen.History -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.ModuleSelection
      }
      HistoryScreen(
        onBack = { currentScreen = Screen.ModuleSelection },
        onLogout = performLogout
      )
    }
    Screen.AdminPanel -> {
      androidx.activity.compose.BackHandler {
        currentScreen = Screen.ModuleSelection
      }
      AdminPanelScreen(
        onBack = { currentScreen = Screen.ModuleSelection },
        onLogout = performLogout
      )
    }
  }
}

@Composable
fun EditDateDialog(
    isOpen: Boolean,
    currentDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (!isOpen) return

    var textState by remember { mutableStateOf(currentDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Session Date",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Please verify or modify the active shift and reconciliation date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text("Date (dd-mm-yyyy)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("dialog_date_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(textState.trim())
                },
                modifier = Modifier.testTag("dialog_date_confirm_button")
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_date_cancel_button")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme {
    CalculatorScreen()
  }
}
