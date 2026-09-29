package com.example.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ManagerTransaction(
    val id: Long? = null,
    @SerialName("owner_admin_phone") val ownerAdminPhone: String,
    val date: String,
    val amount: Double,
    val type: String, // 'BANK_DEPOSIT', 'MANAGER_EXPENSE', 'SALON_EXPENSE', 'MANAGER_UDHAR', 'MANAGER_JAMA', 'LOAN_BORROWED', 'OWNER_BORROWED', 'OWNER_WITHDRAWAL'
    val description: String? = null,
    @SerialName("report_id") val reportId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
