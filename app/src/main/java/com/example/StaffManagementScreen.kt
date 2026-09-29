package com.example

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.StaffMember
import com.example.database.SupabaseUserManager
import com.example.database.SupabaseRepository
import com.example.database.SupabaseMembership
import com.example.database.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffManagementScreen(
    adminPhone: String,
    pumpName: String = "Pump",
    accountId: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var staffList by remember { mutableStateOf<List<StaffMember>>(emptyList()) }

    LaunchedEffect(adminPhone) {
        SupabaseRepository.getStaffMembersFlow(adminPhone).collect { list ->
            staffList = list
        }
    }

    var activeTab by remember { mutableStateOf(0) }
    var staffName by remember { mutableStateOf("") }
    var staffPhone by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("CA") }
    var isSubmitting by remember { mutableStateOf(false) }

    val resetAddStaffForm = {
        staffName = ""; staffPhone = ""; selectedRole = "CA"
        isSubmitting = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Staff Management", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = Color(0xFF1E293B))) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF1E293B)) }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TabRow(selectedTabIndex = activeTab, containerColor = Color.Transparent) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0; resetAddStaffForm() }, text = { Text("Staff List") })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Add Staff") })
            }

            if (activeTab == 0) {
                if (staffList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No staff members added yet.", color = Color.Gray)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        staffList.forEach { staff ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(staff.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(staff.phone, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        val displayPwd = staff.passwordHash.ifBlank { staff.phone.filter { it.isDigit() }.takeLast(10) }
                                        Text("Login Password: $displayPwd", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Surface(
                                            color = if (staff.role == "MANAGER") Color(0xFFF59E0B).copy(alpha = 0.1f) else Color(0xFF2563EB).copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Text(
                                                staff.role,
                                                color = if (staff.role == "MANAGER") Color(0xFFF59E0B) else Color(0xFF2563EB),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    IconButton(onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            SupabaseRepository.deleteStaffMember(staff.phone, adminPhone)
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Register New Staff", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        VoiceOutlinedTextField(
                            value = staffName,
                            onValueChange = { staffName = it },
                            label = { Text("Staff Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        VoiceOutlinedTextField(
                            value = staffPhone,
                            onValueChange = { staffPhone = it },
                            label = { Text("Mobile Number (Login ID)") },
                            placeholder = { Text("Enter 10-digit number") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Assign Role", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("CA", "MANAGER").forEach { role ->
                                    FilterChip(
                                        selected = selectedRole == role,
                                        onClick = { selectedRole = role },
                                        label = { Text(role) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (staffName.trim().length < 3) {
                                    Toast.makeText(context, "Name too short", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val rawPhone = staffPhone.trim().replace("+91", "").replace(" ", "")
                                if (rawPhone.length < 10) {
                                    Toast.makeText(context, "Invalid 10-digit mobile number", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val formattedPhone = "+91$rawPhone"
                                val password = rawPhone // Password is 10-digit mobile number

                                isSubmitting = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val newStaff = StaffMember(formattedPhone, adminPhone, staffName.trim(), password, selectedRole, accountId)
                                        SupabaseRepository.addStaffMember(newStaff)
                                        
                                        // Also add membership
                                        val membership = SupabaseMembership(
                                            staffPhone = formattedPhone,
                                            adminPhone = adminPhone,
                                            pumpName = pumpName,
                                            username = staffName.trim(),
                                            role = selectedRole,
                                            accountId = accountId
                                        )
                                        SupabaseClient.client.postgrest["memberships"].upsert(membership)
                                        
                                        withContext(Dispatchers.Main) {
                                            isSubmitting = false
                                            resetAddStaffForm()
                                            activeTab = 0
                                            Toast.makeText(context, "Staff added successfully. Password is $password", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isSubmitting = false
                                            Toast.makeText(context, "Error adding staff: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                            else Text("ADD STAFF MEMBER", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
