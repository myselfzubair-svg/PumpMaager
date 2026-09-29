package com.example

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.SavedAudit
import com.example.database.DailyPumpData
import com.example.database.SupabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onNavigateToCalculator: () -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    pumpName: String = "D R Inamdar Petroleum",
    loggedInUsername: String = "",
    loggedInMobileNumber: String = "",
    adminPhone: String = "",
    userRoleFromSession: String = "",
    onLogout: () -> Unit = {},
    onDrawerOpen: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToDailySalesReport: () -> Unit = {},
    onNavigateToAllModules: () -> Unit = {},
    onNavigateToDailyPumpData: () -> Unit = {},
    onNavigateToAdminPanel: () -> Unit = {},
    onNavigateToStaffManagement: () -> Unit = {},
    onNavigateToCashToBank: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isSyncing by remember { mutableStateOf(false) }
    
    val todayDate = remember {
        java.text.SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
    }
    val todayDay = remember {
        java.text.SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
    }

    var dailyPumpDataList by remember { mutableStateOf<List<DailyPumpData>>(emptyList()) }
    var bankableCash by remember { mutableStateOf(0.0) }

    LaunchedEffect(adminPhone, todayDate) {
        coroutineScope.launch(Dispatchers.IO) {
            val data = SupabaseRepository.getDailyPumpData(adminPhone, todayDate)
            
            // Calculate bankable cash
            val audits = SupabaseRepository.getAuditsFlow(adminPhone).first()
            val managerTransactions = SupabaseRepository.getManagerTransactionsFlow(adminPhone).first()
            val latestDeposit = managerTransactions.firstOrNull { it.type == "BANK_DEPOSIT" }
            val cycleStart = latestDeposit?.timestamp ?: 0L
            
            val currentAudits = audits.filter { it.timestamp > cycleStart }
            val currentMGR = managerTransactions.filter { it.timestamp > cycleStart }
            
            val inflow = currentAudits.sumOf { it.totalFuelSalesAmount + it.totalUdhariJama } + 
                         currentMGR.filter { it.type == "MANAGER_JAMA" }.sumOf { it.amount }
            
            val outflow = currentAudits.sumOf { it.phonePeAmount + it.cardsAmount + it.totalKharch + it.totalUdhar } + 
                          currentMGR.filter { it.type != "MANAGER_JAMA" && it.type != "BANK_DEPOSIT" }.sumOf { it.amount }
            
            withContext(Dispatchers.Main) {
                dailyPumpDataList = data
                bankableCash = (inflow - outflow).coerceAtLeast(0.0)
            }
        }
    }

    val currentTime = remember {
        java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = Color(0xFF2563EB), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(42.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.WaterDrop, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                        }
                        Column {
                            Text("Pump Manager", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = Color(0xFF1E293B)))
                            Text("Smart Fuel Station Management", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontWeight = FontWeight.Medium))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDrawerOpen) { Icon(Icons.Default.Menu, "Menu", tint = Color(0xFF1E293B)) }
                },
                actions = {
                    // Top-right actions removed as per requirement
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // --- Welcome Banner ---
            Card(
                modifier = Modifier.fillMaxWidth().height(210.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF1E40AF), Color(0xFF1E3A8A)))
                )) {
                    Canvas(modifier = Modifier.fillMaxSize().alpha(0.1f)) {
                        drawCircle(Color.White, radius = 100.dp.toPx(), center = Offset(size.width * 0.8f, size.height * 0.2f))
                        drawCircle(Color.White, radius = 150.dp.toPx(), center = Offset(size.width * 0.9f, size.height * 0.9f))
                    }
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.CenterEnd) {
                        Icon(Icons.Default.LocalGasStation, null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(160.dp))
                    }
                    Column(modifier = Modifier.padding(28.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        Text("Welcome to", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium))
                        Text(
                            text = pumpName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = (-0.5).sp),
                            maxLines = 2
                        )
                        Spacer(Modifier.height(12.dp))
                        Surface(color = Color(0xFF10B981), shape = CircleShape) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(6.dp).background(Color.White, CircleShape))
                                val userDisplayRole = if (userRoleFromSession.uppercase() == "ADMIN") "Owner" else userRoleFromSession
                                Text("Active User: $loggedInUsername ($userDisplayRole)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Manage your fuel station\nefficiently today.", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                    }
                }
            }

            // --- Status Row ---
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                item { PremiumStatusCard("Active Session", pumpName, "Since 08:30 AM", Icons.Default.CheckCircle, Color(0xFF10B981), "Active", Color(0xFF10B981), Modifier.width(160.dp)) }
                item { PremiumStatusCard("Cloud Backup", "Backup is active", "Last: 09:15 AM", Icons.Default.CloudDone, Color(0xFF2563EB), "Success", Color(0xFF10B981), Modifier.width(160.dp)) }
                item { PremiumStatusCard("Current Date", todayDate, todayDay, Icons.Default.CalendarMonth, Color(0xFF2563EB), "Today", Color(0xFF2563EB), Modifier.width(160.dp)) }
                item { PremiumStatusCard("Current Shift", "Morning Shift", "08:00 - 04:00", Icons.Default.AccessTime, Color(0xFFF59E0B), "In Progress", Color(0xFFF59E0B), Modifier.width(160.dp)) }
            }

            // --- Control Bar ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(modifier = Modifier.weight(1f).clickable { 
                        onLanguageChange(if (language == AppLanguage.English) AppLanguage.Hindi else AppLanguage.English) 
                    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Language, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                        }
                        Column {
                            Text("Language", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (language == AppLanguage.English) "English" else "हिन्दी", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Button(
                        onClick = { 
                            isSyncing = true
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(1000)
                                isSyncing = false
                                Toast.makeText(context, "Data Synced", Toast.LENGTH_SHORT).show()
                            }
                        }, 
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)), 
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Default.Sync, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sync Now", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- Setup Overview ---
            val isEditable = userRoleFromSession == "ADMIN" || userRoleFromSession == "MANAGER" || loggedInMobileNumber.isEmpty()
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFF1F5F9))) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Today's Setup Overview", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)))
                        if (isEditable && userRoleFromSession.uppercase() != "CA") {
                            Text("View All", color = Color(0xFF2563EB), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable { onNavigateToAllModules() })
                        }
                    }
                    
                    if (dailyPumpDataList.isEmpty()) {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().clickable(enabled = isEditable) { onNavigateToDailyPumpData() }
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Info, null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isEditable) "No data entered for today. Click to setup." else "No setup data available for today.", 
                                    fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            item { SetupOverviewSection("Morning Density", Icons.Default.Science, dailyPumpDataList, "density", Modifier.clickable(enabled = isEditable) { onNavigateToDailyPumpData() }) }
                            item { SetupOverviewSection("Fuel Rates", Icons.Default.CurrencyRupee, dailyPumpDataList, "rate", Modifier.clickable(enabled = isEditable) { onNavigateToDailyPumpData() }, true) }
                            item { SetupOverviewSection("Opening Stock", Icons.Default.Storage, dailyPumpDataList, "stock", Modifier.clickable(enabled = isEditable) { onNavigateToDailyPumpData() }) }
                        }
                    }
                }
            }

            // --- Quick Access Grid ---
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Quick Access", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)))
                    if (userRoleFromSession.uppercase() != "CA") {
                        Text("View All", color = Color(0xFF2563EB), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable { onNavigateToAllModules() })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val role = userRoleFromSession.uppercase()
                    val isAdmin = role == "ADMIN" || loggedInMobileNumber.isEmpty()
                    val isManager = role == "MANAGER"
                    val isManagerOrAdmin = isAdmin || isManager

                    DashboardQuickAction("CA Module", Icons.Default.LocalGasStation, Color(0xFF2563EB), { onNavigateToCalculator() })
                    
                    if (isAdmin) {
                        DashboardQuickAction("Add Staff", Icons.Default.PersonAdd, Color(0xFF3B82F6), onNavigateToStaffManagement)
                    }
                    
                    if (isManagerOrAdmin) {
                        DashboardQuickAction("Daily Sales", Icons.Default.Assessment, Color(0xFF10B981), { onNavigateToDailySalesReport() })
                        DashboardQuickAction("Bankable: ₹${formatDouble(bankableCash)}", Icons.Default.AccountBalance, Color(0xFFF59E0B), { onNavigateToCashToBank() })
                        
                        // MANAGER cannot see Saved Reports
                        if (isAdmin) {
                            DashboardQuickAction("Saved Reports", Icons.Default.Folder, Color(0xFF8B5CF6), { onNavigateToHistory() })
                        }
                    }
                    
                    if (isManagerOrAdmin) {
                        DashboardQuickAction("Reports", Icons.Default.PieChart, Color(0xFF64748B), { onNavigateToReports() })
                    }
                }
            }

            // --- Footer ---
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 32.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WaterDrop, null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Powered by ", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("GenovaCore™ Innovations", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SetupOverviewSection(
    title: String, 
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    dataList: List<DailyPumpData>, 
    type: String, // "density", "rate", "stock"
    modifier: Modifier = Modifier, 
    isCurrency: Boolean = false
) {
    Column(modifier = modifier.widthIn(min = 140.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(color = Color(0xFF2563EB).copy(alpha = 0.1f), shape = CircleShape, modifier = Modifier.size(24.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp)) }
            }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            dataList.forEach { data ->
                val value = when(type) {
                    "density" -> data.density.toString()
                    "rate" -> data.rate.toString()
                    "stock" -> data.openingStock.toString()
                    else -> ""
                }
                val dotColor = if (data.productName.contains("Petrol", ignoreCase = true)) Color(0xFF6366F1) else Color(0xFF10B981)
                FuelRowItem(data.productName, value, dotColor, isCurrency)
            }
        }
    }
}

@Composable
fun FuelRowItem(label: String, value: String, dotColor: Color, isCurrency: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(6.dp).background(dotColor, CircleShape))
            Text(label, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = (if (isCurrency) "₹" else "") + value, 
            fontSize = 10.sp, 
            fontWeight = FontWeight.Black, 
            color = Color.Black,
            maxLines = 1
        )
    }
}
