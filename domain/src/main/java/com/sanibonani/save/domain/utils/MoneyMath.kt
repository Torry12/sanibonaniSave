package com.sanibonani.save.domain.utils

import java.math.BigDecimal
import java.math.RoundingMode
import com.sanibonani.save.domain.utils.safeRunCatching

private const val MONEY_SCALE = 2

fun String.parseMoneyAmountOrNull(): BigDecimal? {
    val cleaned = trim()
    if (cleaned.isBlank() || !cleaned.matches(Regex("^\\d+(?:\\.\\d{1,2})?$"))) return null
    return safeRunCatching { BigDecimal(cleaned).setScale(MONEY_SCALE, RoundingMode.HALF_UP) }.getOrNull()
}
