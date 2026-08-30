package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "pump_info")
@Serializable
data class PumpInfo(
    @PrimaryKey @SerialName("mobile_number") val mobileNumber: String,
    @SerialName("pump_name") val pumpName: String,
    @SerialName("num_ms_nozzles") val numMsNozzles: Int = 0,
    @SerialName("ms_nozzle_labels") val msNozzleLabels: String, // Comma-separated list of labels
    @SerialName("num_hsd_nozzles") val numHsdNozzles: Int = 0,
    @SerialName("hsd_nozzle_labels") val hsdNozzleLabels: String, // Comma-separated list of labels
    @SerialName("num_ms_tanks") val numMsTanks: Int,
    @SerialName("num_hsd_tanks") val numHsdTanks: Int,
    @SerialName("ms_tank_labels") val msTankLabels: String = "", // Comma-separated labels for MS tanks
    @SerialName("hsd_tank_labels") val hsdTankLabels: String = "", // Comma-separated labels for HSD tanks
    @SerialName("product_names") val productNames: String = "", // Comma-separated list of all products (e.g. "Petrol,Diesel,CNG")
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)
