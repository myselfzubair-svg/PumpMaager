package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "daily_pump_data")
@Serializable
data class DailyPumpData(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    @SerialName("admin_phone") val adminPhone: String,
    val date: String,
    @SerialName("product_name") val productName: String,
    @SerialName("product_id") val productId: String = "",
    val density: Double = 0.0,
    val rate: Double = 0.0,
    @SerialName("opening_stock") val openingStock: Double = 0.0,
    @SerialName("entered_by") val enteredBy: String,
    val timestamp: Long = System.currentTimeMillis()
)
