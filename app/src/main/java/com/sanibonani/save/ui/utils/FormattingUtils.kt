package com.sanibonani.save.ui.utils

import com.sanibonani.save.domain.model.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

private val zarFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-ZA")).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

fun formatZAR(amount: BigDecimal): String = zarFormatter.format(amount)
fun formatZAR(amount: Money): String = amount.format()

fun formatPct(value: Double): String = "${String.format(Locale.US, "%.1f", value)}%"

fun formatZARShort(amount: Money): String = amount.formatShort()

private val fullDateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH)
private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/**
 * Parses an ISO-8601 local datetime, tolerating a trailing "Z" or UTC offset.
 * Returns null when the value cannot be parsed.
 */
private fun parseIsoLocalDateTime(isoString: String): LocalDateTime? =
    try {
        LocalDateTime.parse(isoString.substringBefore("Z").substringBefore("+"))
    } catch (e: DateTimeParseException) {
        null
    }

fun formatDate(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "N/A"
    return parseIsoLocalDateTime(isoString)?.format(fullDateFormatter) ?: isoString
}

fun formatMonthYear(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "Unknown Date"
    return parseIsoLocalDateTime(isoString)?.format(monthYearFormatter) ?: "Unknown Date"
}
