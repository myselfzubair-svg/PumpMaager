package com.example.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeneralNozzleReading(
    val id: Long? = null,
    @SerialName("owner_admin_phone") val ownerAdminPhone: String,
    @SerialName("product_name") val productName: String,
    @SerialName("nozzle_label") val nozzleLabel: String,
    @SerialName("opening_reading") val openingReading: Double = 0.0,
    @SerialName("closing_reading") val closingReading: Double = 0.0,
    val testing: Double = 0.0,
    @SerialName("ca_name") val caName: String,
    val phone: String,
    val udhar: Double = 0.0,
    val kharch: Double = 0.0,
    @SerialName("udhari_jama") val udhariJama: Double = 0.0,
    @SerialName("product_sales_amount") val productSalesAmount: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val date: String = "",
    @SerialName("report_id") val reportId: String = ""
)
