package com.example.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AuditLineItemType {
    CREDIT,   // Udhar
    EXPENSE,  // Kharch
    RECOVERY  // Jama
}

@Serializable
data class AuditLineItem(
    val id: Long = 0,
    @SerialName("report_id") val reportId: String,
    @SerialName("owner_admin_phone") val ownerAdminPhone: String,
    val type: AuditLineItemType,
    val category: String, // Party name for Credit/Recovery, Category for Expense
    val description: String,
    val amount: Double,
    val date: String,
    val timestamp: Long = System.currentTimeMillis(),
    @SerialName("ca_name") val caName: String
)
