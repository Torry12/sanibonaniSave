package com.sanibonani.save.domain.model

import org.junit.Assert.assertEquals
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
