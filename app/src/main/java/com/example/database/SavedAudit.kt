package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_audits")
data class SavedAudit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val caName: String,
    val meterNo: String,
    val auditType: String, // "2 Nozzles", "4 Nozzles", "Full Day"
    val summaryText: String,
    val htmlContent: String,
    val timestamp: Long = System.currentTimeMillis(),
    val cashSubmitted: Double = 0.0,
    val actualCashCollected: Double = 0.0
)
