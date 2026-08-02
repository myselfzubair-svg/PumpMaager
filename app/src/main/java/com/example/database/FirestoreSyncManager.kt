package com.example.database

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object FirestoreSyncManager {
    private const val TAG = "FirestoreSyncManager"
    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /**
     * Synchronizes all data for a specific pump mobile number.
     * Pulls latest configurations, nozzles, and staff.
     * Uploads local audits, readings, and receipts, and downloads any missing ones.
     */
    suspend fun syncAll(
        context: Context,
        pumpMobileNumber: String,
        onProgress: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (pumpMobileNumber.isBlank()) {
            return@withContext Result.failure(Exception("Pump mobile number is empty"))
        }

        try {
            val db = AppDatabase.getDatabase(context)
            onProgress("Connecting to Cloud...")

            // 1. Sync PumpInfo
            onProgress("Syncing Pump configuration...")
            syncPumpInfo(db, pumpMobileNumber)

            // 2. Sync RegisteredNozzles
            onProgress("Syncing Nozzle registration...")
            syncRegisteredNozzles(db, pumpMobileNumber)

            // 3. Sync Staff
            onProgress("Syncing Authorized Staff...")
            syncStaff(db, pumpMobileNumber)

            // 4. Sync LoginInfo
            onProgress("Syncing Login accounts...")
            syncLoginInfo(db, pumpMobileNumber)

            // 5. Sync SavedAudits
            onProgress("Syncing Audits...")
            syncSavedAudits(db, pumpMobileNumber)

            // 6. Sync MsNozzleReadings
            onProgress("Syncing Petrol Nozzle Readings...")
            syncMsNozzleReadings(db, pumpMobileNumber)

            // 7. Sync HsdNozzleReadings
            onProgress("Syncing Diesel Nozzle Readings...")
            syncHsdNozzleReadings(db, pumpMobileNumber)

            // 8. Sync TtReceiptEntries
            onProgress("Syncing Tanker Receipts...")
            syncTtReceiptEntries(db, pumpMobileNumber)

            onProgress("Synchronization Completed!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            val errMsg = e.localizedMessage ?: ""
            val userFriendlyException = if (errMsg.contains("offline", ignoreCase = true) || 
                                           errMsg.contains("unavailable", ignoreCase = true) || 
                                           errMsg.contains("unreachable", ignoreCase = true) ||
                                           errMsg.contains("network", ignoreCase = true) ||
                                           errMsg.contains("channel shutdown", ignoreCase = true) ||
                                           e is java.io.IOException) {
                Exception("Offline Mode: Data saved locally. Will sync automatically when connection is restored.")
            } else {
                e
            }
            Result.failure(userFriendlyException)
        }
    }

    private suspend fun syncPumpInfo(db: AppDatabase, pumpMobileNumber: String) {
        val docRef = firestore.collection("pumps").document(pumpMobileNumber)
        val task = docRef.get()
        val snapshot = Tasks.await(task)

        val localPump = db.pumpInfoDao().getPumpInfoByMobile(pumpMobileNumber)

        if (snapshot.exists()) {
            val cloudUpdatedAt = snapshot.getLong("updatedAt") ?: 0L
            val localUpdatedAt = localPump?.updatedAt ?: 0L

            if (cloudUpdatedAt > localUpdatedAt || localPump == null) {
                // Cloud is newer, update local DB
                val updatedPump = PumpInfo(
                    mobileNumber = pumpMobileNumber,
                    pumpName = snapshot.getString("pumpName") ?: "Unnamed Pump",
                    numMsNozzles = snapshot.getLong("numMsNozzles")?.toInt() ?: 0,
                    msNozzleLabels = snapshot.getString("msNozzleLabels") ?: "",
                    numHsdNozzles = snapshot.getLong("numHsdNozzles")?.toInt() ?: 0,
                    hsdNozzleLabels = snapshot.getString("hsdNozzleLabels") ?: "",
                    numMsTanks = snapshot.getLong("numMsTanks")?.toInt() ?: 1,
                    numHsdTanks = snapshot.getLong("numHsdTanks")?.toInt() ?: 1,
                    updatedAt = cloudUpdatedAt
                )
                db.pumpInfoDao().insertPumpInfo(updatedPump)
            } else if (localUpdatedAt > cloudUpdatedAt) {
                // Local is newer, upload to Cloud
                val data = hashMapOf(
                    "mobileNumber" to localPump.mobileNumber,
                    "pumpName" to localPump.pumpName,
                    "numMsNozzles" to localPump.numMsNozzles,
                    "msNozzleLabels" to localPump.msNozzleLabels,
                    "numHsdNozzles" to localPump.numHsdNozzles,
                    "hsdNozzleLabels" to localPump.hsdNozzleLabels,
                    "numMsTanks" to localPump.numMsTanks,
                    "numHsdTanks" to localPump.numHsdTanks,
                    "updatedAt" to localPump.updatedAt
                )
                Tasks.await(docRef.set(data, SetOptions.merge()))
            }
        } else {
            // Document doesn't exist in cloud, upload if exists locally
            if (localPump != null) {
                val data = hashMapOf(
                    "mobileNumber" to localPump.mobileNumber,
                    "pumpName" to localPump.pumpName,
                    "numMsNozzles" to localPump.numMsNozzles,
                    "msNozzleLabels" to localPump.msNozzleLabels,
                    "numHsdNozzles" to localPump.numHsdNozzles,
                    "hsdNozzleLabels" to localPump.hsdNozzleLabels,
                    "numMsTanks" to localPump.numMsTanks,
                    "numHsdTanks" to localPump.numHsdTanks,
                    "updatedAt" to localPump.updatedAt
                )
                Tasks.await(docRef.set(data, SetOptions.merge()))
            }
        }
    }

    private suspend fun syncRegisteredNozzles(db: AppDatabase, pumpMobileNumber: String) {
        val colRef = firestore.collection("pumps").document(pumpMobileNumber).collection("registered_nozzles")
        val task = colRef.get()
        val snapshot = Tasks.await(task)

        val localNozzles = db.registeredNozzleDao().getNozzlesByPumpMobile(pumpMobileNumber)

        // 1. Download missing/updated cloud nozzles
        snapshot.documents.forEach { doc ->
            val label = doc.getString("label")
            val nozzleType = doc.getString("nozzleType") ?: "MS"
            val nozzleIndex = doc.getLong("nozzleIndex")?.toInt() ?: 0
            val initialReading = doc.getDouble("initialReading") ?: 0.0

            if (label != null) {
                val matchedLocal = localNozzles.firstOrNull { it.label == label && it.nozzleType == nozzleType }
                if (matchedLocal == null) {
                    db.registeredNozzleDao().insertNozzles(
                        listOf(
                            RegisteredNozzle(
                                mobileNumber = pumpMobileNumber,
                                nozzleType = nozzleType,
                                label = label,
                                nozzleIndex = nozzleIndex,
                                initialReading = initialReading
                            )
                        )
                    )
                }
            }
        }

        // 2. Upload missing local nozzles
        val updatedLocalNozzles = db.registeredNozzleDao().getNozzlesByPumpMobile(pumpMobileNumber)
        for (nozzle in updatedLocalNozzles) {
            val docId = "${nozzle.nozzleType}_${nozzle.label.replace(" ", "_")}"
            val docRef = colRef.document(docId)
            val data = hashMapOf(
                "nozzleType" to nozzle.nozzleType,
                "label" to nozzle.label,
                "nozzleIndex" to nozzle.nozzleIndex,
                "initialReading" to nozzle.initialReading
            )
            Tasks.await(docRef.set(data, SetOptions.merge()))
        }
    }

    private suspend fun syncStaff(db: AppDatabase, pumpMobileNumber: String) {
        val colRef = firestore.collection("pumps").document(pumpMobileNumber).collection("staff")
        val task = colRef.get()
        val snapshot = Tasks.await(task)

        val localStaff = db.staffDao().getAllStaff()

        // 1. Download cloud staff to local DB
        snapshot.documents.forEach { doc ->
            val phone = doc.getString("phone")
            val name = doc.getString("name") ?: ""
            val passwordHash = doc.getString("passwordHash") ?: ""
            val role = doc.getString("role") ?: "Staff"

            if (phone != null) {
                val matchedLocal = localStaff.firstOrNull { it.phone == phone }
                if (matchedLocal == null) {
                    db.staffDao().insertStaff(Staff(phone, name, passwordHash, role))
                }
            }
        }

        // 2. Upload local staff to cloud
        val updatedLocalStaff = db.staffDao().getAllStaff()
        for (staff in updatedLocalStaff) {
            val docRef = colRef.document(staff.phone)
            val data = hashMapOf(
                "phone" to staff.phone,
                "name" to staff.name,
                "passwordHash" to staff.passwordHash,
                "role" to staff.role
            )
            Tasks.await(docRef.set(data, SetOptions.merge()))
        }
    }

    private suspend fun syncLoginInfo(db: AppDatabase, pumpMobileNumber: String) {
        val colRef = firestore.collection("pumps").document(pumpMobileNumber).collection("login_info")
        val task = colRef.get()
        val snapshot = Tasks.await(task)

        val localLogins = db.loginInfoDao().getAllLoginInfo()

        // 1. Download cloud login info
        snapshot.documents.forEach { doc ->
            val mobile = doc.getString("mobileNumber")
            val username = doc.getString("username") ?: ""
            val passwordHash = doc.getString("passwordHash") ?: ""
            val verifiedAt = doc.getLong("verifiedAt") ?: System.currentTimeMillis()

            if (mobile != null) {
                val matchedLocal = localLogins.firstOrNull { it.mobileNumber == mobile }
                if (matchedLocal == null) {
                    db.loginInfoDao().insertLoginInfo(LoginInfo(mobile, username, passwordHash, verifiedAt))
                }
            }
        }

        // 2. Upload local login info
        val updatedLocalLogins = db.loginInfoDao().getAllLoginInfo()
        for (login in updatedLocalLogins) {
            if (login.mobileNumber == pumpMobileNumber) {
                val docRef = colRef.document(login.mobileNumber)
                val data = hashMapOf(
                    "mobileNumber" to login.mobileNumber,
                    "username" to login.username,
                    "passwordHash" to login.passwordHash,
                    "verifiedAt" to login.verifiedAt
                )
                Tasks.await(docRef.set(data, SetOptions.merge()))
            }
        }
    }

    private suspend fun syncSavedAudits(db: AppDatabase, pumpMobileNumber: String) {
        val colRef = firestore.collection("pumps").document(pumpMobileNumber).collection("saved_audits")
        val task = colRef.get()
        val snapshot = Tasks.await(task)

        val localAudits = db.savedAuditDao().getAllAudits().first()

        // 1. Download missing cloud audits
        snapshot.documents.forEach { doc ->
            val date = doc.getString("date")
            val caName = doc.getString("caName") ?: ""
            val meterNo = doc.getString("meterNo") ?: ""
            val auditType = doc.getString("auditType") ?: ""
            val summaryText = doc.getString("summaryText") ?: ""
            val htmlContent = doc.getString("htmlContent") ?: ""
            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
            val cashSubmitted = doc.getDouble("cashSubmitted") ?: 0.0
            val actualCashCollected = doc.getDouble("actualCashCollected") ?: 0.0

            if (date != null) {
                // Match based on key fields (date + caName + type + timestamp)
                val matchedLocal = localAudits.firstOrNull { 
                    it.date == date && it.caName == caName && it.auditType == auditType && Math.abs(it.timestamp - timestamp) < 10000 
                }
                if (matchedLocal == null) {
                    db.savedAuditDao().insertAudit(
                        SavedAudit(
                            date = date,
                            caName = caName,
                            meterNo = meterNo,
                            auditType = auditType,
                            summaryText = summaryText,
                            htmlContent = htmlContent,
                            timestamp = timestamp,
                            cashSubmitted = cashSubmitted,
                            actualCashCollected = actualCashCollected
                        )
                    )
                }
            }
        }

        // 2. Upload local audits to cloud
        val updatedLocalAudits = db.savedAuditDao().getAllAudits().first()
        for (audit in updatedLocalAudits) {
            val docId = "audit_${audit.date.replace("-", "_")}_${audit.caName.replace(" ", "_")}_${audit.timestamp}"
            val docRef = colRef.document(docId)
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
            Tasks.await(docRef.set(data, SetOptions.merge()))
        }
    }

    suspend fun deleteSavedAuditFromFirestore(context: Context, audit: SavedAudit) {
        try {
            val sharedPrefs = context.getSharedPreferences("pump_manager_prefs", Context.MODE_PRIVATE)
            val loggedInMobile = sharedPrefs.getString("session_mobile", "") ?: ""
            if (loggedInMobile.isNotEmpty()) {
                val docId = "audit_${audit.date.replace("-", "_")}_${audit.caName.replace(" ", "_")}_${audit.timestamp}"
                val docRef = firestore.collection("pumps").document(loggedInMobile).collection("saved_audits").document(docId)
                Tasks.await(docRef.delete())
                Log.d(TAG, "Deleted saved audit from Firestore: $docId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting saved audit from Firestore", e)
        }
    }

    private suspend fun syncMsNozzleReadings(db: AppDatabase, pumpMobileNumber: String) {
        val colRef = firestore.collection("pumps").document(pumpMobileNumber).collection("ms_nozzle_readings")
        val task = colRef.get()
        val snapshot = Tasks.await(task)

        val localReadings = db.msNozzleReadingDao().getAllMsNozzleReadings().first()

        // 1. Download cloud readings
        snapshot.documents.forEach { doc ->
            val nozzleLabel = doc.getString("nozzleLabel")
            val openingReading = doc.getDouble("openingReading") ?: 0.0
            val closingReading = doc.getDouble("closingReading") ?: 0.0
            val testing = doc.getDouble("testing") ?: 0.0
            val caName = doc.getString("caName") ?: ""
            val phone = doc.getString("phone") ?: ""
            val udhar = doc.getDouble("udhar") ?: 0.0
            val kharch = doc.getDouble("kharch") ?: 0.0
            val udhariJama = doc.getDouble("udhariJama") ?: 0.0
            val msSales = doc.getDouble("msSales") ?: 0.0
            val hsdSales = doc.getDouble("hsdSales") ?: 0.0
            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
            val date = doc.getString("date") ?: ""

            if (nozzleLabel != null) {
                val matchedLocal = localReadings.firstOrNull {
                    it.nozzleLabel == nozzleLabel && it.date == date && Math.abs(it.timestamp - timestamp) < 10000
                }
                if (matchedLocal == null) {
                    db.msNozzleReadingDao().insertMsNozzleReading(
                        MsNozzleReading(
                            nozzleLabel = nozzleLabel,
                            openingReading = openingReading,
                            closingReading = closingReading,
                            testing = testing,
                            caName = caName,
                            phone = phone,
                            udhar = udhar,
                            kharch = kharch,
                            udhariJama = udhariJama,
                            msSales = msSales,
                            hsdSales = hsdSales,
                            timestamp = timestamp,
                            date = date
                        )
                    )
                }
            }
        }

        // 2. Upload local readings
        val updatedLocal = db.msNozzleReadingDao().getAllMsNozzleReadings().first()
        for (r in updatedLocal) {
            val docId = "ms_${r.date.replace("-", "_")}_${r.nozzleLabel.replace(" ", "_")}_${r.timestamp}"
            val docRef = colRef.document(docId)
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
            Tasks.await(docRef.set(data, SetOptions.merge()))
        }
    }

    private suspend fun syncHsdNozzleReadings(db: AppDatabase, pumpMobileNumber: String) {
        val colRef = firestore.collection("pumps").document(pumpMobileNumber).collection("hsd_nozzle_readings")
        val task = colRef.get()
        val snapshot = Tasks.await(task)

        val localReadings = db.hsdNozzleReadingDao().getAllHsdNozzleReadings().first()

        // 1. Download cloud readings
        snapshot.documents.forEach { doc ->
            val nozzleLabel = doc.getString("nozzleLabel")
            val openingReading = doc.getDouble("openingReading") ?: 0.0
            val closingReading = doc.getDouble("closingReading") ?: 0.0
            val testing = doc.getDouble("testing") ?: 0.0
            val caName = doc.getString("caName") ?: ""
            val phone = doc.getString("phone") ?: ""
            val udhar = doc.getDouble("udhar") ?: 0.0
            val kharch = doc.getDouble("kharch") ?: 0.0
            val udhariJama = doc.getDouble("udhariJama") ?: 0.0
            val msSales = doc.getDouble("msSales") ?: 0.0
            val hsdSales = doc.getDouble("hsdSales") ?: 0.0
            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
            val date = doc.getString("date") ?: ""

            if (nozzleLabel != null) {
                val matchedLocal = localReadings.firstOrNull {
                    it.nozzleLabel == nozzleLabel && it.date == date && Math.abs(it.timestamp - timestamp) < 10000
                }
                if (matchedLocal == null) {
                    db.hsdNozzleReadingDao().insertHsdNozzleReading(
                        HsdNozzleReading(
                            nozzleLabel = nozzleLabel,
                            openingReading = openingReading,
                            closingReading = closingReading,
                            testing = testing,
                            caName = caName,
                            phone = phone,
                            udhar = udhar,
                            kharch = kharch,
                            udhariJama = udhariJama,
                            msSales = msSales,
                            hsdSales = hsdSales,
                            timestamp = timestamp,
                            date = date
                        )
                    )
                }
            }
        }

        // 2. Upload local readings
        val updatedLocal = db.hsdNozzleReadingDao().getAllHsdNozzleReadings().first()
        for (r in updatedLocal) {
            val docId = "hsd_${r.date.replace("-", "_")}_${r.nozzleLabel.replace(" ", "_")}_${r.timestamp}"
            val docRef = colRef.document(docId)
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
            Tasks.await(docRef.set(data, SetOptions.merge()))
        }
    }

    private suspend fun syncTtReceiptEntries(db: AppDatabase, pumpMobileNumber: String) {
        val colRef = firestore.collection("pumps").document(pumpMobileNumber).collection("tt_receipt_entries")
        val task = colRef.get()
        val snapshot = Tasks.await(task)

        val localEntries = db.ttReceiptEntryDao().getAllEntries()

        // 1. Download cloud entries
        snapshot.documents.forEach { doc ->
            val date = doc.getString("date")
            val invoiceNumber = doc.getString("invoiceNumber") ?: ""
            val ttNumber = doc.getString("ttNumber") ?: ""
            val invoiceAmount = doc.getDouble("invoiceAmount") ?: 0.0
            val msInvoiceAmount = doc.getDouble("msInvoiceAmount") ?: 0.0
            val hsdInvoiceAmount = doc.getDouble("hsdInvoiceAmount") ?: 0.0
            val msInvoiceDensity = doc.getDouble("msInvoiceDensity") ?: 0.0
            val hsdInvoiceDensity = doc.getDouble("hsdInvoiceDensity") ?: 0.0
            val msActualFuelDensity = doc.getDouble("msActualFuelDensity") ?: 0.0
            val hsdActualFuelDensity = doc.getDouble("hsdActualFuelDensity") ?: 0.0
            val msPreDecantationStock = doc.getDouble("msPreDecantationStock") ?: 0.0
            val hsdPreDecantationStock = doc.getDouble("hsdPreDecantationStock") ?: 0.0
            val msPostDecantationStock = doc.getDouble("msPostDecantationStock") ?: 0.0
            val hsdPostDecantationStock = doc.getDouble("hsdPostDecantationStock") ?: 0.0
            val msShortage = doc.getDouble("msShortage") ?: 0.0
            val hsdShortage = doc.getDouble("hsdShortage") ?: 0.0
            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

            if (date != null) {
                val matchedLocal = localEntries.firstOrNull {
                    it.invoiceNumber == invoiceNumber && it.ttNumber == ttNumber && it.date == date
                }
                if (matchedLocal == null) {
                    db.ttReceiptEntryDao().insertEntry(
                        TtReceiptEntry(
                            date = date,
                            invoiceNumber = invoiceNumber,
                            ttNumber = ttNumber,
                            invoiceAmount = invoiceAmount,
                            msInvoiceAmount = msInvoiceAmount,
                            hsdInvoiceAmount = hsdInvoiceAmount,
                            msInvoiceDensity = msInvoiceDensity,
                            hsdInvoiceDensity = hsdInvoiceDensity,
                            msActualFuelDensity = msActualFuelDensity,
                            hsdActualFuelDensity = hsdActualFuelDensity,
                            msPreDecantationStock = msPreDecantationStock,
                            hsdPreDecantationStock = hsdPreDecantationStock,
                            msPostDecantationStock = msPostDecantationStock,
                            hsdPostDecantationStock = hsdPostDecantationStock,
                            msShortage = msShortage,
                            hsdShortage = hsdShortage,
                            timestamp = timestamp
                        )
                    )
                }
            }
        }

        // 2. Upload local entries
        val updatedLocal = db.ttReceiptEntryDao().getAllEntries()
        for (entry in updatedLocal) {
            val docId = "tt_${entry.date.replace("-", "_")}_${entry.invoiceNumber.replace(" ", "_")}"
            val docRef = colRef.document(docId)
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
            Tasks.await(docRef.set(data, SetOptions.merge()))
        }
    }

    /**
     * Attempts to retrieve and download credentials from Firestore for registration/login if not present locally.
     * This allows multiple client devices to instantly authenticate if the pump admin has added them!
     */
    suspend fun checkAndDownloadLoginInfo(context: Context, mobileNumber: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            
            // Check if this is the main pump registration
            val pumpRef = firestore.collection("pumps").document(mobileNumber)
            val pumpSnap = Tasks.await(pumpRef.get())
            if (pumpSnap.exists()) {
                val pumpInfo = PumpInfo(
                    mobileNumber = mobileNumber,
                    pumpName = pumpSnap.getString("pumpName") ?: "Unnamed Pump",
                    numMsNozzles = pumpSnap.getLong("numMsNozzles")?.toInt() ?: 0,
                    msNozzleLabels = pumpSnap.getString("msNozzleLabels") ?: "",
                    numHsdNozzles = pumpSnap.getLong("numHsdNozzles")?.toInt() ?: 0,
                    hsdNozzleLabels = pumpSnap.getString("hsdNozzleLabels") ?: "",
                    numMsTanks = pumpSnap.getLong("numMsTanks")?.toInt() ?: 1,
                    numHsdTanks = pumpSnap.getLong("numHsdTanks")?.toInt() ?: 1,
                    updatedAt = pumpSnap.getLong("updatedAt") ?: System.currentTimeMillis()
                )
                db.pumpInfoDao().insertPumpInfo(pumpInfo)

                // Also check and download its login_info
                val loginRef = firestore.collection("pumps").document(mobileNumber).collection("login_info").document(mobileNumber)
                val loginSnap = Tasks.await(loginRef.get())
                if (loginSnap.exists()) {
                    val loginInfo = LoginInfo(
                        mobileNumber = mobileNumber,
                        username = loginSnap.getString("username") ?: mobileNumber,
                        passwordHash = loginSnap.getString("passwordHash") ?: "",
                        verifiedAt = loginSnap.getLong("verifiedAt") ?: System.currentTimeMillis()
                    )
                    db.loginInfoDao().insertLoginInfo(loginInfo)
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "checkAndDownloadLoginInfo failed", e)
            false
        }
    }
}
