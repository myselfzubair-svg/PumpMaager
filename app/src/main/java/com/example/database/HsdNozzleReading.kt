package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "hsd_nozzle_readings")
@Serializable
data class HsdNozzleReading(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    @SerialName("owner_admin_phone") val ownerAdminPhone: String, // Multi-tenancy isolation
    @SerialName("nozzle_label") val nozzleLabel: String,
    @SerialName("opening_reading") val openingReading: Double = 0.0,
    @SerialName("closing_reading") val closingReading: Double = 0.0,
    val testing: Double = 0.0,
    @SerialName("ca_name") val caName: String,
    val phone: String, // mobile/phone
    val udhar: Double = 0.0,
    val kharch: Double = 0.0,
    @SerialName("udhari_jama") val udhariJama: Double = 0.0,
    @SerialName("ms_sales") val msSales: Double = 0.0,
    @SerialName("hsd_sales") val hsdSales: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val date: String = "",
    @SerialName("report_id") val reportId: String = ""
)
