package com.sanibonani.save.domain.utils

import java.math.BigDecimal
import java.math.RoundingMode

private const val MONEY_SCALE = 2
private val MONEY_ZERO: BigDecimal = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)

fun Double.toMoneyBigDecimal(): BigDecimal =
    if (!isFinite()) MONEY_ZERO else BigDecimal.valueOf(this).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

fun BigDecimal.toMoneyDouble(): Double = setScale(MONEY_SCALE, RoundingMode.HALF_UP).toDouble()

fun Double.roundMoneyToTwoDecimals(): Double = toMoneyBigDecimal().toMoneyDouble()

fun String.parseMoneyAmountOrNull(): BigDecimal? {
    val cleaned = trim()
    if (cleaned.isBlank() || !cleaned.matches(Regex("^\\d+(?:\\.\\d{1,2})?$"))) return null
    return runCatching { BigDecimal(cleaned).setScale(MONEY_SCALE, RoundingMode.HALF_UP) }.getOrNull()
}

fun Double.isPositiveMoneyAmount(): Boolean = isFinite() && this > 0.0

fun Double.isNonNegativeMoneyAmount(): Boolean = isFinite() && this >= 0.0

