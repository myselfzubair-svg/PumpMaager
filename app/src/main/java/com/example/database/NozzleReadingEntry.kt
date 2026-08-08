package com.example.database

data class NozzleReadingEntry(
    val nozzleId: String,
    val nozzleName: String,
    val nozzleNumber: String,
    val nozzleType: String, // Product Name
    val tankName: String,
    val tankId: String,
    var openingReading: String = "",
    var closingReading: String = "",
    var rate: Double = 0.0
) {
    val salesQuantity: Double
        get() {
            val open = openingReading.toDoubleOrNull() ?: return 0.0
            val close = closingReading.toDoubleOrNull() ?: return 0.0
            return (close - open).coerceAtLeast(0.0)
        }

    val salesAmount: Double
        get() = salesQuantity * rate
}
