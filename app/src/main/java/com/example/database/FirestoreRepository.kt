package com.example.database

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object FirestoreRepository {
    private const val TAG = "FirestoreRepository"
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    // --- Pump Info ---
    suspend fun getPumpInfo(mobileNumber: String): PumpInfo? {
        return try {
            val snapshot = firestore.collection("pumps").document(mobileNumber).get().await()
            if (snapshot.exists()) {
                PumpInfo(
                    mobileNumber = mobileNumber,
                    pumpName = snapshot.getString("pumpName") ?: "Unnamed Pump",
                    numMsNozzles = snapshot.getLong("numMsNozzles")?.toInt() ?: 0,
                    msNozzleLabels = snapshot.getString("msNozzleLabels") ?: "",
                    numHsdNozzles = snapshot.getLong("numHsdNozzles")?.toInt() ?: 0,
                    hsdNozzleLabels = snapshot.getString("hsdNozzleLabels") ?: "",
                    numMsTanks = snapshot.getLong("numMsTanks")?.toInt() ?: 1,
                    numHsdTanks = snapshot.getLong("numHsdTanks")?.toInt() ?: 1,
                    msTankLabels = snapshot.getString("msTankLabels") ?: "",
                    hsdTankLabels = snapshot.getString("hsdTankLabels") ?: "",
                    productNames = snapshot.getString("productNames") ?: "",
                    updatedAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis()
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching pump info", e)
            null
        }
    }

    suspend fun savePumpInfo(pump: PumpInfo) {
        val data = hashMapOf(
            "pumpName" to pump.pumpName,
            "numMsNozzles" to pump.numMsNozzles,
            "msNozzleLabels" to pump.msNozzleLabels,
            "numHsdNozzles" to pump.numHsdNozzles,
            "hsdNozzleLabels" to pump.hsdNozzleLabels,
            "numMsTanks" to pump.numMsTanks,
            "numHsdTanks" to pump.numHsdTanks,
            "msTankLabels" to pump.msTankLabels,
            "hsdTankLabels" to pump.hsdTankLabels,
            "productNames" to pump.productNames,
            "updatedAt" to System.currentTimeMillis()
        )
        firestore.collection("pumps").document(pump.mobileNumber).set(data, SetOptions.merge()).await()
    }

    // --- Staff Management ---
    fun getStaffMembersFlow(adminPhone: String): Flow<List<StaffMember>> = callbackFlow {
        val subscription = firestore.collection("pumps").document(adminPhone).collection("staff")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    StaffMember(
                        phone = doc.id,
                        ownerAdminPhone = adminPhone,
                        name = doc.getString("name") ?: "",
                        role = doc.getString("role") ?: "Staff"
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addStaffMember(staff: StaffMember) {
        val data = hashMapOf(
            "name" to staff.name,
            "role" to staff.role,
            "phone" to staff.phone
        )
        firestore.collection("pumps").document(staff.ownerAdminPhone).collection("staff")
            .document(staff.phone).set(data, SetOptions.merge()).await()
    }

    suspend fun deleteStaffMember(phone: String, adminPhone: String) {
        firestore.collection("pumps").document(adminPhone).collection("staff").document(phone).delete().await()
    }

    // --- Audits ---
    fun getAuditsFlow(adminPhone: String): Flow<List<SavedAudit>> = callbackFlow {
        val subscription = firestore.collection("pumps").document(adminPhone).collection("saved_audits")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    SavedAudit(
                        id = 0, // Firestore IDs are strings, but our model uses Int (auto-generated locally). 
                                // We might need to change SavedAudit model or ignore local ID.
                        ownerAdminPhone = adminPhone,
                        date = doc.getString("date") ?: "",
                        caName = doc.getString("caName") ?: "",
                        meterNo = doc.getString("meterNo") ?: "",
                        auditType = doc.getString("auditType") ?: "",
                        summaryText = doc.getString("summaryText") ?: "",
                        htmlContent = doc.getString("htmlContent") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        cashSubmitted = doc.getDouble("cashSubmitted") ?: 0.0,
                        actualCashCollected = doc.getDouble("actualCashCollected") ?: 0.0
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun saveAudit(audit: SavedAudit) {
        val docId = "audit_${audit.date.replace("-", "_")}_${audit.caName.replace(" ", "_")}_${audit.timestamp}"
        val data = hashMapOf(
            "date" to audit.date,
            "caName" to audit.caName,
            "meterNo" to audit.meterNo,
            "auditType" to audit.auditType,
            "summaryText" to audit.summaryText,
            "htmlContent" to audit.htmlContent,
            "timestamp" to audit.timestamp,
            "cashSubmitted" to audit.cashSubmitted,
            "actualCashCollected" to audit.actualCashCollected
        )
        firestore.collection("pumps").document(audit.ownerAdminPhone).collection("saved_audits")
            .document(docId).set(data, SetOptions.merge()).await()
    }

    // --- Daily Config (Rates, Densities) ---
    suspend fun getDailyConfig(adminPhone: String, date: String): Map<String, String> {
        return try {
            val docId = "config_$date"
            val snapshot = firestore.collection("pumps").document(adminPhone).collection("daily_configs")
                .document(docId).get().await()
            if (snapshot.exists()) {
                snapshot.data?.mapValues { it.value.toString() } ?: emptyMap()
            } else emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun saveDailyConfig(adminPhone: String, date: String, config: Map<String, String>) {
        val docId = "config_$date"
        firestore.collection("pumps").document(adminPhone).collection("daily_configs")
            .document(docId).set(config, SetOptions.merge()).await()
    }

    // --- Daily Pump Data (Enhanced) ---
    suspend fun getDailyPumpData(adminPhone: String, date: String): List<DailyPumpData> {
        return try {
            val snapshot = firestore.collection("pumps").document(adminPhone)
                .collection("daily_pump_data").whereEqualTo("date", date).get().await()
            snapshot.documents.map { doc ->
                DailyPumpData(
                    adminPhone = adminPhone,
                    date = date,
                    productName = doc.getString("productName") ?: "",
                    productId = doc.getString("productId") ?: "",
                    density = doc.getDouble("density") ?: 0.0,
                    rate = doc.getDouble("rate") ?: 0.0,
                    openingStock = doc.getDouble("openingStock") ?: 0.0,
                    enteredBy = doc.getString("enteredBy") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveDailyPumpData(adminPhone: String, date: String, dataList: List<DailyPumpData>) {
        val batch = firestore.batch()
        val colRef = firestore.collection("pumps").document(adminPhone).collection("daily_pump_data")
        
        // Mirror map for legacy daily_configs
        val legacyConfig = mutableMapOf<String, String>()

        dataList.forEach { data ->
            val docId = "${date.replace("-", "_")}_${data.productName.replace(" ", "_")}"
            val map = hashMapOf(
                "date" to data.date,
                "productName" to data.productName,
                "productId" to data.productId,
                "density" to data.density,
                "rate" to data.rate,
                "openingStock" to data.openingStock,
                "enteredBy" to data.enteredBy,
                "timestamp" to data.timestamp
            )
            batch.set(colRef.document(docId), map, SetOptions.merge())

            // Sync with legacy collections
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
        
        batch.commit().await()

        if (legacyConfig.isNotEmpty()) {
            saveDailyConfig(adminPhone, date, legacyConfig)
        }
    }

    suspend fun getAllDailyConfigs(adminPhone: String): Map<String, Map<String, String>> {
        return try {
            val snapshot = firestore.collection("pumps").document(adminPhone).collection("daily_configs").get().await()
            snapshot.documents.associate { doc ->
                val date = doc.id.removePrefix("config_")
                date to (doc.data?.mapValues { it.value.toString() } ?: emptyMap())
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // --- Registered Nozzles ---
    suspend fun getRegisteredNozzles(adminPhone: String): List<RegisteredNozzle> {
        val snapshot = firestore.collection("pumps").document(adminPhone).collection("registered_nozzles").get().await()
        return snapshot.documents.map { doc ->
            RegisteredNozzle(
                mobileNumber = adminPhone,
                nozzleType = doc.getString("nozzleType") ?: "MS",
                label = doc.getString("label") ?: "",
                nozzleName = doc.getString("nozzleName") ?: "",
                nozzleNumber = doc.getString("nozzleNumber") ?: "",
                nozzleId = doc.getString("nozzleId") ?: "",
                productId = doc.getString("productId") ?: "",
                tankName = doc.getString("tankName") ?: "",
                tankId = doc.getString("tankId") ?: "",
                nozzleIndex = doc.getLong("nozzleIndex")?.toInt() ?: 0,
                initialReading = doc.getDouble("initialReading") ?: 0.0,
                isActive = doc.getBoolean("isActive") ?: true
            )
        }
    }

    suspend fun saveNozzles(adminPhone: String, nozzles: List<RegisteredNozzle>) {
        val batch = firestore.batch()
        val colRef = firestore.collection("pumps").document(adminPhone).collection("registered_nozzles")
        nozzles.forEach { nozzle ->
            val docId = "${nozzle.nozzleType}_${nozzle.label.replace(" ", "_")}"
            val data = hashMapOf(
                "nozzleType" to nozzle.nozzleType,
                "label" to nozzle.label,
                "nozzleName" to nozzle.nozzleName,
                "nozzleNumber" to nozzle.nozzleNumber,
                "nozzleId" to nozzle.nozzleId,
                "productId" to nozzle.productId,
                "tankName" to nozzle.tankName,
                "tankId" to nozzle.tankId,
                "nozzleIndex" to nozzle.nozzleIndex,
                "initialReading" to nozzle.initialReading,
                "isActive" to nozzle.isActive
            )
            batch.set(colRef.document(docId), data, SetOptions.merge())
        }
        batch.commit().await()
    }

    // --- TT Receipt Entries ---
    suspend fun getTtEntries(adminPhone: String): List<TtReceiptEntry> {
        val snapshot = firestore.collection("pumps").document(adminPhone).collection("tt_receipt_entries").get().await()
        return snapshot.documents.map { doc ->
            TtReceiptEntry(
                ownerAdminPhone = adminPhone,
                date = doc.getString("date") ?: "",
                invoiceNumber = doc.getString("invoiceNumber") ?: "",
                ttNumber = doc.getString("ttNumber") ?: "",
                invoiceAmount = doc.getDouble("invoiceAmount") ?: 0.0,
                msInvoiceAmount = doc.getDouble("msInvoiceAmount") ?: 0.0,
                hsdInvoiceAmount = doc.getDouble("hsdInvoiceAmount") ?: 0.0,
                msInvoiceDensity = doc.getDouble("msInvoiceDensity") ?: 0.0,
                hsdInvoiceDensity = doc.getDouble("hsdInvoiceDensity") ?: 0.0,
                msActualFuelDensity = doc.getDouble("msActualFuelDensity") ?: 0.0,
                hsdActualFuelDensity = doc.getDouble("hsdActualFuelDensity") ?: 0.0,
                msPreDecantationStock = doc.getDouble("msPreDecantationStock") ?: 0.0,
                hsdPreDecantationStock = doc.getDouble("hsdPreDecantationStock") ?: 0.0,
                msPostDecantationStock = doc.getDouble("msPostDecantationStock") ?: 0.0,
                hsdPostDecantationStock = doc.getDouble("hsdPostDecantationStock") ?: 0.0,
                msShortage = doc.getDouble("msShortage") ?: 0.0,
                hsdShortage = doc.getDouble("hsdShortage") ?: 0.0,
                timestamp = doc.getLong("timestamp") ?: 0L
            )
        }
    }

    // --- Nozzle Readings ---
    fun getMsNozzleReadingsFlow(adminPhone: String): Flow<List<MsNozzleReading>> = callbackFlow {
        val subscription = firestore.collection("pumps").document(adminPhone).collection("ms_nozzle_readings")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    MsNozzleReading(
                        ownerAdminPhone = adminPhone,
                        nozzleLabel = doc.getString("nozzleLabel") ?: "",
                        openingReading = doc.getDouble("openingReading") ?: 0.0,
                        closingReading = doc.getDouble("closingReading") ?: 0.0,
                        testing = doc.getDouble("testing") ?: 0.0,
                        caName = doc.getString("caName") ?: "",
                        phone = doc.getString("phone") ?: "",
                        udhar = doc.getDouble("udhar") ?: 0.0,
                        kharch = doc.getDouble("kharch") ?: 0.0,
                        udhariJama = doc.getDouble("udhariJama") ?: 0.0,
                        msSales = doc.getDouble("msSales") ?: 0.0,
                        hsdSales = doc.getDouble("hsdSales") ?: 0.0,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        date = doc.getString("date") ?: ""
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }

    fun getHsdNozzleReadingsFlow(adminPhone: String): Flow<List<HsdNozzleReading>> = callbackFlow {
        val subscription = firestore.collection("pumps").document(adminPhone).collection("hsd_nozzle_readings")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    HsdNozzleReading(
                        ownerAdminPhone = adminPhone,
                        nozzleLabel = doc.getString("nozzleLabel") ?: "",
                        openingReading = doc.getDouble("openingReading") ?: 0.0,
                        closingReading = doc.getDouble("closingReading") ?: 0.0,
                        testing = doc.getDouble("testing") ?: 0.0,
                        caName = doc.getString("caName") ?: "",
                        phone = doc.getString("phone") ?: "",
                        udhar = doc.getDouble("udhar") ?: 0.0,
                        kharch = doc.getDouble("kharch") ?: 0.0,
                        udhariJama = doc.getDouble("udhariJama") ?: 0.0,
                        msSales = doc.getDouble("msSales") ?: 0.0,
                        hsdSales = doc.getDouble("hsdSales") ?: 0.0,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        date = doc.getString("date") ?: ""
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun saveMsNozzleReadings(adminPhone: String, readings: List<MsNozzleReading>) {
        val batch = firestore.batch()
        val colRef = firestore.collection("pumps").document(adminPhone).collection("ms_nozzle_readings")
        readings.forEach { r ->
            val docId = "ms_${r.date.replace("-", "_")}_${r.nozzleLabel.replace(" ", "_")}_${r.timestamp}"
            val data = hashMapOf(
                "nozzleLabel" to r.nozzleLabel,
                "openingReading" to r.openingReading,
                "closingReading" to r.closingReading,
                "testing" to r.testing,
                "caName" to r.caName,
                "phone" to r.phone,
                "udhar" to r.udhar,
                "kharch" to r.kharch,
                "udhariJama" to r.udhariJama,
                "msSales" to r.msSales,
                "hsdSales" to r.hsdSales,
                "timestamp" to r.timestamp,
                "date" to r.date
            )
            batch.set(colRef.document(docId), data, SetOptions.merge())
        }
        batch.commit().await()
    }

    suspend fun saveHsdNozzleReadings(adminPhone: String, readings: List<HsdNozzleReading>) {
        val batch = firestore.batch()
        val colRef = firestore.collection("pumps").document(adminPhone).collection("hsd_nozzle_readings")
        readings.forEach { r ->
            val docId = "hsd_${r.date.replace("-", "_")}_${r.nozzleLabel.replace(" ", "_")}_${r.timestamp}"
            val data = hashMapOf(
                "nozzleLabel" to r.nozzleLabel,
                "openingReading" to r.openingReading,
                "closingReading" to r.closingReading,
                "testing" to r.testing,
                "caName" to r.caName,
                "phone" to r.phone,
                "udhar" to r.udhar,
                "kharch" to r.kharch,
                "udhariJama" to r.udhariJama,
                "msSales" to r.msSales,
                "hsdSales" to r.hsdSales,
                "timestamp" to r.timestamp,
                "date" to r.date
            )
            batch.set(colRef.document(docId), data, SetOptions.merge())
        }
        batch.commit().await()
    }

    fun getGeneralNozzleReadingsFlow(adminPhone: String): Flow<List<GeneralNozzleReading>> = callbackFlow {
        val subscription = firestore.collection("pumps").document(adminPhone).collection("general_nozzle_readings")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    GeneralNozzleReading(
                        ownerAdminPhone = adminPhone,
                        productName = doc.getString("productName") ?: "MS",
                        nozzleLabel = doc.getString("nozzleLabel") ?: "",
                        openingReading = doc.getDouble("openingReading") ?: 0.0,
                        closingReading = doc.getDouble("closingReading") ?: 0.0,
                        testing = doc.getDouble("testing") ?: 0.0,
                        caName = doc.getString("caName") ?: "",
                        phone = doc.getString("phone") ?: "",
                        udhar = doc.getDouble("udhar") ?: 0.0,
                        kharch = doc.getDouble("kharch") ?: 0.0,
                        udhariJama = doc.getDouble("udhariJama") ?: 0.0,
                        productSalesAmount = doc.getDouble("productSalesAmount") ?: 0.0,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        date = doc.getString("date") ?: ""
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun saveGeneralNozzleReadings(adminPhone: String, readings: List<GeneralNozzleReading>) {
        val batch = firestore.batch()
        val colRef = firestore.collection("pumps").document(adminPhone).collection("general_nozzle_readings")
        readings.forEach { r ->
            val docId = "gen_${r.date.replace("-", "_")}_${r.nozzleLabel.replace(" ", "_")}_${r.timestamp}"
            val data = hashMapOf(
                "productName" to r.productName,
                "nozzleLabel" to r.nozzleLabel,
                "openingReading" to r.openingReading,
                "closingReading" to r.closingReading,
                "testing" to r.testing,
                "caName" to r.caName,
                "phone" to r.phone,
                "udhar" to r.udhar,
                "kharch" to r.kharch,
                "udhariJama" to r.udhariJama,
                "productSalesAmount" to r.productSalesAmount,
                "timestamp" to r.timestamp,
                "date" to r.date
            )
            batch.set(colRef.document(docId), data, SetOptions.merge())
        }
        batch.commit().await()
    }

    suspend fun deleteAudit(adminPhone: String, date: String, caName: String, timestamp: Long) {
        val docId = "audit_${date.replace("-", "_")}_${caName.replace(" ", "_")}_$timestamp"
        firestore.collection("pumps").document(adminPhone).collection("saved_audits").document(docId).delete().await()
    }

    suspend fun saveTtEntry(entry: TtReceiptEntry) {
        val docId = "tt_${entry.date.replace("-", "_")}_${entry.invoiceNumber.replace(" ", "_")}"
        val data = hashMapOf(
            "date" to entry.date,
            "invoiceNumber" to entry.invoiceNumber,
            "ttNumber" to entry.ttNumber,
            "invoiceAmount" to entry.invoiceAmount,
            "msInvoiceAmount" to entry.msInvoiceAmount,
            "hsdInvoiceAmount" to entry.hsdInvoiceAmount,
            "msInvoiceDensity" to entry.msInvoiceDensity,
            "hsdInvoiceDensity" to entry.hsdInvoiceDensity,
            "msActualFuelDensity" to entry.msActualFuelDensity,
            "hsdActualFuelDensity" to entry.hsdActualFuelDensity,
            "msPreDecantationStock" to entry.msPreDecantationStock,
            "hsdPreDecantationStock" to entry.hsdPreDecantationStock,
            "msPostDecantationStock" to entry.msPostDecantationStock,
            "hsdPostDecantationStock" to entry.hsdPostDecantationStock,
            "msShortage" to entry.msShortage,
            "hsdShortage" to entry.hsdShortage,
            "timestamp" to entry.timestamp
        )
        firestore.collection("pumps").document(entry.ownerAdminPhone).collection("tt_receipt_entries")
            .document(docId).set(data, SetOptions.merge()).await()
    }

    // --- Udhari Names ---
    suspend fun getUdhariNames(adminPhone: String): List<String> {
        return try {
            val snapshot = firestore.collection("pumps").document(adminPhone).get().await()
            val listStr = snapshot.getString("udhari_names") ?: ""
            if (listStr.isBlank()) listOf("Miscellaneous")
            else listStr.split(";").filter { it.isNotBlank() }
        } catch (e: Exception) {
            listOf("Miscellaneous")
        }
    }

    suspend fun saveUdhariNames(adminPhone: String, names: List<String>) {
        val data = hashMapOf("udhari_names" to names.joinToString(";"))
        firestore.collection("pumps").document(adminPhone).set(data, SetOptions.merge()).await()
    }
}
