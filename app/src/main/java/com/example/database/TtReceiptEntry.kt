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

    @SerialName("ms_invoice_quantity") val msInvoiceQuantity: Double = 0.0,
    @SerialName("hsd_invoice_quantity") val hsdInvoiceQuantity: Double = 0.0,
    
    @SerialName("extra_product_name") val extraProductName: String? = null,
    @SerialName("extra_invoice_quantity") val extraInvoiceQuantity: Double = 0.0,
    @SerialName("extra_invoice_amount") val extraInvoiceAmount: Double = 0.0,
    @SerialName("extra_invoice_density") val extraInvoiceDensity: Double = 0.0,
    @SerialName("extra_actual_density") val extraActualDensity: Double = 0.0,
    @SerialName("extra_pre_decantation_stock") val extraPreDecantationStock: Double = 0.0,
    @SerialName("extra_post_decantation_stock") val extraPostDecantationStock: Double = 0.0,
    @SerialName("extra_shortage") val extraShortage: Double = 0.0,

    @SerialName("ms_density_diff") val msDensityDiff: Double = 0.0,
    @SerialName("hsd_density_diff") val hsdDensityDiff: Double = 0.0,
    @SerialName("extra_density_diff") val extraDensityDiff: Double = 0.0,

    val timestamp: Long = System.currentTimeMillis()
)
