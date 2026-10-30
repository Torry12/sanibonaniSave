package com.sanibonani.save.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

/**
 * Rounding contract for every financial value that enters the app. These replace the retired
 * Double-based `roundMoneyToTwoDecimals` helper, which rounded HALF_UP and therefore disagreed
 * with [Money] on half cents.
 */
class MoneyRoundingTest {

    @Test
    fun `half cents round to the even cent`() {
        // HALF_UP would report 1.01 here; ledger math must be reproducible against the DB.
        assertEquals("1.00", Money("1.005").toPlainString())
        assertEquals("1.02", Money("1.015").toPlainString())
    }

    @Test
    fun `repeated cent additions stay exact`() {
        var total = Money.ZERO
        repeat(10) { total += Money("0.10") }
        assertEquals(Money("1.00"), total)
    }

    @Test
    fun `amounts built from differently written literals compare equal`() {
        assertEquals(Money("10.0"), Money("10.00"))
        assertEquals(Money(10), Money("10.00"))
    }

    @Test
    fun `BigDecimal construction normalizes scale and equality keeps matching hash codes`() {
        val rounded = Money(BigDecimal("1.005"))
        val normalized = Money(BigDecimal("1.00"))

        assertEquals("1.00", rounded.toPlainString())
        assertEquals(normalized, rounded)
        assertEquals(normalized.hashCode(), rounded.hashCode())
    }

    @Test
    fun `non-finite floating values and zero divisors are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Money.fromDouble(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { Money("1.00") * Double.POSITIVE_INFINITY }
        assertThrows(IllegalArgumentException::class.java) { Money("1.00").dividedBy(0.0) }
        assertThrows(IllegalArgumentException::class.java) { Money("1.00").dividedBy(Money.ZERO) }
    }

    @Test
    fun `cent conversion fails rather than truncating an overflowing amount`() {
        assertThrows(ArithmeticException::class.java) {
            Money("92233720368547758.08").toCents()
        }
    }

    @Test
    fun `ratio does not overflow when both monetary amounts exceed double range`() {
        assertEquals(
            2.0,
            Money(BigDecimal.TEN.pow(400).multiply(BigDecimal("2")))
                .ratio(Money(BigDecimal.TEN.pow(400))),
            0.0
        )
    }

    @Test
    fun `percentage calculations use decimal rates and round the final amount`() {
        assertEquals(Money("0.02"), Money("0.05").percent(BigDecimal("33.3333333333")))
        assertEquals(Money("0.00"), Money("0.05").percent(10))
    }

    @Test
    fun `proportional shares avoid floating point and round half even`() {
        assertEquals(Money("0.02"), Money("0.05").proportionOf(Money("1.00"), Money("3.00")))
        assertEquals(Money.ZERO, Money("100.00").proportionOf(Money("1.00"), Money.ZERO))
    }
}
