package com.example.database

import android.app.Application
import android.util.Log
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
            else SupabaseRepository.getAuditsFlow(phone)
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
        actualCashCollected: Double = 0.0,
        customTimestamp: Long? = null,
        reportId: String = "",
        totalFuelSalesAmount: Double = 0.0,
        totalUdhariJama: Double = 0.0,
        totalKharch: Double = 0.0,
        totalUdhar: Double = 0.0,
        phonePeAmount: Double = 0.0,
        cardsAmount: Double = 0.0,
        expectedCashBalance: Double = 0.0,
        tallyDifference: Double = 0.0
    ) {
        viewModelScope.launch {
            SupabaseRepository.saveAudit(
                SavedAudit(
                    ownerAdminPhone = adminPhone,
                    date = date,
                    caName = caName,
                    meterNo = meterNo,
                    auditType = auditType,
                    summaryText = summaryText,
                    htmlContent = htmlContent,
                    timestamp = customTimestamp ?: System.currentTimeMillis(),
                    cashSubmitted = cashSubmitted,
                    actualCashCollected = actualCashCollected,
                    reportId = reportId,
                    totalFuelSalesAmount = totalFuelSalesAmount,
                    totalUdhariJama = totalUdhariJama,
                    totalKharch = totalKharch,
                    totalUdhar = totalUdhar,
                    phonePeAmount = phonePeAmount,
                    cardsAmount = cardsAmount,
                    expectedCashBalance = expectedCashBalance,
                    tallyDifference = tallyDifference
                )
            )
        }
    }

    fun deleteAudit(adminPhone: String, date: String, caName: String, timestamp: Long, reportId: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            SupabaseRepository.deleteAudit(adminPhone, date, caName, timestamp, reportId)
        }
    }

    // Specialized Readings (Aggregated from product-wise tables)
    val allGeneralNozzleReadings: StateFlow<List<GeneralNozzleReading>> = _adminPhone.flatMapLatest { phone ->
        if (phone.isBlank()) flowOf(emptyList())
        else SupabaseRepository.getNozzleReadingsFlow(phone).map { readings ->
            Log.d("HistoryViewModel", "Flow emitted ${readings.size} readings for $phone")
            readings.map { r ->
                GeneralNozzleReading(
                    id = r.id,
                    ownerAdminPhone = r.ownerAdminPhone,
                    productName = r.productName,
                    nozzleLabel = r.nozzleLabel,
                    openingReading = r.opening,
                    closingReading = r.closing,
                    testing = r.testing,
                    caName = r.caName,
                    phone = "",
                    udhar = 0.0,
                    kharch = 0.0,
                    udhariJama = 0.0,
                    productSalesAmount = r.netSales,
                    timestamp = r.timestamp,
                    date = r.date,
                    reportId = r.reportId
                )
            }
        }.onStart { Log.d("HistoryViewModel", "Starting nozzle readings flow for $phone") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Financials
    val allCreditEntries: StateFlow<List<CreditEntry>> = _adminPhone.flatMapLatest { phone ->
        if (phone.isBlank()) flowOf(emptyList())
        else SupabaseRepository.getCreditEntriesFlow(phone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExpenseEntries: StateFlow<List<ExpenseEntry>> = _adminPhone.flatMapLatest { phone ->
        if (phone.isBlank()) flowOf(emptyList())
        else SupabaseRepository.getExpenseEntriesFlow(phone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecoveryEntries: StateFlow<List<RecoveryEntry>> = _adminPhone.flatMapLatest { phone ->
        if (phone.isBlank()) flowOf(emptyList())
        else SupabaseRepository.getRecoveryEntriesFlow(phone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAuditLineItems: StateFlow<List<AuditLineItem>> = combine(
        allCreditEntries, allExpenseEntries, allRecoveryEntries
    ) { cEntries, eEntries, rEntries ->
        cEntries.map { it: CreditEntry -> AuditLineItem(it.id ?: 0, it.reportId, it.ownerAdminPhone, AuditLineItemType.CREDIT, it.party, it.description, it.amount, it.date, it.timestamp, it.caName) } +
        eEntries.map { it: ExpenseEntry -> AuditLineItem(it.id ?: 0, it.reportId, it.ownerAdminPhone, AuditLineItemType.EXPENSE, it.category, it.description, it.amount, it.date, it.timestamp, it.caName) } +
        rEntries.map { it: RecoveryEntry -> AuditLineItem(it.id ?: 0, it.reportId, it.ownerAdminPhone, AuditLineItemType.RECOVERY, it.party, it.description, it.amount, it.date, it.timestamp, it.caName) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertGeneralNozzleReadings(readings: List<GeneralNozzleReading>) {
        // This method is now legacy as we save per nozzle, but we can keep the signature if needed for other UI.
        // Actually, the new logic in CalculatorScreen calls saveNozzleReading directly.
    }

    fun insertCreditEntries(entries: List<CreditEntry>) = viewModelScope.launch { SupabaseRepository.saveCreditEntries(entries) }
    fun insertExpenseEntries(entries: List<ExpenseEntry>) = viewModelScope.launch { SupabaseRepository.saveExpenseEntries(entries) }
    fun insertRecoveryEntries(entries: List<RecoveryEntry>) = viewModelScope.launch { SupabaseRepository.saveRecoveryEntries(entries) }

    fun updateAudit(audit: SavedAudit) {
        viewModelScope.launch {
            SupabaseRepository.saveAudit(audit)
        }
    }

    fun clearAll(adminPhone: String) {
        // Not supported in this architecture
    }

    suspend fun getInitialReadingForNozzle(nozzleLabel: String, adminPhone: String): Double {
        if (adminPhone.isBlank()) return 0.0
        val nozzle = SupabaseRepository.getRegisteredNozzles(adminPhone)
            .firstOrNull { it.nozzleName == nozzleLabel || it.label == nozzleLabel }
        return nozzle?.initialReading ?: 0.0
    }

    suspend fun getLatestClosingReadingForNozzle(nozzleLabel: String, adminPhone: String, date: String): Double {
        if (adminPhone.isBlank()) return 0.0

        // Search through combined general nozzle readings (which are now derived from all nozzle tables)
        val todayReading = allGeneralNozzleReadings.value.filter { it.date == date && it.nozzleLabel == nozzleLabel }.maxByOrNull { it.timestamp }
        if (todayReading != null) return todayReading.closingReading

        val histReading = allGeneralNozzleReadings.value.filter { it.nozzleLabel == nozzleLabel }.maxByOrNull { it.timestamp }
        if (histReading != null) return histReading.closingReading

        // 3. Fallback to initialReading from registration
        return getInitialReadingForNozzle(nozzleLabel, adminPhone)
    }

    // Compatibility properties (empty) to avoid breaking other screens immediately
    val allMsNozzleReadings: StateFlow<List<MsNozzleReading>> = flowOf(emptyList<MsNozzleReading>()).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allHsdNozzleReadings: StateFlow<List<HsdNozzleReading>> = flowOf(emptyList<HsdNozzleReading>()).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
