package com.sanibonani.save.ui.utils

import com.sanibonani.save.domain.model.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private val zarFormatter = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

fun formatZAR(amount: Double): String = zarFormatter.format(amount)
fun formatZAR(amount: BigDecimal): String = zarFormatter.format(amount)
fun formatZAR(amount: Money): String = amount.format()

fun formatDecimal(value: Double): String = String.format("%.2f", value)
fun formatPct(value: Double): String = "${String.format("%.1f", value)}%"

fun formatZARShort(amount: Double): String {
    val absAmount = Math.abs(amount)
    return when {
        absAmount >= 1_000_000 -> "R${String.format("%.1fM", amount / 1_000_000)}"
        absAmount >= 1_000 -> "R${String.format("%.1fK", amount / 1_000)}"
        else -> formatZAR(amount)
    }
}

fun formatZARShort(amount: Money): String = amount.formatShort()
