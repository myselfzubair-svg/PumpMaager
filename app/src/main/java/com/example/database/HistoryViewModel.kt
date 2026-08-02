package com.example.database

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = SavedAuditRepository(database.savedAuditDao())

    val allAudits: StateFlow<List<SavedAudit>> = repository.allAudits
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveAudit(
        date: String,
        caName: String,
        meterNo: String,
        auditType: String,
        summaryText: String,
        htmlContent: String,
        cashSubmitted: Double = 0.0,
        actualCashCollected: Double = 0.0
    ) {
        viewModelScope.launch {
            repository.insert(
                SavedAudit(
                    date = date,
                    caName = caName,
                    meterNo = meterNo,
                    auditType = auditType,
                    summaryText = summaryText,
                    htmlContent = htmlContent,
                    cashSubmitted = cashSubmitted,
                    actualCashCollected = actualCashCollected
                )
            )
        }
    }

    fun deleteAudit(id: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val audit = repository.getById(id)
            if (audit != null) {
                FirestoreSyncManager.deleteSavedAuditFromFirestore(getApplication(), audit)
                database.msNozzleReadingDao().deleteMsNozzleReadingsByDateAndCa(audit.date, audit.caName)
                database.hsdNozzleReadingDao().deleteHsdNozzleReadingsByDateAndCa(audit.date, audit.caName)
            }
            repository.deleteById(id)
        }
    }

    fun updateAudit(audit: SavedAudit) {
        viewModelScope.launch {
            repository.insert(audit)
        }
    }
    
    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun insertMsNozzleReading(reading: MsNozzleReading) {
        viewModelScope.launch {
            database.msNozzleReadingDao().insertMsNozzleReading(reading)
        }
    }

    fun insertHsdNozzleReading(reading: HsdNozzleReading) {
        viewModelScope.launch {
            database.hsdNozzleReadingDao().insertHsdNozzleReading(reading)
        }
    }

    fun insertMsNozzleReadings(readings: List<MsNozzleReading>) {
        viewModelScope.launch {
            database.msNozzleReadingDao().insertMsNozzleReadings(readings)
        }
    }

    fun insertHsdNozzleReadings(readings: List<HsdNozzleReading>) {
        viewModelScope.launch {
            database.hsdNozzleReadingDao().insertHsdNozzleReadings(readings)
        }
    }

    fun getMsReadingsByPhone(phone: String) = database.msNozzleReadingDao().getMsNozzleReadingsByPhone(phone)
    fun getHsdReadingsByPhone(phone: String) = database.hsdNozzleReadingDao().getHsdNozzleReadingsByPhone(phone)
    fun getMsReadingsByDate(date: String) = database.msNozzleReadingDao().getMsNozzleReadingsByDate(date)
    fun getHsdReadingsByDate(date: String) = database.hsdNozzleReadingDao().getHsdNozzleReadingsByDate(date)

    val allMsNozzleReadings: kotlinx.coroutines.flow.Flow<List<MsNozzleReading>> = database.msNozzleReadingDao().getAllMsNozzleReadings()
    val allHsdNozzleReadings: kotlinx.coroutines.flow.Flow<List<HsdNozzleReading>> = database.hsdNozzleReadingDao().getAllHsdNozzleReadings()

    suspend fun getLatestMsReadingForNozzle(nozzleLabel: String, phone: String): MsNozzleReading? {
        return database.msNozzleReadingDao().getLatestReadingForNozzle(nozzleLabel, phone)
    }

    suspend fun getLatestHsdReadingForNozzle(nozzleLabel: String, phone: String): HsdNozzleReading? {
        return database.hsdNozzleReadingDao().getLatestReadingForNozzle(nozzleLabel, phone)
    }

    suspend fun getInitialReadingForNozzle(nozzleLabel: String, phone: String): Double {
        val resolvedMobile = database.loginInfoDao().getLoginInfoByUsername(phone)?.mobileNumber ?: phone
        val nozzle = database.registeredNozzleDao().getNozzlesByPumpMobile(resolvedMobile)
            .firstOrNull { it.label == nozzleLabel }
        return nozzle?.initialReading ?: 0.0
    }
}
