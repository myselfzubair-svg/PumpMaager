package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pump_info")
data class PumpInfo(
    @PrimaryKey val mobileNumber: String,
    val pumpName: String,
    val numMsNozzles: Int = 0,
    val msNozzleLabels: String, // Comma-separated list of labels
    val numHsdNozzles: Int = 0,
    val hsdNozzleLabels: String, // Comma-separated list of labels
    val numMsTanks: Int,
    val numHsdTanks: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
