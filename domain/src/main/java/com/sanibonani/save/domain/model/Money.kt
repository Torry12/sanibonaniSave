package com.sanibonani.save.domain.model

import com.sanibonani.save.domain.logging.AppLogger
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*


/**
 * Robust domain model for handling currency (South African Rand).
 * Replaces unreliable [Double] math with [BigDecimal] for financial precision.
 *
 * CRITICAL: NEVER use [Double] for currency storage or arithmetic.
 * AI Optimization: This class ensures that all financial calculations are deterministic
 * and free from floating-point drift, which is essential for accurate ledger reconstruction.
 */
@Serializable(with = MoneySerializer::class)
data class Money(val amount: BigDecimal) : Comparable<Money> {

    /**
     * Permanently blocked entry point: binary floating point cannot hold rand exactly, so a
     * financial amount must never be built from a [Double]. Use the [String] form for literals,
     * the [Int] form for whole rand, or [Companion.fromDouble] at the edge where a third-party
     * payload really does arrive as a number.
     */
    @Deprecated(
        "Financial values must not come from Double: use Money(\"150.00\"), or Money.fromDouble for provider payloads.",
        replaceWith = ReplaceWith("Money(amount.toString())"),
        level = DeprecationLevel.ERROR
    )
    constructor(amount: Double) : this(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_EVEN))
    constructor(amount: Int)    : this(BigDecimal.valueOf(amount.toLong()).setScale(2, RoundingMode.HALF_EVEN))
    constructor(amount: String) : this(BigDecimal(amount).setScale(2, RoundingMode.HALF_EVEN))

    companion object {
        val ZERO = Money(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN))
        private val zarFormatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-ZA")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

        // Interop door for provider payloads that still arrive as floating point numbers.
        fun fromDouble(value: Double?): Money =
            value?.let { Money(BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_EVEN)) } ?: ZERO
    }

    operator fun plus(other: Money): Money = Money(this.amount.add(other.amount).setScale(2, RoundingMode.HALF_EVEN))
    operator fun minus(other: Money): Money = Money(this.amount.subtract(other.amount).setScale(2, RoundingMode.HALF_EVEN))
    operator fun unaryMinus(): Money = Money(this.amount.negate().setScale(2, RoundingMode.HALF_EVEN))
    operator fun times(multiplier: Double): Money = Money(this.amount.multiply(BigDecimal.valueOf(multiplier)).setScale(2, RoundingMode.HALF_EVEN))
    operator fun times(multiplier: Int): Money = Money(this.amount.multiply(BigDecimal.valueOf(multiplier.toLong())).setScale(2, RoundingMode.HALF_EVEN))
    operator fun times(multiplier: Long): Money = Money(this.amount.multiply(BigDecimal.valueOf(multiplier)).setScale(2, RoundingMode.HALF_EVEN))
    operator fun times(multiplier: BigDecimal): Money = Money(this.amount.multiply(multiplier).setScale(2, RoundingMode.HALF_EVEN))

    fun dividedBy(divisor: Int): Money = Money(this.amount.divide(BigDecimal(divisor), 2, RoundingMode.HALF_EVEN))
    fun dividedBy(divisor: Long): Money = Money(this.amount.divide(BigDecimal(divisor), 2, RoundingMode.HALF_EVEN))
    fun dividedBy(divisor: Double): Money = Money(this.amount.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_EVEN))

    operator fun div(divisor: Int): Money = dividedBy(divisor)
    operator fun div(divisor: Long): Money = dividedBy(divisor)
    operator fun div(divisor: Double): Money = dividedBy(divisor)

    /** Returns a percentage of this money amount. */
    fun percent(percentage: Double): Money = this * (percentage / 100.0)

    /** Returns the ratio of this money to another (Double). */
    fun ratio(other: Money): Double = if (other.isZero) 0.0 else this.amount.toDouble() / other.amount.toDouble()

    fun max(other: Money): Money = if (this >= other) this else other
    fun min(other: Money): Money = if (this <= other) this else other
    fun abs(): Money = Money(this.amount.abs())

    fun toDouble(): Double = amount.toDouble()
    fun toPlainString(): String = amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString()
    fun toCents(): Long = amount.multiply(BigDecimal("100")).toLong()

    fun format(): String = zarFormatter.format(amount)

    fun formatShort(): String {
        val absAmount = amount.abs().toDouble()
        // Locale.US keeps the decimal separator a dot on every device: a comma-decimal locale
        // would otherwise render an amount as "R1,5M" while the full figure renders "R1.50".
        return when {
            absAmount >= 1_000_000 -> "R${String.format(Locale.US, "%.1fM", absAmount / 1_000_000)}"
            absAmount >= 1_000 -> "R${String.format(Locale.US, "%.1fK", absAmount / 1_000)}"
            else -> format()
        }
    }

    override operator fun compareTo(other: Money): Int = this.amount.compareTo(other.amount)

    /**
     * A data class otherwise renders as `Money(amount=250.00)`, which leaks into any string
     * interpolation of a monetary column (CSV exports, notifications, logs). Financial output
     * must be plain numeric text; use [format] for a user-facing R-denominated string.
     */
    override fun toString(): String = amount.toPlainString()

    val isZero: Boolean get() = amount.signum() == 0
    val isPositive: Boolean get() = amount.signum() > 0
    val isNegative: Boolean get() = amount.signum() < 0
}

operator fun Int.times(money: Money): Money = money * this
operator fun Double.times(money: Money): Money = money * this

object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Money", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Money) {
        encoder.encodeString(value.toPlainString())
    }

    /**
     * Parses a monetary amount.
     *
     * A malformed value MUST degrade to [Money.ZERO] rather than throwing: this
     * runs inside every sync response, and throwing would abort the whole fetch
     * because of one bad row. The fallback is therefore retained for resilience,
     * but it is now **visible**: the corrupt input is logged at ERROR level, so a
     * silent R0.00 (e.g. a bad `platform_settings` row causing a group to be
     * activated for free) shows up in logs instead of quietly understating money.
     */
    override fun deserialize(decoder: Decoder): Money {
        val dec = decoder as? JsonDecoder
        return if (dec != null) {
            val element = dec.decodeJsonElement()
            if (element is JsonPrimitive) {
                // AI Precision Hardening: Use element.content for both strings and numbers
                // to avoid floating-point loss via element.double.
                if (element.content.toBigDecimalOrNull() != null) {
                    Money(element.content)
                } else {
                    // Non-numeric content (null, "", "N/A", ...) is a data problem, not
                    // a legitimate zero: surface it instead of silently returning ZERO.
                    AppLogger.w(
                        "MoneySerializer",
                        "Monetary field is not numeric; treated as R0.00. raw='${element.content}'"
                    )
                    Money.ZERO
                }
            } else {
                AppLogger.w(
                    "MoneySerializer",
                    "Monetary field is not a primitive; treated as R0.00. kind=${element::class.simpleName}"
                )
                Money.ZERO
            }
        } else {
            try {
                Money(decoder.decodeString())
            } catch (e: Exception) {
                AppLogger.e(
                    "MoneySerializer",
                    "Discarding unparseable monetary amount; treated as R0.00. "
                )
                Money.ZERO
            }
        }
    }
}
