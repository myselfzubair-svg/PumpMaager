package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "registered_nozzles")
data class RegisteredNozzle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mobileNumber: String, // Maps to the Pump's mobileNumber
    val nozzleType: String, // "MS" or "HSD"
    val label: String, // e.g. "MS Nozzle 1"
    val nozzleIndex: Int, // 0-based index or order
    val initialReading: Double = 0.0
)
