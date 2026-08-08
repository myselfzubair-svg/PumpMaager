package com.example

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.database.StaffMember
import com.example.database.FirestoreUserManager
import com.example.database.FirestoreRepository
import com.example.database.SmsOtpManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleSelectionScreen(
    loggedInMobileNumber: String,
    adminPhone: String,
    pumpName: String = "Pump",
    userRoleFromSession: String,
    onNavigateToCaModule: () -> Unit,
    onNavigateToManagersModule: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current

    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var deniedMessage by remember { mutableStateOf("") }
    var userRole by remember { mutableStateOf(userRoleFromSession) }
    
    fun checkAccess(requiredRole: String, onGranted: () -> Unit) {
        val hasAccess = when (requiredRole) {
            "ADMIN" -> userRole == "ADMIN"
            "MANAGER" -> userRole == "ADMIN" || userRole == "MANAGER"
            "CA" -> userRole == "ADMIN" || userRole == "MANAGER" || userRole == "CA"
            else -> false
        }
        if (hasAccess || loggedInMobileNumber.isEmpty()) onGranted()
        else {
            deniedMessage = "Access Denied. Your role is '$userRole', which does not have permission for this module."
            showPermissionDeniedDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Modules", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = Color(0xFF1E293B))) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF1E293B)) }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Search, "Search", tint = Color(0xFF1E293B)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val isStaff = userRole == "CA" || userRole == "MANAGER"
            val isAdmin = userRole == "ADMIN" || loggedInMobileNumber.isEmpty()
            val isManagerOrAdmin = userRole == "ADMIN" || userRole == "MANAGER" || loggedInMobileNumber.isEmpty()

            item {
                ModuleGridCard("CA Module", "Complete CA operations", Icons.Default.LocalGasStation, Color(0xFF2563EB)) {
                    checkAccess("CA", onNavigateToCaModule)
                }
            }
            if (isManagerOrAdmin) {
                item {
                    ModuleGridCard("Manager Module", "Manager tools & controls", Icons.Default.Person, Color(0xFFF59E0B)) {
                        checkAccess("MANAGER", onNavigateToManagersModule)
                    }
                }
            }
            if (isManagerOrAdmin) {
                item {
                    ModuleGridCard("Office", "Office management", Icons.Default.Business, Color(0xFF3B82F6)) {}
                }
            }
            if (isManagerOrAdmin) {
                item {
                    ModuleGridCard("Save View", "Save current view settings", Icons.Default.Save, Color(0xFF8B5CF6)) {}
                }
            }
            if (isManagerOrAdmin) {
                item {
                    ModuleGridCard("Saved Reports", "Access your saved reports", Icons.Default.Folder, Color(0xFFF59E0B)) {}
                }
            }
            // "Add Staff" removed as per request - now in Quick Access only
            if (isManagerOrAdmin) {
                item {
                    ModuleGridCard("Daily Audit", "Perform daily audit tasks", Icons.Default.Assessment, Color(0xFF6366F1)) {}
                }
            }
            if (isManagerOrAdmin) {
                item {
                    ModuleGridCard("Attendance", "Mark & manage attendance", Icons.Default.EventAvailable, Color(0xFF10B981)) {}
                }
            }
            if (isManagerOrAdmin) {
                item {
                    ModuleGridCard("Payroll", "Payroll management", Icons.Default.Payments, Color(0xFFEF4444)) {}
                }
            }
            if (isManagerOrAdmin) {
                item {
                    ModuleGridCard("Stock Mgmt.", "Manage fuel stock", Icons.Default.Inventory2, Color(0xFF3B82F6)) {}
                }
            }
            if (isManagerOrAdmin) {
                item {
                    ModuleGridCard("Fuel Rates", "Update fuel rates", Icons.Default.LocalGasStation, Color(0xFFEF4444)) {}
                }
            }
            if (isManagerOrAdmin) {
                item {
                    ModuleGridCard("Density Mgmt.", "Manage fuel densities", Icons.Default.PieChart, Color(0xFF3B82F6)) {}
                }
            }
        }
    }

    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text("Access Restricted", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text(deniedMessage) },
            confirmButton = { Button(onClick = { showPermissionDeniedDialog = false }) { Text("OK") } }
        )
    }
}

@Composable
fun ModuleGridCard(title: String, desc: String, icon: ImageVector, iconColor: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(140.dp).clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = iconColor.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp)) }
            }
            Column {
                Text(title, fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF1E293B))
                Text(desc, fontSize = 10.sp, color = Color(0xFF64748B), lineHeight = 14.sp)
            }
        }
    }
}
