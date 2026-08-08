package com.example

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerDashboardScreen(
    onBack: () -> Unit,
    onNavigateToDailyAudit: (String) -> Unit,
    onNavigateToDailySalesReport: () -> Unit,
    onNavigateToTtEntryReport: () -> Unit,
    onNavigateToMonthlyExpensesReport: () -> Unit,
    onNavigateToMonthlyCreditReport: () -> Unit,
    onNavigateToMonthlyUdhariJamaReport: () -> Unit,
    onNavigateToCustomerUdhariLedgerReport: () -> Unit,
    adminPhone: String = "",
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    var selectedDate by rememberSaveable {
        mutableStateOf(
            String.format(
                "%02d-%02d-%04d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR)
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = LanguageManager.translate("Reports Center", "रिपोर्ट्स केंद्र"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "D R INAMDAR PETROLEUM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // DATE SELECTOR
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                selectedDate = String.format("%02d-%02d-%04d", dayOfMonth, month + 1, year)
                            },
                            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.CalendarMonth, null, tint = Color(0xFF2563EB), modifier = Modifier.size(28.dp))
                            Column {
                                Text("Reporting Date", fontSize = 12.sp, color = Color.Gray)
                                Text(selectedDate, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2563EB))
                            }
                        }
                        Text("Change", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB), fontSize = 12.sp)
                    }
                }
            }

            // REPORTS SECTION
            item {
                Text("Available Reports", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), modifier = Modifier.padding(top = 8.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                ) {
                    Column {
                        ReportRowItem("Daily Sales Audit Report", "Reconcile shift sales and print audit PDF", Icons.Default.Assessment, Color(0xFF2563EB)) { onNavigateToDailyAudit(selectedDate) }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        ReportRowItem("Monthly Sales Report", "View full month sales, stocks, and rates summary", Icons.Default.LocalGasStation, Color(0xFF3B82F6), onNavigateToDailySalesReport)
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        ReportRowItem("Monthly Expenses Report", "Summary and details of daily station expenses", Icons.Default.TrendingDown, Color(0xFFEF4444), onNavigateToMonthlyExpensesReport)
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        ReportRowItem("Monthly Credit Report", "View outstanding credits and party details", Icons.Default.TrendingUp, Color(0xFF2563EB), onNavigateToMonthlyCreditReport)
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        ReportRowItem("Monthly Udhari Jama Report", "Track recovered outstanding credits", Icons.Default.TrendingUp, Color(0xFF10B981), onNavigateToMonthlyUdhariJamaReport)
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        ReportRowItem("Customer Credit Ledger", "Detailed party-wise credit/recovery tracking", Icons.Default.AccountBalanceWallet, Color(0xFFF59E0B), onNavigateToCustomerUdhariLedgerReport)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRowItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(color = iconColor.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp)) }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF64748B), lineHeight = 16.sp)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
    }
}
