package com.sanibonani.save.domain.utils

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Suppress("DEPRECATION")  // Java deprecation, not an issue in practice
private val zarFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
    currency = Currency.getInstance("ZAR")
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

/**
 * DEPRECATED: Use MoneyFormatter.formatAsZAR() or the extension function Double.formatZAR() instead.
 * This function is maintained for backward compatibility only.
 */
@Deprecated(
    message = "Use MoneyFormatter.formatAsZAR() or Double.formatZAR() extension instead",
    replaceWith = ReplaceWith("MoneyFormatter.formatAsZAR(amount)", "com.sanibonani.save.domain.utils.MoneyFormatter"),
    level = DeprecationLevel.WARNING
)
fun formatZAR(amount: Double): String =
    if (!amount.isFinite()) zarFormatter.format(BigDecimal.ZERO)
    else zarFormatter.format(amount.toMoneyBigDecimal())

/**
 * DEPRECATED: Use MoneyFormatter.formatAsZAR() or the extension function BigDecimal.formatZAR() instead.
 * This function is maintained for backward compatibility only.
 */
@Deprecated(
    message = "Use MoneyFormatter.formatAsZAR() or BigDecimal.formatZAR() extension instead",
    replaceWith = ReplaceWith("MoneyFormatter.formatAsZAR(amount)", "com.sanibonani.save.domain.utils.MoneyFormatter"),
    level = DeprecationLevel.WARNING
)
fun formatZAR(amount: BigDecimal): String = zarFormatter.format(amount.setScale(2, java.math.RoundingMode.HALF_UP))
