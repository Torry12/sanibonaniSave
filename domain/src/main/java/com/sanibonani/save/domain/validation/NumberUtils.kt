package com.sanibonani.save.domain.validation

/**
 * Utility extensions for safe number parsing across the application.
 * Replaces repetitive toDoubleOrNull() ?: fallback patterns and prevents formatting crashes.
 */

fun String?.safeToDouble(fallback: Double = 0.0): Double {
    if (this.isNullOrBlank()) return fallback
    return this.trim().replace(",", ".").toDoubleOrNull() ?: fallback
}

fun String?.safeToInt(fallback: Int = 0): Int {
    if (this.isNullOrBlank()) return fallback
    return this.trim().toIntOrNull() ?: fallback
}

/**
 * For Jetpack Compose text fields: allows valid decimal inputs while typing.
 * E.g., allows "12." before it becomes "12.5"
 */
fun String.isValidDecimalInput(): Boolean {
    return this.isEmpty() || this.toDoubleOrNull() != null || this.endsWith(".")
}
