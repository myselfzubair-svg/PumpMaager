package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "registered_nozzles")
@Serializable
data class RegisteredNozzle(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    @SerialName("mobile_number") val mobileNumber: String, // Maps to the Pump's mobileNumber
    @SerialName("nozzle_type") val nozzleType: String, // "MS", "HSD", "PREMIUM", "CNG", "ADBLUE", etc.
    val label: String, // e.g. "Petrol - Left (#1)"
    @SerialName("nozzle_name") val nozzleName: String = "", // custom name: "Petrol - Left"
    @SerialName("nozzle_number") val nozzleNumber: String = "", // "1", "2" etc
    @SerialName("nozzle_id") val nozzleId: String = "", // System-generated: "NOZ_TANK1_1"
    @SerialName("product_id") val productId: String = "", // Links to the product identity
    @SerialName("tank_name") val tankName: String = "", // Associated tank name
    @SerialName("tank_id") val tankId: String = "", // System-generated tank ID
    @SerialName("nozzle_index") val nozzleIndex: Int = 0, // 0-based index or order
    @SerialName("initial_reading") val initialReading: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true
)
