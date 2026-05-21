package com.sanibonani.save.domain.model

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

/**
 * Robust domain model for handling currency (South African Rand).
 * Replaces unreliable [Double] math with [BigDecimal] for financial precision.
 */
@Serializable(with = MoneySerializer::class)
data class Money(val amount: BigDecimal) : Comparable<Money> {
    
    constructor(amount: Double) : this(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_EVEN))
    constructor(amount: String) : this(BigDecimal(amount).setScale(2, RoundingMode.HALF_EVEN))

    companion object {
        val ZERO = Money(BigDecimal.ZERO)
        private val zarFormatter = NumberFormat.getCurrencyInstance(Locale("en", "ZA")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

        fun fromDouble(value: Double?): Money = value?.let { Money(it) } ?: ZERO
    }

    operator fun plus(other: Money) = Money(amount.add(other.amount))
    operator fun minus(other: Money) = Money(amount.subtract(other.amount))
    operator fun times(multiplier: Double) = Money(amount.multiply(BigDecimal.valueOf(multiplier)))
    operator fun div(divisor: Int) = Money(amount.divide(BigDecimal(divisor), RoundingMode.HALF_EVEN))

    fun toDouble(): Double = amount.toDouble()
    
    fun format(): String = zarFormatter.format(amount)
    
    fun formatShort(): String {
        val absAmount = amount.abs().toDouble()
        return when {
            absAmount >= 1_000_000 -> "R${String.format("%.1fM", absAmount / 1_000_000)}"
            absAmount >= 1_000 -> "R${String.format("%.1fK", absAmount / 1_000)}"
            else -> format()
        }
    }

    override fun compareTo(other: Money): Int = amount.compareTo(other.amount)
    
    val isZero: Boolean get() = amount.signum() == 0
    val isPositive: Boolean get() = amount.signum() > 0
    val isNegative: Boolean get() = amount.signum() < 0
}

object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Money", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Money) = encoder.encodeDouble(value.toDouble())
    override fun deserialize(decoder: Decoder): Money = Money(decoder.decodeDouble())
}
