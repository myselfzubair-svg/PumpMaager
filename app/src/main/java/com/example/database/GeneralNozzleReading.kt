package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "general_nozzle_readings")
data class GeneralNozzleReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerAdminPhone: String,
    val productName: String,
    val nozzleLabel: String,
    val openingReading: Double,
    val closingReading: Double,
    val testing: Double,
    val caName: String,
    val phone: String,
    val udhar: Double,
    val kharch: Double,
    val udhariJama: Double,
    val productSalesAmount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val date: String = ""
)
