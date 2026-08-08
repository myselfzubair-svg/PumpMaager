package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tt_receipt_entries")
data class TtReceiptEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ownerAdminPhone: String, // Multi-tenancy isolation
    val date: String,
    val invoiceNumber: String,
    val ttNumber: String,
    val invoiceAmount: Double,
    val msInvoiceAmount: Double = 0.0,
    val hsdInvoiceAmount: Double = 0.0,
    val msInvoiceDensity: Double,
    val hsdInvoiceDensity: Double,
    val msActualFuelDensity: Double,
    val hsdActualFuelDensity: Double,
    val msPreDecantationStock: Double,
    val hsdPreDecantationStock: Double,
    val msPostDecantationStock: Double,
    val hsdPostDecantationStock: Double,
    val msShortage: Double,
    val hsdShortage: Double,
    val timestamp: Long = System.currentTimeMillis()
)
