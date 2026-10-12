package com.sanibonani.save.domain.utils

import com.sanibonani.save.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Monetary value formatter ensuring all currency displays use 2 decimal places.
 * This is the single source of truth for all monetary formatting in the app.
 * Uses Money/BigDecimal exclusively - NO Double for financial values.
 */
object MoneyFormatter {

    // South African Rand formatter with 2 decimal places
    private val zarFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("ZA").build()).apply {
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
    fun formatAsZAR(amount: Money): String =
        zarFormatter.format(amount.amount.setScale(2, RoundingMode.HALF_EVEN))

    /**
     * Formats amount as South African Rand with currency symbol (BigDecimal version).
     */
    fun formatAsZAR(amount: BigDecimal): String =
        zarFormatter.format(amount.setScale(2, RoundingMode.HALF_UP))

    /**
     * Formats amount as plain decimal with 2 places (no currency symbol).
     * Examples: 1234.56 or 0.50
     */
    fun formatAsPlain(amount: Money): String =
        plainFormatter.format(amount.amount.setScale(2, RoundingMode.HALF_EVEN))

    /**
     * Formats amount as plain decimal with 2 places (BigDecimal version).
     */
    fun formatAsPlain(amount: BigDecimal): String =
        plainFormatter.format(amount.setScale(2, RoundingMode.HALF_UP))

    /**
     * Formats amount with specified currency symbol.
     * Example: formatWithCurrency(Money(1234.56), "$") returns "$1,234.56"
     */
    @Suppress("UNUSED")  // Public API for future use
    fun formatWithCurrency(amount: Money, currencySymbol: String = "R"): String =
        "$currencySymbol ${formatAsPlain(amount)}"

    /**
     * Formats percentage with 2 decimal places and % sign.
     * Example: formatAsPercentage(15.5) returns "15.50%"
     * Note: percentage is a ratio (not currency), so Double is acceptable here.
     */
    fun formatAsPercentage(percentage: Double): String = when {
        !percentage.isFinite() -> "0.00%"
        else -> "${plainFormatter.format(BigDecimal.valueOf(percentage).setScale(2, RoundingMode.HALF_UP))}%"
    }

    /**
     * Returns true if money amounts are equal.
     */
    @Suppress("UNUSED")  // Public API for future use
    fun areEqual(amount1: Money, amount2: Money): Boolean = amount1 == amount2

    /**
     * Compares two money amounts with 2 decimal precision.
     * Returns -1 if amount1 < amount2, 0 if equal, 1 if amount1 > amount2
     */
    @Suppress("UNUSED")  // Public API for future use
    fun compareMoney(amount1: Money, amount2: Money): Int = amount1.compareTo(amount2)
}

/**
 * Extension functions for convenient formatting of monetary values.
 */

/** Formats Money as ZAR currency string with symbol */
fun Money.formatZAR(): String = MoneyFormatter.formatAsZAR(this)

/** Formats BigDecimal as ZAR currency string with symbol */
fun BigDecimal.formatZAR(): String = MoneyFormatter.formatAsZAR(this)

/** Formats Money as plain decimal (no currency symbol) */
@Suppress("UNUSED")  // Public API for future use
fun Money.formatPlain(): String = MoneyFormatter.formatAsPlain(this)

/** Formats BigDecimal as plain decimal (no currency symbol) */
@Suppress("UNUSED")  // Public API for future use
fun BigDecimal.formatPlain(): String = MoneyFormatter.formatAsPlain(this)

/** Formats percentage Double with % sign */
@Suppress("UNUSED")  // Public API for future use
fun Double.formatPercent(): String = MoneyFormatter.formatAsPercentage(this)

/** Safely formats money value, handling nulls as "R0.00" */
@Suppress("UNUSED")  // Public API for future use
fun Money?.formatZARSafe(): String = if (this == null) "R0.00" else formatZAR()

/** Safely formats money value, handling nulls as "0.00" */
@Suppress("UNUSED")  // Public API for future use
fun Money?.formatPlainSafe(): String = if (this == null) "0.00" else formatPlain()

