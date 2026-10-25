package com.sanibonani.save.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payout state machine is shared by the online admin path and the offline replay lane.
 * A terminal payout must not be reopened by a stale queued transition, and money must only
 * leave through the approved chain.
 */
class PayoutTransitionsTest {

    @Test
    fun `terminal payouts accept no further status`() {
        listOf(PayoutStatus.COMPLETED, PayoutStatus.CANCELLED, PayoutStatus.REJECTED).forEach { terminal ->
            PayoutStatus.entries.filter { it != terminal }.forEach { next ->
                assertFalse("$terminal must not move to $next", terminal.canTransitionTo(next))
            }
        }
    }

    @Test
    fun `a failed transfer can be retried or abandoned`() {
        assertTrue(PayoutStatus.FAILED.canTransitionTo(PayoutStatus.PROCESSING))
        assertTrue(PayoutStatus.FAILED.canTransitionTo(PayoutStatus.CANCELLED))
        assertTrue(PayoutStatus.FAILED.canTransitionTo(PayoutStatus.REJECTED))
    }

    @Test
    fun `money only leaves through the approved chain`() {
        assertTrue(PayoutStatus.PENDING.canTransitionTo(PayoutStatus.GROUP_APPROVED))
        assertTrue(PayoutStatus.GROUP_APPROVED.canTransitionTo(PayoutStatus.PROCESSING))
        assertTrue(PayoutStatus.PROCESSING.canTransitionTo(PayoutStatus.COMPLETED))

        assertFalse(PayoutStatus.PENDING.canTransitionTo(PayoutStatus.PROCESSING))
        assertFalse(PayoutStatus.PENDING.canTransitionTo(PayoutStatus.COMPLETED))
        assertFalse(PayoutStatus.GROUP_APPROVED.canTransitionTo(PayoutStatus.COMPLETED))
    }

    @Test
    fun `a rejection is available until the transfer starts`() {
        assertTrue(PayoutStatus.PENDING.canTransitionTo(PayoutStatus.REJECTED))
        assertTrue(PayoutStatus.GROUP_APPROVED.canTransitionTo(PayoutStatus.REJECTED))
        assertFalse(PayoutStatus.PROCESSING.canTransitionTo(PayoutStatus.REJECTED))
    }

    @Test
    fun `the same status is not a transition`() {
        PayoutStatus.entries.forEach { status ->
            assertFalse("$status is not a transition from itself", status.canTransitionTo(status))
        }
    }
}
