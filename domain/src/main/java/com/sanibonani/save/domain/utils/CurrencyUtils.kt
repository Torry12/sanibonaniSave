package com.sanibonani.save.domain.utils

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private val zarFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
    currency = Currency.getInstance("ZAR")
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

fun formatZAR(amount: Double): String =
    if (!amount.isFinite()) zarFormatter.format(BigDecimal.ZERO)
    else zarFormatter.format(amount.toMoneyBigDecimal())

fun formatZAR(amount: BigDecimal): String = zarFormatter.format(amount.setScale(2, java.math.RoundingMode.HALF_UP))
