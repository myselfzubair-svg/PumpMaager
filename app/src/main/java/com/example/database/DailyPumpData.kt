package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_pump_data")
data class DailyPumpData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val adminPhone: String,
    val date: String,
    val productName: String,
    val productId: String = "",
    val density: Double,
    val rate: Double,
    val openingStock: Double,
    val enteredBy: String,
    val timestamp: Long = System.currentTimeMillis()
)
