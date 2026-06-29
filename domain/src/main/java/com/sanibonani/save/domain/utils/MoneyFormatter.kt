package com.sanibonani.save.domain.utils

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Monetary value formatter ensuring all currency displays use 2 decimal places.
 * This is the single source of truth for all monetary formatting in the app.
 */
object MoneyFormatter {

    // South African Rand formatter with 2 decimal places
    @Suppress("DEPRECATION")  // Java deprecation, not an issue in practice
    private val zarFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
        currency = Currency.getInstance("ZAR")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    // Plain decimal formatter with 2 decimal places (no currency symbol)
    private val plainFormatter: DecimalFormat = DecimalFormat("0.00").apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    /**
     * Formats amount as South African Rand with currency symbol.
     * Examples: R1,234.56 or R0.50
     */
    fun formatAsZAR(amount: Double): String = when {
        !amount.isFinite() -> zarFormatter.format(BigDecimal.ZERO)
        else -> zarFormatter.format(amount.toMoneyBigDecimal())
    }

    /**
     * Formats amount as South African Rand with currency symbol (BigDecimal version).
     */
    fun formatAsZAR(amount: BigDecimal): String =
        zarFormatter.format(amount.setScale(2, java.math.RoundingMode.HALF_UP))

    /**
     * Formats amount as plain decimal with 2 places (no currency symbol).
     * Examples: 1234.56 or 0.50
     */
    fun formatAsPlain(amount: Double): String = when {
        !amount.isFinite() -> "0.00"
        else -> plainFormatter.format(amount.toMoneyBigDecimal().toDouble())
    }

    /**
     * Formats amount as plain decimal with 2 places (BigDecimal version).
     */
    fun formatAsPlain(amount: BigDecimal): String =
        plainFormatter.format(amount.setScale(2, java.math.RoundingMode.HALF_UP).toDouble())

    /**
     * Formats amount with specified currency symbol.
     * Example: formatWithCurrency(1234.56, "$") returns "$1,234.56"
     */
    @Suppress("UNUSED")  // Public API for future use
    fun formatWithCurrency(amount: Double, currencySymbol: String = "R"): String = when {
        !amount.isFinite() -> "$currencySymbol 0.00"
        else -> "$currencySymbol ${formatAsPlain(amount)}"
    }

    /**
     * Formats amount as percentage with 2 decimal places and % sign.
     * Example: formatAsPercentage(15.5) returns "15.50%"
     */
    fun formatAsPercentage(percentage: Double): String = when {
        !percentage.isFinite() -> "0.00%"
        else -> "${plainFormatter.format(percentage.toMoneyBigDecimal().toDouble())}%"
    }

    /**
     * Returns true if money amounts are equal (accounting for floating-point precision).
     */
    @Suppress("UNUSED")  // Public API for future use
    fun areEqual(amount1: Double, amount2: Double): Boolean =
        amount1.toMoneyBigDecimal() == amount2.toMoneyBigDecimal()

    /**
     * Compares two money amounts with 2 decimal precision.
     * Returns -1 if amount1 < amount2, 0 if equal, 1 if amount1 > amount2
     */
    @Suppress("UNUSED")  // Public API for future use
    fun compareMoney(amount1: Double, amount2: Double): Int =
        amount1.toMoneyBigDecimal().compareTo(amount2.toMoneyBigDecimal())
}

/**
 * Extension functions for convenient formatting of monetary values.
 */

/** Formats Double as ZAR currency string with symbol */
fun Double.formatZAR(): String = MoneyFormatter.formatAsZAR(this)

/** Formats BigDecimal as ZAR currency string with symbol */
fun BigDecimal.formatZAR(): String = MoneyFormatter.formatAsZAR(this)

/** Formats Double as plain decimal (no currency symbol) */
@Suppress("UNUSED")  // Public API for future use
fun Double.formatPlain(): String = MoneyFormatter.formatAsPlain(this)

/** Formats BigDecimal as plain decimal (no currency symbol) */
@Suppress("UNUSED")  // Public API for future use
fun BigDecimal.formatPlain(): String = MoneyFormatter.formatAsPlain(this)

/** Formats Double as percentage with % sign */
@Suppress("UNUSED")  // Public API for future use
fun Double.formatPercent(): String = MoneyFormatter.formatAsPercentage(this)

/** Safely formats money value, handling nulls as "R0.00" */
@Suppress("UNUSED")  // Public API for future use
fun Double?.formatZARSafe(): String = if (this == null || !isFinite()) "R0.00" else formatZAR()

/** Safely formats money value, handling nulls as "0.00" */
@Suppress("UNUSED")  // Public API for future use
fun Double?.formatPlainSafe(): String = if (this == null || !isFinite()) "0.00" else formatPlain()

