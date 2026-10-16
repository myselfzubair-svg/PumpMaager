package com.example.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DailyCashRecord(
    val id: Long? = null,
    @SerialName("admin_phone") val adminPhone: String,
    val date: String,
    @SerialName("total_cash") val totalCash: Double,
    val timestamp: Long = System.currentTimeMillis(),
    @SerialName("cash_submitted") val cashSubmitted: Double = 0.0,
    @SerialName("cash_balance") val cashBalance: Double = 0.0
)
