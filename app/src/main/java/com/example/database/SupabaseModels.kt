package com.example.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseUser(
    val id: String? = null,
    @SerialName("mobile_number") val mobileNumber: String,
    val username: String? = null,
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("pump_name") val pumpName: String? = null,
    val role: String = "STAFF",
    @SerialName("password_hash") val passwordHash: String? = null,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SupabaseMembership(
    val id: Long? = null,
    @SerialName("staff_phone") val staffPhone: String,
    @SerialName("admin_phone") val adminPhone: String,
    @SerialName("pump_name") val pumpName: String? = null,
    val username: String? = null,
    val role: String? = null,
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class CreditEntry(
    val id: Long? = null,
    @SerialName("report_id") val reportId: String,
    @SerialName("owner_admin_phone") val ownerAdminPhone: String,
    val date: String,
    val amount: Double,
    @SerialName("ca_name") val caName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val party: String,
    val description: String
)

@Serializable
data class ExpenseEntry(
    val id: Long? = null,
    @SerialName("report_id") val reportId: String,
    @SerialName("owner_admin_phone") val ownerAdminPhone: String,
    val date: String,
    val amount: Double,
    @SerialName("ca_name") val caName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String,
    val description: String
)

@Serializable
data class RecoveryEntry(
    val id: Long? = null,
    @SerialName("report_id") val reportId: String,
    @SerialName("owner_admin_phone") val ownerAdminPhone: String,
    val date: String,
    val amount: Double,
    @SerialName("ca_name") val caName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val party: String,
    val description: String
)

@Serializable
data class NozzleReading(
    val id: Long? = null,
    @SerialName("report_id") val reportId: String,
    @SerialName("owner_admin_phone") val ownerAdminPhone: String,
    @SerialName("product_name") val productName: String = "",
    val date: String,
    @SerialName("ca_name") val caName: String,
    val timestamp: Long = System.currentTimeMillis(),
    @SerialName("nozzle_label") val nozzleLabel: String,
    val opening: Double,
    val closing: Double,
    val testing: Double,
    @SerialName("net_sales") val netSales: Double
)
