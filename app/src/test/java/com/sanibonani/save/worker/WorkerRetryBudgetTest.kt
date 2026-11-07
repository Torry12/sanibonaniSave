package com.sanibonani.save.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Most periodic cycles answered a read they could not complete with an uncapped Result.retry(), so a
 * group whose row would never arrive kept re-running on the backoff schedule forever - while three of
 * the workers already stopped at three attempts. The budget is now one rule for every cycle.
 */
class WorkerRetryBudgetTest {

    @Test
    fun `a cycle keeps retrying inside its budget`() {
        assertFalse(outOfRetryBudget(0))
        assertFalse(outOfRetryBudget(MAX_CYCLE_ATTEMPTS - 1))
    }

    @Test
    fun `a cycle that spent its budget ends the period`() {
        assertTrue(outOfRetryBudget(MAX_CYCLE_ATTEMPTS))
        assertTrue(outOfRetryBudget(MAX_CYCLE_ATTEMPTS + 1))
    }
}
