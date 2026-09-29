package com.example

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.DailyPumpData
import com.example.database.SupabaseRepository
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPumpDataScreen(
    adminPhone: String,
    enteredBy: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val calendar = Calendar.getInstance()

    var selectedDate by rememberSaveable {
        mutableStateOf(
            String.format(
                Locale.US,
                "%02d-%02d-%04d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR)
            )
        )
    }

    var products by remember { mutableStateOf<List<String>>(emptyList()) }
    // Map of product to its inputs (Density, Rate, Stock)
    val densityInputs = remember { mutableStateMapOf<String, String>() }
    val rateInputs = remember { mutableStateMapOf<String, String>() }
    val stockInputs = remember { mutableStateMapOf<String, String>() }
    
    var isLoading by remember { mutableStateOf(false) }

    val resetAndBack = {
        onBack()
    }

    BackHandler {
        resetAndBack()
    }

    // Load configured products and existing daily data
    LaunchedEffect(adminPhone, selectedDate) {
        isLoading = true
        coroutineScope.launch {
            val pumpInfo = SupabaseRepository.getPumpInfo(adminPhone)
            val configuredProducts = pumpInfo?.productNames?.split(",")?.filter { it.isNotBlank() }?.map { it.trim() } ?: emptyList()
            
            // Fallback: Check registered nozzles if productNames is empty
            val allProducts = if (configuredProducts.isEmpty()) {
                val nozzles = SupabaseRepository.getRegisteredNozzles(adminPhone)
                val nozzleTypes = nozzles.map { it.nozzleType }.distinct()
                if (nozzleTypes.isEmpty()) listOf("MS", "HSD") else nozzleTypes
            } else {
                configuredProducts
            }
            
            products = allProducts

            // Fetch existing data for selected date
            val existingData = SupabaseRepository.getDailyPumpData(adminPhone, selectedDate)
            
            // Clear maps first
            densityInputs.clear()
            rateInputs.clear()
            stockInputs.clear()

            // Populate inputs
            allProducts.forEach { productName ->
                val record = existingData.find { isSameProduct(it.productName, productName) }
                densityInputs[productName] = record?.density?.toString() ?: ""
                rateInputs[productName] = record?.rate?.toString() ?: ""
                stockInputs[productName] = record?.openingStock?.toString() ?: ""
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Pump Data", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = resetAndBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Date Selector
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            selectedDate = String.format(Locale.US, "%02d-%02d-%04d", dayOfMonth, month + 1, year)
                        },
                        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.CalendarMonth, null, tint = Color(0xFF2563EB), modifier = Modifier.size(28.dp))
                    Column {
                        Text("Calculation Date", fontSize = 12.sp, color = Color.Gray)
                        Text(selectedDate, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // 1. Morning Density Section
                DailyDataSection(
                    title = "Morning Density",
                    icon = Icons.Default.Opacity,
                    iconColor = Color(0xFF3B82F6),
                    products = products,
                    inputs = densityInputs,
                    labelSuffix = "Density",
                    placeholder = "0.742"
                )

                // 2. Fuel Rates Section
                DailyDataSection(
                    title = "Fuel Rates",
                    icon = Icons.Default.CurrencyRupee,
                    iconColor = Color(0xFF10B981),
                    products = products,
                    inputs = rateInputs,
                    labelSuffix = "Fuel Rate (₹/L)",
                    placeholder = "104.75"
                )

                // 3. Opening Stock Section
                DailyDataSection(
                    title = "Opening Stock",
                    icon = Icons.Default.Storage,
                    iconColor = Color(0xFF8B5CF6),
                    products = products,
                    inputs = stockInputs,
                    labelSuffix = "Opening Stock (L)",
                    placeholder = "12500"
                )

                Button(
                    onClick = {
                        // Validation
                        val invalidProducts = products.filter { product ->
                            densityInputs[product].isNullOrBlank() || 
                            rateInputs[product].isNullOrBlank() || 
                            stockInputs[product].isNullOrBlank()
                        }
                        
                        if (invalidProducts.isNotEmpty()) {
                            Toast.makeText(context, "Please fill all fields for ${invalidProducts.joinToString(", ")}", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        coroutineScope.launch {
                            val dataList = products.map { product ->
                                DailyPumpData(
                                    adminPhone = adminPhone,
                                    date = selectedDate,
                                    productName = product,
                                    productId = "PROD_${product.uppercase().replace(" ", "_")}",
                                    density = densityInputs[product]?.toDoubleOrNull() ?: 0.0,
                                    rate = rateInputs[product]?.toDoubleOrNull() ?: 0.0,
                                    openingStock = stockInputs[product]?.toDoubleOrNull() ?: 0.0,
                                    enteredBy = enteredBy
                                )
                            }
                            SupabaseRepository.saveDailyPumpData(adminPhone, selectedDate, dataList)
                            isLoading = false
                            Toast.makeText(context, "Daily Pump Data Saved!", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isLoading && products.isNotEmpty()
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(12.dp))
                        Text("SAVE DAILY DATA", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun DailyDataSection(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    products: List<String>,
    inputs: MutableMap<String, String>,
    labelSuffix: String,
    placeholder: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = iconColor.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp)) }
                }
                Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF1E293B))
            }

            products.forEach { product ->
                VoiceOutlinedTextField(
                    value = inputs[product] ?: "",
                    onValueChange = { inputs[product] = it },
                    label = { Text("$product $labelSuffix") },
                    placeholder = { Text(placeholder) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = iconColor,
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    )
                )
            }
        }
    }
}
