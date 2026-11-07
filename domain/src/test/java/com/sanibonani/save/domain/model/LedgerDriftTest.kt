package com.sanibonani.save.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ledger drift is the difference between the balance a group carries and the sum of its ledger
 * rows. It is computed in the local reconciliation engine, in the admin verification rules and in
 * the nightly consistency worker, and those copies had drifted apart: the local engine called a
 * one-cent gap a discrepancy while `recalculate_all_group_financials_v3` calls it balanced, and the
 * rules compared only the newest cached running balance.
 *
 * These tests pin the single definition the three callers now share - sign, sum and tolerance.
 */
class LedgerDriftTest {

    private fun amounts(vararg values: String) = values.map { Money(it) }

    @Test
    fun `a cached balance above the ledger is a positive drift`() {
        val drift = LedgerDrift.between(Money("1000.00"), amounts("400.00", "500.00"))

        assertEquals(Money("100.00"), drift)
    }

    @Test
    fun `a cached balance below the ledger is a negative drift`() {
        val drift = LedgerDrift.between(Money("800.00"), amounts("400.00", "500.00"))

        assertEquals(Money("-100.00"), drift)
    }

    @Test
    fun `the drift is the balance minus the whole ledger, not minus its newest row`() {
        val ledger = amounts("300.00", "300.00", "300.00")

        assertEquals(Money("100.00"), LedgerDrift.between(Money("1000.00"), ledger))
        assertEquals(Money("900.00"), LedgerDrift.sum(ledger))
    }

    @Test
    fun `an empty ledger leaves the recorded balance as the drift`() {
        assertEquals(Money("1000.00"), LedgerDrift.between(Money("1000.00"), emptyList()))
        assertEquals(Money.ZERO, LedgerDrift.sum(emptyList()))
    }

    @Test
    fun `the ledger sums on cents and never rounds through a float`() {
        val ledger = amounts("0.10", "0.20")

        assertEquals(Money("0.30"), LedgerDrift.sum(ledger))
        assertTrue(LedgerDrift.isBalanced(LedgerDrift.between(Money("0.30"), ledger)))
    }

    @Test
    fun `a drift of exactly one cent is balanced, the way the server reports it`() {
        val drift = LedgerDrift.between(Money("100.01"), amounts("100.00"))

        assertEquals(LedgerDrift.TOLERANCE, drift)
        assertTrue("v3 calls ABS(diff) <= 0.01 balanced; the local engine must not disagree",
            LedgerDrift.isBalanced(drift))
    }

    @Test
    fun `a drift past one cent is not balanced`() {
        val drift = LedgerDrift.between(Money("100.02"), amounts("100.00"))

        assertFalse(LedgerDrift.isBalanced(drift))
    }

    @Test
    fun `a drift past one cent on the other side is not balanced either`() {
        val drift = LedgerDrift.between(Money("99.98"), amounts("100.00"))

        assertEquals(Money("-0.02"), drift)
        assertFalse(LedgerDrift.isBalanced(drift))
    }
}
