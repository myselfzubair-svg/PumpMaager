package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "registered_nozzles")
data class RegisteredNozzle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mobileNumber: String, // Maps to the Pump's mobileNumber
    val nozzleType: String, // "MS", "HSD", "PREMIUM", "CNG", "ADBLUE", etc.
    val label: String, // e.g. "Petrol - Left (#1)"
    val nozzleName: String = "", // custom name: "Petrol - Left"
    val nozzleNumber: String = "", // "1", "2" etc
    val nozzleId: String = "", // System-generated: "NOZ_TANK1_1"
    val productId: String = "", // Links to the product identity
    val tankName: String = "", // Associated tank name
    val tankId: String = "", // System-generated tank ID
    val nozzleIndex: Int, // 0-based index or order
    val initialReading: Double = 0.0,
    val isActive: Boolean = true
)
