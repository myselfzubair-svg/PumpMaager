package com.example.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SavedAudit(
    val id: Int? = null,
    @SerialName("owner_admin_phone") val ownerAdminPhone: String, // Multi-tenancy isolation
    val date: String,
    @SerialName("ca_name") val caName: String,
    @SerialName("meter_no") val meterNo: String,
    @SerialName("audit_type") val auditType: String, // "2 Nozzles", "4 Nozzles", "Full Day"
    @SerialName("summary_text") val summaryText: String,
    @SerialName("html_content") val htmlContent: String,
    val timestamp: Long = System.currentTimeMillis(),
    @SerialName("cash_submitted") val cashSubmitted: Double = 0.0,
    @SerialName("actual_cash_collected") val actualCashCollected: Double = 0.0,
    @SerialName("report_id") val reportId: String = "",
    
    // Structured Financial Data
    @SerialName("total_fuel_sales_amount") val totalFuelSalesAmount: Double = 0.0,
    @SerialName("total_udhari_jama") val totalUdhariJama: Double = 0.0,
    @SerialName("total_kharch") val totalKharch: Double = 0.0,
    @SerialName("total_udhar") val totalUdhar: Double = 0.0,
    @SerialName("phone_pe_amount") val phonePeAmount: Double = 0.0,
    @SerialName("cards_amount") val cardsAmount: Double = 0.0,
    @SerialName("expected_cash_balance") val expectedCashBalance: Double = 0.0,
    @SerialName("tally_difference") val tallyDifference: Double = 0.0
)
