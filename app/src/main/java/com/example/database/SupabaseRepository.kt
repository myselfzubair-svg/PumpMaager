package com.example.database

import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/**
 * Repository for Supabase operations, mirroring FirestoreRepository.
 */
object SupabaseRepository {
    private const val TAG = "SupabaseRepository"
    private val client = SupabaseClient.client

    // --- Diagnostics ---
    suspend fun testSupabaseConnection(): Result<Unit> {
        return try {
            client.postgrest["connection_test"].upsert(mapOf("last_ping" to System.currentTimeMillis()))
            Log.i(TAG, "Supabase connection test: SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Supabase connection test: FAILED", e)
            Result.failure(e)
        }
    }

    // --- Pump Info ---
    suspend fun getPumpInfo(mobileNumber: String): PumpInfo? = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(mobileNumber.trim())
            val clean = mobileNumber.filter { it.isDigit() }.takeLast(10)
            
            val response = client.postgrest["pumps"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("mobile_number", formatted)
                        eq("mobile_number", clean)
                        eq("mobile_number", mobileNumber.trim())
                    }
                }
            }
            response.decodeSingleOrNull<PumpInfo>()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching pump info", e)
            null
        }
    }

    suspend fun savePumpInfo(pump: PumpInfo) = withContext(Dispatchers.IO) {
        try {
            client.postgrest["pumps"].upsert(pump)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pump info", e)
        }
    }

    // --- Staff Management ---
    fun getStaffMembersFlow(adminPhone: String): Flow<List<StaffMember>> = flow {
        val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
        val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
        
        // Simple polling for now as a fallback for realtime if not fully configured
        while (true) {
            try {
                val response = client.postgrest["staff_members"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                    }
                }
                emit(response.decodeList<StaffMember>())
            } catch (e: Exception) {
                Log.e(TAG, "Error in staff members flow", e)
            }
            kotlinx.coroutines.delay(5000) // Poll every 5 seconds
        }
    }.flowOn(Dispatchers.IO)

    suspend fun addStaffMember(staff: StaffMember) = withContext(Dispatchers.IO) {
        try {
            client.postgrest["staff_members"].upsert(staff)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding staff member", e)
        }
    }

    suspend fun deleteStaffMember(phone: String, adminPhone: String) = withContext(Dispatchers.IO) {
        try {
            // Delete from staff_members
            client.postgrest["staff_members"].delete {
                filter {
                    eq("phone", phone)
                    eq("owner_admin_phone", adminPhone)
                }
            }
            // Delete corresponding membership
            client.postgrest["memberships"].delete {
                filter {
                    eq("staff_phone", phone)
                    eq("admin_phone", adminPhone)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting staff member and membership", e)
        }
    }

    suspend fun getStaffMemberByPhoneOnly(phone: String): StaffMember? = withContext(Dispatchers.IO) {
        try {
            val response = client.postgrest["staff_members"].select(columns = Columns.ALL) {
                filter {
                    eq("phone", phone)
                }
            }
            response.decodeSingleOrNull<StaffMember>()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching staff by phone", e)
            null
        }
    }

    // --- Daily Cash Summary (Refactored for absolute accuracy and legacy support) ---
    private fun parseAmountFromText(text: String, pattern: String): Double {
        return try {
            Regex(pattern, RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) { 0.0 }
    }

    suspend fun updateDailyCashFromAudits(adminPhone: String, date: String) = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            // 1. Fetch all audits for this day
            val auditsResponse = client.postgrest["saved_audits"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", adminPhone.trim())
                    }
                    eq("date", date)
                    neq("audit_type", "Manager's Report")
                }
            }
            
            val audits = auditsResponse.decodeList<SavedAudit>()
            // 2. Sum the absolute total cash generated (Submitted + Balance)
            // Use dedicated fields, but fallback to text parsing for legacy records
            val absoluteTotalCash = audits.sumOf { audit ->
                var submitted = audit.cashSubmitted
                var collected = audit.actualCashCollected
                
                // Legacy fallback: Parse from summaryText if fields are empty
                if (submitted <= 0.0) {
                    submitted = parseAmountFromText(audit.summaryText, """Cash Submitted:\s*-?₹?\s*([\d,.]+)""")
                }
                if (collected <= 0.0) {
                    collected = parseAmountFromText(audit.summaryText, """CASH BALANCE:\s*-?₹?\s*([\d,.]+)""")
                    if (collected <= 0.0) {
                        collected = parseAmountFromText(audit.summaryText, """ACTUAL CASH COLLECTED:\s*-?₹?\s*([\d,.]+)""")
                    }
                }
                
                submitted + collected
            }
            
            // 3. Find if a summary row already exists
            val existingResponse = client.postgrest["daily_cash_summary"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("admin_phone", formatted)
                        eq("admin_phone", clean)
                        eq("admin_phone", adminPhone.trim())
                    }
                    eq("date", date)
                }
            }
            
            val existing = existingResponse.decodeSingleOrNull<DailyCashRecord>()
            
            val record = DailyCashRecord(
                id = existing?.id,
                adminPhone = formatted,
                date = date,
                totalCash = absoluteTotalCash,
                timestamp = System.currentTimeMillis()
            )
            
            client.postgrest["daily_cash_summary"].upsert(record, onConflict = "admin_phone,date")
            Log.d(TAG, "Re-calculated absolute daily cash for $date. Total Audits: ${audits.size}, Total Cash: $absoluteTotalCash")
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating absolute daily cash", e)
        }
    }

    // Legacy support (redirects to re-calculation)
    suspend fun upsertDailyCash(adminPhone: String, date: String, amountToAdd: Double) {
        updateDailyCashFromAudits(adminPhone, date)
    }

    // Direct Summation logic for Manager's Report
    suspend fun getDailyCashHandoverFromAudits(adminPhone: String, sinceTimestamp: Long, upToTimestamp: Long): List<DailyCashRecord> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            // 1. Fetch all audits in range
            val response = client.postgrest["saved_audits"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", adminPhone.trim())
                    }
                    gt("timestamp", sinceTimestamp)
                    lt("timestamp", upToTimestamp)
                    neq("audit_type", "Manager's Report")
                }
            }
            
            val audits = response.decodeList<SavedAudit>()
            
            // 2. Group by date and calculate absolute sum for each
            return@withContext audits.groupBy { it.date }
                .map { (date, dayAudits) ->
                    val totalForDay = dayAudits.sumOf { audit ->
                        var sub = audit.cashSubmitted
                        var col = audit.actualCashCollected
                        if (sub <= 0.0) sub = parseAmountFromText(audit.summaryText, """Cash Submitted:\s*-?₹?\s*([\d,.]+)""")
                        if (col <= 0.0) {
                            col = parseAmountFromText(audit.summaryText, """CASH BALANCE:\s*-?₹?\s*([\d,.]+)""")
                            if (col <= 0.0) col = parseAmountFromText(audit.summaryText, """ACTUAL CASH COLLECTED:\s*-?₹?\s*([\d,.]+)""")
                        }
                        sub + col
                    }
                    
                    DailyCashRecord(
                        adminPhone = formatted,
                        date = date,
                        totalCash = totalForDay,
                        timestamp = dayAudits.maxOf { it.timestamp } // Use latest audit as anchor
                    )
                }
                .sortedBy { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating handover sums from audits", e)
            emptyList()
        }
    }

    suspend fun getDailyCashRecords(adminPhone: String, sinceTimestamp: Long, upToTimestamp: Long): List<DailyCashRecord> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val response = client.postgrest["daily_cash_summary"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("admin_phone", formatted)
                        eq("admin_phone", clean)
                        eq("admin_phone", adminPhone.trim())
                    }
                    gt("timestamp", sinceTimestamp)
                    lt("timestamp", upToTimestamp)
                }
                order("timestamp", Order.ASCENDING)
            }
            response.decodeList<DailyCashRecord>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteDailyCashRecord(id: Int) = withContext(Dispatchers.IO) {
        try {
            client.postgrest["daily_cash_summary"].delete {
                filter { eq("id", id) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting daily cash record", e)
        }
    }

    // --- Audits ---
    fun getAuditsFlow(adminPhone: String): Flow<List<SavedAudit>> = flow {
        val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
        val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
        
        while (true) {
            try {
                val response = client.postgrest["saved_audits"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                    }
                    order("timestamp", Order.DESCENDING)
                }
                emit(response.decodeList<SavedAudit>())
            } catch (e: Exception) {
                Log.e(TAG, "Error in audits flow", e)
            }
            kotlinx.coroutines.delay(5000)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun saveAudit(audit: SavedAudit) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Saving main audit record for reportId: ${audit.reportId}")
        client.postgrest["saved_audits"].insert(audit)
    }

    // --- Daily Config (Rates, Densities) ---
    suspend fun getDailyConfig(adminPhone: String, date: String): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val response = client.postgrest["daily_configs"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("admin_phone", formatted)
                        eq("admin_phone", clean)
                        eq("admin_phone", adminPhone.trim())
                    }
                    eq("date", date)
                }
            }
            val row = response.decodeSingleOrNull<Map<String, @kotlinx.serialization.Contextual Any>>()
            val config = row?.get("config") as? Map<*, *>
            config?.map { it.key.toString() to it.value.toString() }?.toMap() ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun saveDailyConfig(adminPhone: String, date: String, config: Map<String, String>) = withContext(Dispatchers.IO) {
        try {
            val data = mapOf(
                "admin_phone" to adminPhone,
                "date" to date,
                "config" to config
            )
            client.postgrest["daily_configs"].upsert(data, onConflict = "admin_phone,date")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving daily config", e)
        }
    }

    // --- Daily Pump Data ---
    suspend fun getDailyPumpData(adminPhone: String, date: String): List<DailyPumpData> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val response = client.postgrest["daily_sales"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("admin_phone", formatted)
                        eq("admin_phone", clean)
                        eq("admin_phone", adminPhone.trim())
                    }
                    eq("date", date)
                }
            }
            response.decodeList<DailyPumpData>()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching daily pump data", e)
            emptyList()
        }
    }

    suspend fun saveDailyPumpData(adminPhone: String, date: String, dataList: List<DailyPumpData>) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Saving daily pump data for $adminPhone on $date. Count: ${dataList.size}")
            // Use upsert to avoid duplicate errors if the user saves multiple times for the same date/product
            val response = client.postgrest["daily_sales"].upsert(dataList, onConflict = "admin_phone,date,product_name")
            Log.d(TAG, "Save daily pump data response: ${response.data}")
            
            // Legacy sync
            val legacyConfig = mutableMapOf<String, String>()
            dataList.forEach { data ->
                if (data.productName.contains("Petrol", ignoreCase = true) && !data.productName.contains("Premium", ignoreCase = true)) {
                    legacyConfig["density_ms"] = data.density.toString()
                    legacyConfig["rate_ms"] = data.rate.toString()
                    legacyConfig["stock_ms"] = data.openingStock.toString()
                } else if (data.productName.contains("Diesel", ignoreCase = true)) {
                    legacyConfig["density_hsd"] = data.density.toString()
                    legacyConfig["rate_hsd"] = data.rate.toString()
                    legacyConfig["stock_hsd"] = data.openingStock.toString()
                }
            }
            if (legacyConfig.isNotEmpty()) {
                saveDailyConfig(adminPhone, date, legacyConfig)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving daily pump data", e)
        }
    }

    suspend fun getAllDailyConfigs(adminPhone: String): Map<String, Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val response = client.postgrest["daily_configs"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("admin_phone", formatted)
                        eq("admin_phone", clean)
                        eq("admin_phone", adminPhone.trim())
                    }
                }
            }
            val rows = response.decodeList<Map<String, @kotlinx.serialization.Contextual Any>>()
            rows.associate { row ->
                val date = row["date"].toString()
                val config = row["config"] as? Map<*, *>
                date to (config?.map { it.key.toString() to it.value.toString() }?.toMap() ?: emptyMap())
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // --- Registered Nozzles ---
    suspend fun getRegisteredNozzles(adminPhone: String): List<RegisteredNozzle> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            Log.d(TAG, "Fetching nozzles for Admin Phone. Formatted: [$formatted], Clean: [$clean]")
            
            // Try matching either formatted (+91) or clean (10-digit) mobile number
            val response = client.postgrest["registered_nozzles"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("mobile_number", formatted)
                        eq("mobile_number", clean)
                        eq("mobile_number", adminPhone.trim())
                    }
                }
            }
            
            Log.d(TAG, "Raw Nozzle Response Body: ${response.data}")
            
            if (response.data.trim() == "[]") {
                Log.w(TAG, "No nozzles found in database for this phone number.")
                return@withContext emptyList()
            }

            val list = response.decodeList<RegisteredNozzle>()
            Log.d(TAG, "Successfully decoded ${list.size} nozzles")
            list
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR fetching/parsing registered nozzles: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun saveNozzles(adminPhone: String, nozzles: List<RegisteredNozzle>) = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            client.postgrest["registered_nozzles"].delete {
                filter {
                    or {
                        eq("mobile_number", formatted)
                        eq("mobile_number", clean)
                        eq("mobile_number", adminPhone.trim())
                    }
                }
            }
            if (nozzles.isNotEmpty()) {
                client.postgrest["registered_nozzles"].insert(nozzles)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving nozzles", e)
            try {
                client.postgrest["registered_nozzles"].upsert(nozzles)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Error in upsert fallback saving nozzles", fallbackEx)
            }
        }
    }

    // --- TT Receipt Entries ---
    suspend fun getTtEntries(adminPhone: String): List<TtReceiptEntry> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val response = client.postgrest["tt_receipts"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", adminPhone.trim())
                    }
                }
            }
            response.decodeList<TtReceiptEntry>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTtEntriesByDate(adminPhone: String, date: String): List<TtReceiptEntry> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val response = client.postgrest["tt_receipts"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", adminPhone.trim())
                    }
                    eq("date", date)
                }
            }
            response.decodeList<TtReceiptEntry>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveTtEntry(entry: TtReceiptEntry) = withContext(Dispatchers.IO) {
        try {
            client.postgrest["tt_receipts"].insert(entry)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving TT entry", e)
            throw e
        }
    }

    suspend fun checkInvoiceExists(adminPhone: String, invoiceNumber: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val response = client.postgrest["tt_receipts"].select(columns = Columns.raw("invoice_number")) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", adminPhone.trim())
                    }
                    eq("invoice_number", invoiceNumber)
                }
            }
            response.decodeList<Map<String, String>>().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // --- Nozzle Readings Flows (Product-Wise Routing) ---
    private fun getTableNameForProduct(product: String): String {
        val p = product.trim().lowercase()
        val prefix = when {
            p == "ms" || p == "petrol" || p.contains("motor spirit") || p == "ms petrol" -> "petrol"
            p == "hsd" || p == "diesel" || p.contains("high speed diesel") || p == "hsd diesel" -> "diesel"
            p.contains("premium") || p.contains("speed") || p.contains("power") -> "premium"
            else -> p.replace(" ", "_")
        }
        return "${prefix}_readings"
    }

    fun getProductReadingsFlow(product: String, adminPhone: String): Flow<List<NozzleReading>> = flow {
        val tableName = getTableNameForProduct(product)
        Log.d(TAG, "Starting sub-flow for table: $tableName (from product: $product)")
        
        val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
        val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
        
        while (true) {
            try {
                val response = client.postgrest[tableName].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                    }
                    order("timestamp", Order.DESCENDING)
                }
                val list = response.decodeList<NozzleReading>()
                Log.d(TAG, "Table $tableName returned ${list.size} records")
                emit(list)
            } catch (e: Exception) {
                Log.e(TAG, "Error in flow for $tableName: ${e.message}")
                emit(emptyList<NozzleReading>())
            }
            kotlinx.coroutines.delay(10000)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun saveProductReadings(product: String, readings: List<NozzleReading>) = withContext(Dispatchers.IO) {
        val tableName = getTableNameForProduct(product)
        val fallbackTable = "${product.lowercase().replace(" ", "_")}_readings"
        
        try {
            Log.d(TAG, "Attempting to save ${readings.size} readings to table: $tableName (Source Product: $product)")
            client.postgrest[tableName].upsert(readings, onConflict = "report_id,nozzle_label")
            Log.d(TAG, "Successfully saved to $tableName.")
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: ""
            if (errorMsg.contains("column \"product_name\" of relation") && errorMsg.contains("does not exist")) {
                Log.w(TAG, "Table $tableName is missing 'product_name' column. Retrying without it...")
                // We could try to strip the product_name from the readings map here if needed
                // but the best fix is the SQL ALTER TABLE.
            }

            Log.w(TAG, "Failed to save to $tableName. Attempting fallback to $fallbackTable...", e)
            if (tableName != fallbackTable) {
                try {
                    client.postgrest[fallbackTable].upsert(readings, onConflict = "report_id,nozzle_label")
                    Log.d(TAG, "Successfully saved to fallback table $fallbackTable.")
                } catch (ex: Exception) {
                    throw ex
                }
            } else {
                throw e
            }
        }
    }

    // Point the unified methods to the product-wise tables
    suspend fun saveNozzleReadings(readings: List<NozzleReading>) = withContext(Dispatchers.IO) {
        readings.groupBy { it.productName }.forEach { (product, prodReadings) ->
            saveProductReadings(product, prodReadings)
        }
    }

    fun getNozzleReadingsFlow(adminPhone: String): Flow<List<NozzleReading>> = flow {
        val phone = adminPhone.trim()
        Log.d(TAG, "Starting unified nozzle readings flow for $phone")
        
        // 1. Get products (try multiple times if needed)
        val pumpInfo = getPumpInfo(phone)
        val products = pumpInfo?.productNames?.split(",")?.filter { it.isNotBlank() }?.map { it.trim() } 
            ?: listOf("Petrol", "Diesel")
        
        Log.d(TAG, "Products found for flow: $products")
        
        val flows = products.map { getProductReadingsFlow(it, phone) }
        
        if (flows.isEmpty()) {
            emit(emptyList<NozzleReading>())
        } else {
            combine(flows) { arrays ->
                arrays.flatMap { it.toList() }
            }.collect { readings ->
                Log.d(TAG, "Unified flow emitted ${readings.size} total readings")
                emit(readings)
            }
        }
    }.flowOn(Dispatchers.IO)

    // --- Legacy / Compatibility ---
    suspend fun saveNozzleReading(nozzleId: String, reading: NozzleReading) { 
        saveProductReadings(reading.productName, listOf(reading))
    }
    
    fun getNozzleReadingsFlow(nozzleId: String, adminPhone: String): Flow<List<NozzleReading>> {
        return getNozzleReadingsFlow(adminPhone).map { list -> list.filter { it.nozzleLabel == nozzleId } }
    }

    // --- Financial Entries ---
    fun getCreditEntriesFlow(adminPhone: String): Flow<List<CreditEntry>> = flow {
        val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
        val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
        
        while (true) {
            try {
                val response = client.postgrest["udhari"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                    }
                    order("timestamp", Order.DESCENDING)
                }
                emit(response.decodeList<CreditEntry>())
            } catch (e: Exception) { Log.e(TAG, "Error in Credit flow", e) }
            kotlinx.coroutines.delay(5000)
        }
    }.flowOn(Dispatchers.IO)

    fun getExpenseEntriesFlow(adminPhone: String): Flow<List<ExpenseEntry>> = flow {
        val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
        val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
        
        while (true) {
            try {
                val response = client.postgrest["expense"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                    }
                    order("timestamp", Order.DESCENDING)
                }
                emit(response.decodeList<ExpenseEntry>())
            } catch (e: Exception) { Log.e(TAG, "Error in Expense flow", e) }
            kotlinx.coroutines.delay(5000)
        }
    }.flowOn(Dispatchers.IO)

    fun getRecoveryEntriesFlow(adminPhone: String): Flow<List<RecoveryEntry>> = flow {
        val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
        val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
        
        while (true) {
            try {
                val response = client.postgrest["udhari_jama"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                    }
                    order("timestamp", Order.DESCENDING)
                }
                emit(response.decodeList<RecoveryEntry>())
            } catch (e: Exception) { Log.e(TAG, "Error in Recovery flow", e) }
            kotlinx.coroutines.delay(5000)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun saveCreditEntries(entries: List<CreditEntry>) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Saving ${entries.size} Credit entries to 'udhari' table")
        client.postgrest["udhari"].upsert(entries, onConflict = "report_id,timestamp,party,amount")
    }

    suspend fun saveExpenseEntries(entries: List<ExpenseEntry>) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Saving ${entries.size} Expense entries to 'expense' table")
        client.postgrest["expense"].upsert(entries, onConflict = "report_id,timestamp,category,amount")
    }

    suspend fun saveRecoveryEntries(entries: List<RecoveryEntry>) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Saving ${entries.size} Recovery entries to 'udhari_jama' table")
        client.postgrest["udhari_jama"].upsert(entries, onConflict = "report_id,timestamp,party,amount")
    }

    suspend fun deleteAudit(adminPhone: String, date: String, caName: String, timestamp: Long, reportId: String) = withContext(Dispatchers.IO) {
        val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
        val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
        
        // Delete main audit
        client.postgrest["saved_audits"].delete {
            filter {
                or {
                    eq("owner_admin_phone", formatted)
                    eq("owner_admin_phone", clean)
                    eq("owner_admin_phone", adminPhone.trim())
                }
                eq("date", date)
                eq("ca_name", caName)
                eq("timestamp", timestamp)
            }
        }
        
        // Trigger re-calculation of daily cash handover entry for this date
        updateDailyCashFromAudits(adminPhone, date)
        
        // Cascading delete for Manager Reports
        if (reportId.isNotEmpty()) {
            val filterBlock: io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder.() -> Unit = {
                eq("report_id", reportId)
                or {
                    eq("owner_admin_phone", formatted)
                    eq("owner_admin_phone", clean)
                    eq("owner_admin_phone", adminPhone.trim())
                }
            }
            try { client.postgrest["bank_deposits"].delete { filter(filterBlock) } } catch (e: Exception) { Log.w(TAG, "bank_deposits delete failed", e) }
            try { client.postgrest["manager_ledger"].delete { filter(filterBlock) } } catch (e: Exception) { Log.w(TAG, "manager_ledger delete failed", e) }
            try { client.postgrest["manager_transactions"].delete { filter(filterBlock) } } catch (e: Exception) { Log.w(TAG, "manager_transactions delete failed", e) }
            
            // Unlink CA audits that were reconciled in this report
            try {
                client.postgrest["saved_audits"].update(mapOf("reconciled_in_manager_report_id" to null)) {
                    filter {
                        eq("reconciled_in_manager_report_id", reportId)
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "Unlink CA audits failed", e) }
        }
        
        // Delete associated items from known tables (Legacy/CA logic)
        val filterBlock: io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder.() -> Unit = {
            if (reportId.isNotEmpty()) eq("report_id", reportId) else eq("timestamp", timestamp)
            or {
                eq("owner_admin_phone", formatted)
                eq("owner_admin_phone", clean)
                eq("owner_admin_phone", adminPhone.trim())
            }
        }
        
        client.postgrest["udhari"].delete { filter(filterBlock) }
        client.postgrest["expense"].delete { filter(filterBlock) }
        client.postgrest["udhari_jama"].delete { filter(filterBlock) }

        // Delete from all product-specific tables
        val pumpInfo = getPumpInfo(adminPhone)
        val products = pumpInfo?.productNames?.split(",")?.filter { it.isNotBlank() }?.map { it.trim() } ?: emptyList()
        products.forEach { product ->
            val tableName = getTableNameForProduct(product)
            val fallbackTable = "${product.lowercase().replace(" ", "_")}_readings"
            
            try {
                client.postgrest[tableName].delete { filter(filterBlock) }
            } catch (e: Exception) { /* Ignore */ }
            
            if (tableName != fallbackTable) {
                try {
                    client.postgrest[fallbackTable].delete { filter(filterBlock) }
                } catch (e: Exception) { /* Ignore */ }
            }
        }
        
        // Legacy cleanups
        try { client.postgrest["nozzle_readings"].delete { filter(filterBlock) } } catch (e: Exception) {}
        try { client.postgrest["ms_nozzle_readings"].delete { filter(filterBlock) } } catch (e: Exception) {}
        try { client.postgrest["hsd_nozzle_readings"].delete { filter(filterBlock) } } catch (e: Exception) {}
    }

    // --- Udhari Names & Balances ---
    suspend fun getUdhariNames(adminPhone: String): List<String> = withContext(Dispatchers.IO) {
        val phone = adminPhone.trim()
        val formatted = SupabaseUserManager.formatMobileNumber(phone)
        val clean = phone.filter { it.isDigit() }.takeLast(10)
        
        val masterList = mutableSetOf("Miscellaneous")
        Log.d(TAG, "getUdhariNames: Starting fetch for $phone")
        
        try {
            // 1. From Pump Config
            val pumpResponse = client.postgrest["pumps"].select(columns = Columns.raw("udhari_names")) {
                filter {
                    or {
                        eq("mobile_number", formatted)
                        eq("mobile_number", clean)
                        eq("mobile_number", phone)
                    }
                }
            }
            val pumpData = pumpResponse.decodeSingleOrNull<Map<String, String?>>()
            val configNames = pumpData?.get("udhari_names")?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
            masterList.addAll(configNames)
            Log.d(TAG, "getUdhariNames: Found ${configNames.size} names in config")
        } catch (e: Exception) {
            Log.w(TAG, "getUdhariNames: Error fetching from pumps table", e)
        }
            
        try {
            // 2. From Credits Table
            val udhariResponse = client.postgrest["udhari"].select(columns = Columns.raw("party")) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", phone)
                    }
                }
            }
            val udhariList = udhariResponse.decodeList<Map<String, String?>>()
            val creditNames = udhariList.mapNotNull { it["party"] }.filter { it.isNotBlank() }
            masterList.addAll(creditNames)
            Log.d(TAG, "getUdhariNames: Found ${creditNames.distinct().size} unique names in credits")
        } catch (e: Exception) {
            Log.w(TAG, "getUdhariNames: Error fetching from udhari table", e)
        }
            
        try {
            // 3. From Recoveries Table
            val jamaResponse = client.postgrest["udhari_jama"].select(columns = Columns.raw("party")) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", phone)
                    }
                }
            }
            val jamaList = jamaResponse.decodeList<Map<String, String?>>()
            val recoveryNames = jamaList.mapNotNull { it["party"] }.filter { it.isNotBlank() }
            masterList.addAll(recoveryNames)
            Log.d(TAG, "getUdhariNames: Found ${recoveryNames.distinct().size} unique names in recoveries")
        } catch (e: Exception) {
            Log.w(TAG, "getUdhariNames: Error fetching from udhari_jama table", e)
        }
            
        val finalResult = masterList.filter { it.isNotBlank() && it != "null" }.distinct().sortedBy { it.lowercase() }
        Log.d(TAG, "getUdhariNames: Final list size: ${finalResult.size}")
        finalResult
    }

    suspend fun getPartyBalance(adminPhone: String, partyName: String): Double = withContext(Dispatchers.IO) {
        try {
            val phone = adminPhone.trim()
            val formatted = SupabaseUserManager.formatMobileNumber(phone)
            val clean = phone.filter { it.isDigit() }.takeLast(10)
            val party = partyName.trim()
            
            val udhariResponse = client.postgrest["udhari"].select(columns = Columns.raw("amount")) {
                filter { 
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", phone)
                    }
                    eq("party", party)
                }
            }
            // Use a safer decoding approach for raw column selects
            val udhari = udhariResponse.decodeList<kotlinx.serialization.json.JsonObject>().sumOf { 
                it["amount"]?.toString()?.toDoubleOrNull() ?: 0.0 
            }
            
            val jamaResponse = client.postgrest["udhari_jama"].select(columns = Columns.raw("amount")) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", phone)
                    }
                    eq("party", party)
                }
            }
            val jama = jamaResponse.decodeList<kotlinx.serialization.json.JsonObject>().sumOf { 
                it["amount"]?.toString()?.toDoubleOrNull() ?: 0.0 
            }
            
            udhari - jama
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating balance for $partyName", e)
            0.0
        }
    }

    suspend fun saveUdhariNames(adminPhone: String, names: List<String>) = withContext(Dispatchers.IO) {
        val phone = adminPhone.trim()
        val cleanNewNames = names.filter { it.isNotBlank() && it != "null" }
        if (cleanNewNames.isEmpty()) return@withContext

        try {
            // Fetch current list to merge correctly
            val existingNames = getUdhariNames(phone)
            val combined = (existingNames + cleanNewNames).distinct().filter { it.isNotBlank() }.sortedBy { it.lowercase() }
            
            Log.d(TAG, "saveUdhariNames: Saving ${combined.size} total names for $phone")
            val data = mapOf(
                "mobile_number" to phone,
                "udhari_names" to combined.joinToString(";")
            )
            client.postgrest["pumps"].upsert(data, onConflict = "mobile_number")
        } catch (e: Exception) {
            Log.e(TAG, "saveUdhariNames: Error", e)
        }
    }

    // --- Manager Transactions (Split Storage) ---
    fun getManagerTransactionsFlow(adminPhone: String): Flow<List<ManagerTransaction>> = flow {
        val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
        val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
        
        while (true) {
            val deposits = try {
                client.postgrest["bank_deposits"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                    }
                }.decodeList<ManagerTransaction>()
            } catch (e: Exception) { emptyList() }

            val ledger = try {
                client.postgrest["manager_ledger"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                    }
                }.decodeList<ManagerTransaction>()
            } catch (e: Exception) { emptyList() }

            val legacy = try {
                client.postgrest["manager_transactions"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                    }
                }.decodeList<ManagerTransaction>()
            } catch (e: Exception) { emptyList() }

            val existingIds = (deposits + ledger).mapNotNull { it.id }.toSet()
            val filteredLegacy = legacy.filter { it.id == null || it.id !in existingIds }

            emit((deposits + ledger + filteredLegacy).sortedByDescending { it.timestamp })
            kotlinx.coroutines.delay(10000)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun saveManagerTransaction(transaction: ManagerTransaction) = withContext(Dispatchers.IO) {
        val tableName = if (transaction.type == "BANK_DEPOSIT") "bank_deposits" else "manager_ledger"
        val mapWithoutReportId = mutableMapOf<String, Any?>(
            "owner_admin_phone" to transaction.ownerAdminPhone,
            "date" to transaction.date,
            "amount" to transaction.amount,
            "type" to transaction.type,
            "description" to transaction.description,
            "timestamp" to transaction.timestamp
        )
        if (transaction.id != null) mapWithoutReportId["id"] = transaction.id

        try {
            Log.d(TAG, "Saving transaction to $tableName with reportId=${transaction.reportId}")
            client.postgrest[tableName].insert(transaction)
        } catch (e1: Exception) {
            Log.w(TAG, "Failed to insert into $tableName with full object. Trying without report_id map...", e1)
            try {
                client.postgrest[tableName].insert(mapWithoutReportId)
            } catch (e2: Exception) {
                Log.w(TAG, "Failed to insert into $tableName map. Attempting legacy manager_transactions...", e2)
                try {
                    client.postgrest["manager_transactions"].insert(transaction)
                } catch (e3: Exception) {
                    Log.w(TAG, "Failed to insert into legacy manager_transactions with full object. Trying legacy map...", e3)
                    try {
                        client.postgrest["manager_transactions"].insert(mapWithoutReportId)
                    } catch (ex: Exception) {
                        Log.e(TAG, "CRITICAL: Failed to save manager transaction to any table/schema", ex)
                        throw Exception(
                            "Database error: Table 'manager_ledger' or 'report_id' column not found in Supabase. " +
                            "Please execute the schema.sql script in your Supabase SQL Editor.",
                            ex
                        )
                    }
                }
            }
        }
    }

    suspend fun deleteManagerTransaction(id: Long, type: String) = withContext(Dispatchers.IO) {
        val tableName = if (type == "BANK_DEPOSIT") "bank_deposits" else "manager_ledger"
        try {
            client.postgrest[tableName].delete {
                filter { eq("id", id) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting manager transaction from $tableName, trying manager_transactions", e)
            try {
                client.postgrest["manager_transactions"].delete {
                    filter { eq("id", id) }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error deleting manager transaction from legacy table", ex)
            }
        }
    }

    suspend fun saveManagerReport(
        report: SavedAudit,
        transactions: List<ManagerTransaction>,
        reconciledAuditIds: List<String>
    ) = withContext(Dispatchers.IO) {
        // 1. Save Report
        client.postgrest["saved_audits"].insert(report)
        
        // 2. Save Transactions safely via saveManagerTransaction
        transactions.forEach { tx ->
            saveManagerTransaction(tx)
        }
        
        // 3. Update CA Audits to point to this report
        if (reconciledAuditIds.isNotEmpty()) {
            val formatted = SupabaseUserManager.formatMobileNumber(report.ownerAdminPhone.trim())
            val clean = report.ownerAdminPhone.filter { it.isDigit() }.takeLast(10)
            
            try {
                client.postgrest["saved_audits"].update(mapOf("reconciled_in_manager_report_id" to report.reportId)) {
                    filter {
                        isIn("report_id", reconciledAuditIds)
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", report.ownerAdminPhone.trim())
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to link reconciled audits to manager report", e)
            }
        }
    }

    suspend fun getUnreconciledAudits(adminPhone: String): List<SavedAudit> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val response = client.postgrest["saved_audits"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", adminPhone.trim())
                    }
                    neq("audit_type", "Manager's Report")
                }
            }
            // Filter locally for now to handle null check correctly in this SDK version
            response.decodeList<SavedAudit>().filter { it.reconciledInManagerReportId == null }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLatestManagerReport(adminPhone: String): SavedAudit? = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val response = client.postgrest["saved_audits"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", adminPhone.trim())
                    }
                    eq("audit_type", "Manager's Report")
                }
                order("timestamp", Order.DESCENDING)
                limit(1)
            }
            response.decodeSingleOrNull<SavedAudit>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getLatestSettledManagerReport(adminPhone: String): SavedAudit? = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val response = client.postgrest["saved_audits"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", adminPhone.trim())
                    }
                    eq("audit_type", "Manager's Report")
                    eq("is_settled", true)
                }
                order("timestamp", Order.DESCENDING)
                limit(1)
            }
            response.decodeSingleOrNull<SavedAudit>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getManagerReportsAfterTimestamp(adminPhone: String, timestamp: Long): List<SavedAudit> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val response = client.postgrest["saved_audits"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", adminPhone.trim())
                    }
                    eq("audit_type", "Manager's Report")
                    gt("timestamp", timestamp)
                }
                order("timestamp", Order.ASCENDING)
            }
            response.decodeList<SavedAudit>()
        } catch (e: Exception) {
            emptyList()
        }
    }
    suspend fun getAuditsAfterTimestamp(adminPhone: String, timestamp: Long): List<SavedAudit> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val response = client.postgrest["saved_audits"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", adminPhone.trim())
                    }
                    neq("audit_type", "Manager's Report")
                    gt("timestamp", timestamp)
                }
                order("timestamp", Order.ASCENDING)
            }
            response.decodeList<SavedAudit>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getManagerTransactionsByReportId(adminPhone: String, reportId: String): List<ManagerTransaction> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val deposits = try {
                client.postgrest["bank_deposits"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                        eq("report_id", reportId)
                    }
                }.decodeList<ManagerTransaction>()
            } catch (e: Exception) { emptyList() }
            
            val ledger = try {
                client.postgrest["manager_ledger"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                        eq("report_id", reportId)
                    }
                }.decodeList<ManagerTransaction>()
            } catch (e: Exception) { emptyList() }

            val legacy = try {
                client.postgrest["manager_transactions"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                        eq("report_id", reportId)
                    }
                }.decodeList<ManagerTransaction>()
            } catch (e: Exception) { emptyList() }

            val existingIds = (deposits + ledger).mapNotNull { it.id }.toSet()
            val filteredLegacy = legacy.filter { it.id == null || it.id !in existingIds }

            (deposits + ledger + filteredLegacy).sortedBy { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getRecoveryEntriesAfterTimestamp(adminPhone: String, timestamp: Long): List<RecoveryEntry> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            val response = client.postgrest["udhari_jama"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", adminPhone.trim())
                    }
                    gt("timestamp", timestamp)
                }
            }
            response.decodeList<RecoveryEntry>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getCreditEntriesAfterTimestamp(adminPhone: String, timestamp: Long): List<CreditEntry> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            val response = client.postgrest["udhari"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", adminPhone.trim())
                    }
                    gt("timestamp", timestamp)
                }
            }
            response.decodeList<CreditEntry>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getExpenseEntriesAfterTimestamp(adminPhone: String, timestamp: Long): List<ExpenseEntry> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            val response = client.postgrest["expense"].select(columns = Columns.ALL) {
                filter {
                    or {
                        eq("owner_admin_phone", formatted)
                        eq("owner_admin_phone", clean)
                        eq("owner_admin_phone", adminPhone.trim())
                    }
                    gt("timestamp", timestamp)
                }
            }
            response.decodeList<ExpenseEntry>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getManagerTransactionsAfterTimestamp(adminPhone: String, timestamp: Long): List<ManagerTransaction> = withContext(Dispatchers.IO) {
        try {
            val formatted = SupabaseUserManager.formatMobileNumber(adminPhone.trim())
            val clean = adminPhone.filter { it.isDigit() }.takeLast(10)
            
            val deposits = try {
                client.postgrest["bank_deposits"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                        gt("timestamp", timestamp)
                    }
                }.decodeList<ManagerTransaction>()
            } catch (e: Exception) { emptyList() }
            
            val ledger = try {
                client.postgrest["manager_ledger"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                        gt("timestamp", timestamp)
                    }
                }.decodeList<ManagerTransaction>()
            } catch (e: Exception) { emptyList() }

            val legacy = try {
                client.postgrest["manager_transactions"].select(columns = Columns.ALL) {
                    filter {
                        or {
                            eq("owner_admin_phone", formatted)
                            eq("owner_admin_phone", clean)
                            eq("owner_admin_phone", adminPhone.trim())
                        }
                        gt("timestamp", timestamp)
                    }
                }.decodeList<ManagerTransaction>()
            } catch (e: Exception) { emptyList() }

            val existingIds = (deposits + ledger).mapNotNull { it.id }.toSet()
            val filteredLegacy = legacy.filter { it.id == null || it.id !in existingIds }

            (deposits + ledger + filteredLegacy).sortedBy { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
