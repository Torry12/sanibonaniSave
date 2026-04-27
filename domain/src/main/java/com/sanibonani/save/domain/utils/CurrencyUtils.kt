package com.sanibonani.save.domain.utils

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private val zarFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
    currency = Currency.getInstance("ZAR")
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

fun formatZAR(amount: Double): String = zarFormatter.format(amount)
