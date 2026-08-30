package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "tt_receipt_entries")
@Serializable
data class TtReceiptEntry(
    @PrimaryKey(autoGenerate = true) val id: Int? = null,
    @SerialName("owner_admin_phone") val ownerAdminPhone: String, // Multi-tenancy isolation
    val date: String,
    @SerialName("invoice_number") val invoiceNumber: String,
    @SerialName("tt_number") val ttNumber: String,
    @SerialName("invoice_amount") val invoiceAmount: Double = 0.0,
    @SerialName("ms_invoice_amount") val msInvoiceAmount: Double = 0.0,
    @SerialName("hsd_invoice_amount") val hsdInvoiceAmount: Double = 0.0,
    @SerialName("ms_invoice_density") val msInvoiceDensity: Double = 0.0,
    @SerialName("hsd_invoice_density") val hsdInvoiceDensity: Double = 0.0,
    @SerialName("ms_actual_fuel_density") val msActualFuelDensity: Double = 0.0,
    @SerialName("hsd_actual_fuel_density") val hsdActualFuelDensity: Double = 0.0,
    @SerialName("ms_pre_decantation_stock") val msPreDecantationStock: Double = 0.0,
    @SerialName("hsd_pre_decantation_stock") val hsdPreDecantationStock: Double = 0.0,
    @SerialName("ms_post_decantation_stock") val msPostDecantationStock: Double = 0.0,
    @SerialName("hsd_post_decantation_stock") val hsdPostDecantationStock: Double = 0.0,
    @SerialName("ms_shortage") val msShortage: Double = 0.0,
    @SerialName("hsd_shortage") val hsdShortage: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)
