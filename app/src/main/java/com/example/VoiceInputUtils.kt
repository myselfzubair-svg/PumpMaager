package com.example

import java.util.Locale

object VoiceInputUtils {
    fun parseSpokenNumber(spokenText: String): String {
        var text = spokenText.lowercase().trim()
        
        // Simple basic mappings
        val englishWords = mapOf(
            "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
            "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
            "point" to ".", "dot" to ".", "decimal" to "."
        )
        
        // Let's replace simple Indian numbering words too for better local support
        val hindiWords = mapOf(
            "ek" to "1", "do" to "2", "teen" to "3", "chaar" to "4", "paanch" to "5",
            "chhey" to "6", "saat" to "7", "aath" to "8", "nau" to "9", "shunya" to "0"
        )

        // Replace english words
        for ((word, digit) in englishWords) {
            text = text.replace(word, digit)
        }
        
        // Replace hindi words
        for ((word, digit) in hindiWords) {
            text = text.replace(word, digit)
        }

        // Remove commas, spaces
        text = text.replace(",", "")
        text = text.replace("\\s+".toRegex(), "")

        // Filter characters to keep only digits and decimal points
        val filteredStr = text.filter { it.isDigit() || it == '.' }

        // Enforce maximum of one decimal point
        val parts = filteredStr.split(".")
        return if (parts.size > 2) {
            parts[0] + "." + parts.drop(1).joinToString("")
        } else {
            filteredStr
        }
    }
}
