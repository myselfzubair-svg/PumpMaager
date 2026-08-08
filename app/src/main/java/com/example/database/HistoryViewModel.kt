package com.example.database

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val _adminPhone = MutableStateFlow("")
    val adminPhone: StateFlow<String> = _adminPhone.asStateFlow()

    fun setAdminPhone(phone: String) {
        _adminPhone.value = phone
    }

    val allAudits: StateFlow<List<SavedAudit>> = _adminPhone
        .flatMapLatest { phone ->
            if (phone.isBlank()) flowOf(emptyList())
            else FirestoreRepository.getAuditsFlow(phone)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveAudit(
        adminPhone: String,
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
            FirestoreRepository.saveAudit(
                SavedAudit(
                    ownerAdminPhone = adminPhone,
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

    fun deleteAudit(adminPhone: String, date: String, caName: String, timestamp: Long) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            FirestoreRepository.deleteAudit(adminPhone, date, caName, timestamp)
        }
    }

    fun insertMsNozzleReadings(readings: List<MsNozzleReading>) {
        viewModelScope.launch {
            if (readings.isNotEmpty()) {
                FirestoreRepository.saveMsNozzleReadings(readings.first().ownerAdminPhone, readings)
            }
        }
    }

    fun insertHsdNozzleReadings(readings: List<HsdNozzleReading>) {
        viewModelScope.launch {
            if (readings.isNotEmpty()) {
                FirestoreRepository.saveHsdNozzleReadings(readings.first().ownerAdminPhone, readings)
            }
        }
    }

    val allMsNozzleReadings: StateFlow<List<MsNozzleReading>> = _adminPhone.flatMapLatest { phone ->
        if (phone.isBlank()) flowOf(emptyList())
        else FirestoreRepository.getMsNozzleReadingsFlow(phone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHsdNozzleReadings: StateFlow<List<HsdNozzleReading>> = _adminPhone.flatMapLatest { phone ->
        if (phone.isBlank()) flowOf(emptyList())
        else FirestoreRepository.getHsdNozzleReadingsFlow(phone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGeneralNozzleReadings: StateFlow<List<GeneralNozzleReading>> = _adminPhone.flatMapLatest { phone ->
        if (phone.isBlank()) flowOf(emptyList())
        else FirestoreRepository.getGeneralNozzleReadingsFlow(phone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getGeneralReadingsByDate(date: String, adminPhone: String): Flow<List<GeneralNozzleReading>> {
        return allGeneralNozzleReadings.map { list ->
            list.filter { it.date == date }
        }
    }

    fun insertGeneralNozzleReadings(readings: List<GeneralNozzleReading>) {
        viewModelScope.launch {
            if (readings.isNotEmpty()) {
                FirestoreRepository.saveGeneralNozzleReadings(readings.first().ownerAdminPhone, readings)
            }
        }
    }

    fun getMsReadingsByDate(date: String, adminPhone: String): Flow<List<MsNozzleReading>> {
        return allMsNozzleReadings.map { list ->
            list.filter { it.date == date }
        }
    }

    fun getHsdReadingsByDate(date: String, adminPhone: String): Flow<List<HsdNozzleReading>> {
        return allHsdNozzleReadings.map { list ->
            list.filter { it.date == date }
        }
    }

    fun insertMsNozzleReading(reading: MsNozzleReading) {
        viewModelScope.launch {
            FirestoreRepository.saveMsNozzleReadings(reading.ownerAdminPhone, listOf(reading))
        }
    }

    fun insertHsdNozzleReading(reading: HsdNozzleReading) {
        viewModelScope.launch {
            FirestoreRepository.saveHsdNozzleReadings(reading.ownerAdminPhone, listOf(reading))
        }
    }

    fun updateAudit(audit: SavedAudit) {
        viewModelScope.launch {
            FirestoreRepository.saveAudit(audit)
        }
    }

    fun clearAll(adminPhone: String) {
        // Firestore doesn't support clearing a collection easily.
        // For now, we clear the local Room DB if needed, but since we are firestore-centric,
        // we might want to implement a batch delete. 
        // For simplicity, we just log this or implement a basic version.
    }

    suspend fun getLatestMsReadingForNozzle(nozzleLabel: String, phone: String, adminPhone: String): MsNozzleReading? {
        return allMsNozzleReadings.first().firstOrNull { it.nozzleLabel == nozzleLabel }
    }

    suspend fun getLatestHsdReadingForNozzle(nozzleLabel: String, phone: String, adminPhone: String): HsdNozzleReading? {
        return allHsdNozzleReadings.first().firstOrNull { it.nozzleLabel == nozzleLabel }
    }

    suspend fun getInitialReadingForNozzle(nozzleLabel: String, adminPhone: String): Double {
        if (adminPhone.isBlank()) return 0.0
        val nozzle = FirestoreRepository.getRegisteredNozzles(adminPhone)
            .firstOrNull { it.nozzleName == nozzleLabel || it.label == nozzleLabel }
        return nozzle?.initialReading ?: 0.0
    }
}
