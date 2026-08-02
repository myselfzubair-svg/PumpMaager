package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hsd_nozzle_readings")
data class HsdNozzleReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nozzleLabel: String,
    val openingReading: Double,
    val closingReading: Double,
    val testing: Double,
    val caName: String,
    val phone: String, // mobile/phone
    val udhar: Double,
    val kharch: Double,
    val udhariJama: Double,
    val msSales: Double,
    val hsdSales: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val date: String = ""
)
