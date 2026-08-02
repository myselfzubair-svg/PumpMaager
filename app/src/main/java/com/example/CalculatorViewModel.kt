package com.example

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CalculationGroup(
    val id: Int,
    val label: String,
    val valueA: String = "",
    val valueB: String = ""
) {
    val result: Double
        get() {
            val a = valueA.toDoubleOrNull() ?: 0.0
            val b = valueB.toDoubleOrNull() ?: 0.0
            return a - b
        }
}

class CalculatorViewModel : ViewModel() {
    private val _groups = MutableStateFlow(
        listOf(
            CalculationGroup(1, "Group 1"),
            CalculationGroup(2, "Group 2"),
            CalculationGroup(3, "Group 3"),
            CalculationGroup(4, "Group 4")
        )
    )
    val groups: StateFlow<List<CalculationGroup>> = _groups.asStateFlow()

    fun updateValueA(groupId: Int, newValue: String) {
        val sanitized = sanitizeNumericInput(newValue)
        _groups.update { list ->
            list.map { group ->
                if (group.id == groupId) group.copy(valueA = sanitized) else group
            }
        }
    }

    fun updateValueB(groupId: Int, newValue: String) {
        val sanitized = sanitizeNumericInput(newValue)
        _groups.update { list ->
            list.map { group ->
                if (group.id == groupId) group.copy(valueB = sanitized) else group
            }
        }
    }

    fun updateLabel(groupId: Int, newLabel: String) {
        _groups.update { list ->
            list.map { group ->
                if (group.id == groupId) group.copy(label = newLabel) else group
            }
        }
    }

    fun clearAll() {
        _groups.update { list ->
            list.map { group ->
                group.copy(valueA = "", valueB = "")
            }
        }
    }

    fun getSummaryText(): String {
        val currentGroups = _groups.value

        val results = currentGroups.map { it.result }
        val grandTotal = results.sum()
        val finalResult = grandTotal

        val sb = StringBuilder()
        sb.append("--- FIELDS CALCULATOR REPORT ---\n\n")
        currentGroups.forEach { g ->
            val vA = g.valueA.ifEmpty { "0" }
            val vB = g.valueB.ifEmpty { "0" }
            sb.append("${g.label}: $vA - $vB = ${formatDouble(g.result)}\n")
        }
        sb.append("\n--------------------------------\n")
        
        val breakdown = results.joinToString(" + ") { formatDouble(it) }
        sb.append("Grand Total: $breakdown = ${formatDouble(grandTotal)}\n")
        sb.append("--------------------------------\n")
        sb.append("Final Result = ${formatDouble(finalResult)}\n")
        return sb.toString()
    }

    private fun sanitizeNumericInput(input: String): String {
        // Allow optional leading or single minus sign, digits, and a single decimal point
        val hasMinus = input.startsWith("-")
        val strippedMinus = if (hasMinus) input.substring(1) else input
        
        var seenDot = false
        val s = strippedMinus.filter { char ->
            if (char == '.') {
                if (seenDot) {
                    false
                } else {
                    seenDot = true
                    true
                }
            } else {
                char.isDigit()
            }
        }
        
        return if (hasMinus) "-$s" else s
    }
}

fun formatDouble(value: Double): String {
    return when {
        value.isNaN() -> "0"
        value.isInfinite() -> if (value > 0) "∞" else "-∞"
        else -> {
            val formatted = "%,.4f".format(value).trimEnd('0').trimEnd('.')
            if (formatted.isEmpty() || formatted == "-0") "0" else formatted
        }
    }
}
