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
            val response = client.postgrest["pumps"].select(columns = Columns.ALL) {
                filter {
                    eq("mobile_number", mobileNumber)
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
        // Simple polling for now as a fallback for realtime if not fully configured
        while (true) {
            try {
                val response = client.postgrest["staff_members"].select(columns = Columns.ALL) {
                    filter {
                        eq("owner_admin_phone", adminPhone)
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

    // --- Audits ---
    fun getAuditsFlow(adminPhone: String): Flow<List<SavedAudit>> = flow {
        while (true) {
            try {
                val response = client.postgrest["saved_audits"].select(columns = Columns.ALL) {
                    filter {
                        eq("owner_admin_phone", adminPhone)
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
            val response = client.postgrest["daily_configs"].select(columns = Columns.ALL) {
                filter {
                    eq("admin_phone", adminPhone)
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
            val response = client.postgrest["daily_sales"].select(columns = Columns.ALL) {
                filter {
                    eq("admin_phone", adminPhone)
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
            val response = client.postgrest["daily_configs"].select(columns = Columns.ALL) {
                filter {
                    eq("admin_phone", adminPhone)
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
            val formatted = SmsOtpManager.formatMobileNumber(adminPhone.trim())
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
            // Unique on mobile_number + label to allow updating existing nozzles
            client.postgrest["registered_nozzles"].upsert(nozzles, onConflict = "mobile_number,label")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving nozzles", e)
        }
    }

    // --- TT Receipt Entries ---
    suspend fun getTtEntries(adminPhone: String): List<TtReceiptEntry> = withContext(Dispatchers.IO) {
        try {
            val response = client.postgrest["tt_receipts"].select(columns = Columns.ALL) {
                filter {
                    eq("owner_admin_phone", adminPhone)
                }
            }
            response.decodeList<TtReceiptEntry>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTtEntriesByDate(adminPhone: String, date: String): List<TtReceiptEntry> = withContext(Dispatchers.IO) {
        try {
            val response = client.postgrest["tt_receipts"].select(columns = Columns.ALL) {
                filter {
                    eq("owner_admin_phone", adminPhone)
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
        while (true) {
            try {
                val response = client.postgrest[tableName].select(columns = Columns.ALL) {
                    filter { eq("owner_admin_phone", adminPhone.trim()) }
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
        while (true) {
            try {
                val response = client.postgrest["udhari"].select(columns = Columns.ALL) {
                    filter { eq("owner_admin_phone", adminPhone) }
                    order("timestamp", Order.DESCENDING)
                }
                emit(response.decodeList<CreditEntry>())
            } catch (e: Exception) { Log.e(TAG, "Error in Credit flow", e) }
            kotlinx.coroutines.delay(5000)
        }
    }.flowOn(Dispatchers.IO)

    fun getExpenseEntriesFlow(adminPhone: String): Flow<List<ExpenseEntry>> = flow {
        while (true) {
            try {
                val response = client.postgrest["expense"].select(columns = Columns.ALL) {
                    filter { eq("owner_admin_phone", adminPhone) }
                    order("timestamp", Order.DESCENDING)
                }
                emit(response.decodeList<ExpenseEntry>())
            } catch (e: Exception) { Log.e(TAG, "Error in Expense flow", e) }
            kotlinx.coroutines.delay(5000)
        }
    }.flowOn(Dispatchers.IO)

    fun getRecoveryEntriesFlow(adminPhone: String): Flow<List<RecoveryEntry>> = flow {
        while (true) {
            try {
                val response = client.postgrest["udhari_jama"].select(columns = Columns.ALL) {
                    filter { eq("owner_admin_phone", adminPhone) }
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
        // Delete main audit
        client.postgrest["saved_audits"].delete {
            filter {
                eq("owner_admin_phone", adminPhone)
                eq("date", date)
                eq("ca_name", caName)
                eq("timestamp", timestamp)
            }
        }
        
        // Delete associated items from known tables
        val filterBlock: io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder.() -> Unit = {
            if (reportId.isNotEmpty()) eq("report_id", reportId) else eq("timestamp", timestamp)
            eq("owner_admin_phone", adminPhone)
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
        try {
            val phone = adminPhone.trim()
            val response = client.postgrest["pumps"].select(columns = Columns.raw("udhari_names")) {
                filter { eq("mobile_number", phone) }
            }
            val row = response.decodeSingleOrNull<Map<String, String?>>()
            val listStr = row?.get("udhari_names") ?: ""
            
            val list = if (listStr.isNullOrBlank()) {
                mutableSetOf("Miscellaneous")
            } else {
                listStr.split(";").filter { it.isNotBlank() }.toMutableSet()
            }
            
            list.filter { it.isNotBlank() }.sortedBy { it.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching udhari names", e)
            listOf("Miscellaneous")
        }
    }

    suspend fun getPartyBalance(adminPhone: String, partyName: String): Double = withContext(Dispatchers.IO) {
        try {
            val phone = adminPhone.trim()
            val party = partyName.trim()
            
            val udhariResponse = client.postgrest["udhari"].select(columns = Columns.raw("amount")) {
                filter { 
                    eq("owner_admin_phone", phone)
                    eq("party", party)
                }
            }
            // Use a safer decoding approach for raw column selects
            val udhari = udhariResponse.decodeList<kotlinx.serialization.json.JsonObject>().sumOf { 
                it["amount"]?.toString()?.toDoubleOrNull() ?: 0.0 
            }
            
            val jamaResponse = client.postgrest["udhari_jama"].select(columns = Columns.raw("amount")) {
                filter {
                    eq("owner_admin_phone", phone)
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
        try {
            val data = mapOf(
                "mobile_number" to adminPhone.trim(),
                "udhari_names" to names.joinToString(";")
            )
            client.postgrest["pumps"].upsert(data, onConflict = "mobile_number")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving udhari names", e)
        }
    }
}
