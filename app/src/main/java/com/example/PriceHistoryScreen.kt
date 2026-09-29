package com.example

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.database.SupabaseRepository
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceHistoryScreen(
    adminPhone: String,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Selected Month & Year
    val calendar = Calendar.getInstance()
    var selectedMonth by rememberSaveable { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var selectedYear by rememberSaveable { mutableStateOf(calendar.get(Calendar.YEAR)) }

    var products by remember { mutableStateOf<List<String>>(emptyList()) }
    var priceData by remember { mutableStateOf<Map<String, Map<String, Double>>>(emptyMap()) } // Date -> Product -> Rate
    var isLoading by remember { mutableStateOf(false) }

    val daysInMonthList = remember(selectedMonth, selectedYear) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, selectedYear)
        cal.set(Calendar.MONTH, selectedMonth)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        (1..maxDays).map { day ->
            String.format(Locale.US, "%02d-%02d-%04d", day, selectedMonth + 1, selectedYear)
        }
    }

    LaunchedEffect(adminPhone, selectedMonth, selectedYear) {
        if (adminPhone.isNotBlank()) {
            isLoading = true
            val pumpInfo = SupabaseRepository.getPumpInfo(adminPhone)
            val configuredProducts = pumpInfo?.productNames?.split(",")?.filter { it.isNotBlank() }?.map { it.trim() } ?: emptyList()
            products = if (configuredProducts.isEmpty()) listOf("MS", "HSD") else configuredProducts

            val dataMap = mutableMapOf<String, Map<String, Double>>()
            daysInMonthList.forEach { dateStr ->
                val dailyData = SupabaseRepository.getDailyPumpData(adminPhone, dateStr)
                val prodMap = dailyData.associate { it.productName to it.rate }
                dataMap[dateStr] = prodMap
            }
            priceData = dataMap
            isLoading = false
        }
    }

    val monthNamesEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val selectedMonthName = monthNamesEn[selectedMonth]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Price History Report", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        printPriceHistoryPdf(context, selectedMonthName, selectedYear, products, priceData, daysInMonthList)
                    }) { Icon(Icons.Default.Share, "Share") }
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, "Logout") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        Column(modifier = modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {
            // Month Picker
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (selectedMonth == 0) { selectedMonth = 11; selectedYear-- } else selectedMonth-- }) {
                        Icon(Icons.Default.ChevronLeft, "Prev")
                    }
                    Text("$selectedMonthName $selectedYear", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    IconButton(onClick = { if (selectedMonth == 11) { selectedMonth = 0; selectedYear++ } else selectedMonth++ }) {
                        Icon(Icons.Default.ChevronRight, "Next")
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val scrollState = rememberScrollState()
                Box(modifier = Modifier.weight(1f).horizontalScroll(scrollState)) {
                    Column {
                        // Header Row
                        Row(modifier = Modifier.background(Color(0xFFE2E8F0)).padding(vertical = 8.dp)) {
                            Text("Date", modifier = Modifier.width(100.dp).padding(start = 8.dp), fontWeight = FontWeight.Bold)
                            products.forEach { prod ->
                                Text(prod, modifier = Modifier.width(120.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(daysInMonthList) { dateStr ->
                                Row(modifier = Modifier.padding(vertical = 8.dp).border(width = 0.5.dp, color = Color.LightGray.copy(alpha = 0.3f))) {
                                    Text(dateStr, modifier = Modifier.width(100.dp).padding(start = 8.dp))
                                    products.forEach { prod ->
                                        val rate = priceData[dateStr]?.entries?.find { isSameProduct(it.key, prod) }?.value
                                        val valStr = if (rate != null && rate > 0) String.format(Locale.US, "₹%.2f", rate) else "--"
                                        Text(valStr, modifier = Modifier.width(120.dp), textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun printPriceHistoryPdf(
    context: Context,
    month: String,
    year: Int,
    products: List<String>,
    data: Map<String, Map<String, Double>>,
    days: List<String>
) {
    val webView = WebView(context)
    val htmlBuilder = StringBuilder()

    htmlBuilder.append("""
        <html>
        <head>
            <style>
                body { font-family: Arial, sans-serif; font-size: 10px; }
                table { width: 100%; border-collapse: collapse; margin-top: 20px; }
                th, td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                th { background-color: #f2f2f2; }
                h1 { text-align: center; color: #1a237e; }
            </style>
        </head>
        <body>
            <h1>Price History Report - ${"$month $year"}</h1>
            <table>
                <thead>
                    <tr>
                        <th>Date</th>
                        ${products.joinToString("") { "<th>$it</th>" }}
                    </tr>
                </thead>
                <tbody>
                    ${days.joinToString("") { date ->
                        val rowData = products.joinToString("") { prod ->
                            val rate = data[date]?.entries?.find { isSameProduct(it.key, prod) }?.value
                            val valStr = if (rate != null && rate > 0) String.format(Locale.US, "₹%.2f", rate) else "--"
                            "<td>$valStr</td>"
                        }
                        "<tr><td>$date</td>$rowData</tr>"
                    }}
                </tbody>
            </table>
        </body>
        </html>
    """.trimIndent())

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "Price_History_${month}_$year"
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "UTF-8", null)
}
