package com.sanibonani.save.domain.model

import org.junit.Assert.*
import org.junit.Test

class HybridPotAllocationTest {

    @Test
    fun calculateSplitCorrectlyDividesGroupBalance() {
        val allocation = HybridPotAllocation(
            personalSavingsPct = 60.0,
            lendingPoolPct = 20.0,
            socialFundPct = 10.0,
            investmentPct = 10.0
        )

        assertTrue(allocation.isValidAllocation)

        val total = Money("10000.0")
        val split = allocation.calculateSplit(total)

        assertEquals(6000.0, split.personalSavings.toDouble(), 0.01)
        assertEquals(2000.0, split.lendingPool.toDouble(), 0.01)
        assertEquals(1000.0, split.socialFund.toDouble(), 0.01)
        assertEquals(1000.0, split.investment.toDouble(), 0.01)

        val breakdowns = split.toFormulaBreakdowns(allocation)
        assertEquals(4, breakdowns.size)
        assertTrue(breakdowns[0].title.contains("Personal Savings"))
        assertTrue(breakdowns[1].title.contains("Lending Pool"))
    }

    @Test
    fun detectsInvalidAllocations() {
        val invalid = HybridPotAllocation(
            personalSavingsPct = 50.0,
            lendingPoolPct = 20.0,
            socialFundPct = 10.0,
            investmentPct = 10.0
        )
        assertFalse(invalid.isValidAllocation)
    }
}
