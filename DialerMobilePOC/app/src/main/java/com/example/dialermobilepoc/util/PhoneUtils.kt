package com.example.dialermobilepoc.util

fun normalizePhone(input: String): String {
    val trimmed = input.trim().replace(" ", "").replace("-", "")

    return when {
        trimmed.startsWith("+") -> trimmed
        trimmed.length == 10 && trimmed.all { it.isDigit() } -> "+91$trimmed"
        trimmed.startsWith("91") && trimmed.length == 12 && trimmed.all { it.isDigit() } -> "+$trimmed"
        trimmed.startsWith("0") && trimmed.length == 11 && trimmed.all { it.isDigit() } -> "+91${trimmed.substring(1)}"
        else -> trimmed
    }
}