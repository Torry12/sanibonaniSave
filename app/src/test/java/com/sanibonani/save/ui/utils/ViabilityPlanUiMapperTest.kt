package com.sanibonani.save.ui.utils

import com.sanibonani.save.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViabilityPlanUiMapperTest {

    private fun createDefaultPlan(
        initialContribution: Double = 1000.0,
        suggestedMonthlyContribution: Double = 300.0,
        projectedValue: Double = 12000.0,
        isViable: Boolean = true,
        activeMemberRatio: Double = 1.0,
        defaultRiskFactor: Double = 1.0,
        cycleSlippageFactor: Double = 1.0,
        projectionRetentionFactor: Double = 1.0,
        claimReadinessFactor: Double = 1.0,
        mortalityBufferFactor: Double = 1.0,
        reserveAdequacyFactor: Double = 1.0
    ) = ViabilityPlan(
        initialContribution = Money(initialContribution),
        suggestedMonthlyContribution = Money(suggestedMonthlyContribution),
        projectedValue = Money(projectedValue),
        isViable = isViable,
        goalAmount = Money.ZERO,
        periodMonths = 12,
        messages = emptyList(),
        requiredMonthlyToMeetGoal = Money.ZERO,
        shortfallAmount = Money.ZERO,
        breakEvenMonths = 0,
        compoundedProjectedValue = Money.ZERO,
        optimisticProjectedValue = Money.ZERO,
        pessimisticProjectedValue = Money.ZERO,
        activeMemberRatio = activeMemberRatio,
        inflationAdjustmentFactor = 1.0,
        projectionRetentionFactor = projectionRetentionFactor,
        claimReadinessFactor = claimReadinessFactor,
        mortalityBufferFactor = mortalityBufferFactor,
        reserveAdequacyFactor = reserveAdequacyFactor,
        marketReturnPremiumFactor = 1.0,
        volatilityHaircutFactor = 1.0,
        collectionEfficiencyFactor = 1.0,
        festivePayoutPressureFactor = 1.0,
        defaultRiskFactor = defaultRiskFactor,
        cycleSlippageFactor = cycleSlippageFactor,
        withdrawalPressureFactor = 1.0,
        inflationSafetyFactor = 1.0,
        survivorUncertaintyFactor = 1.0,
        horizonCompoundingFactor = 1.0,
        goalStretchRatio = 1.0,
        growthConservatismFactor = 1.0
    )

    @Test
    fun `toViabilityFactors returns non-neutral factors by default`() {
        val plan = createDefaultPlan(
            activeMemberRatio = 0.9,
            defaultRiskFactor = 1.03,
            cycleSlippageFactor = 1.05
        )

        val factors = plan.toViabilityFactors()

        assertTrue(factors.any { it.key == "active_member_ratio" && it.value == "0.9" })
        assertTrue(factors.any { it.key == "default_risk_factor" && it.value == "1.03" })
        assertTrue(factors.any { it.key == "cycle_slippage_factor" && it.value == "1.05" })
        assertTrue(factors.none { it.key == "projection_retention_factor" })
    }

    @Test
    fun `toViabilityFactors includeNeutral true returns full factor set`() {
        val plan = createDefaultPlan()

        val factors = plan.toViabilityFactors(includeNeutral = true)

        assertEquals(24, factors.size) // Total number of factor properties in ViabilityPlan
    }

    @Test
    fun `toViabilityFactors keeps active member ratio even when neutral`() {
        val plan = createDefaultPlan(activeMemberRatio = 1.0)

        val factors = plan.toViabilityFactors()

        assertTrue(factors.any { it.key == "active_member_ratio" && it.value == "1.0" })
    }

    @Test
    fun `trend classifies values above one as lift`() {
        val factor = ViabilityFactorUi(
            key = "market_return_premium_factor",
            label = "Market Return Premium",
            value = "1.08",
            numericValue = 1.08
        )

        assertEquals(ViabilityFactorTrend.LIFT, factor.trend())
    }

    @Test
    fun `trend classifies values below one as haircut and near one as neutral`() {
        val haircutFactor = ViabilityFactorUi(
            key = "volatility_haircut_factor",
            label = "Volatility Haircut",
            value = "0.91",
            numericValue = 0.91
        )
        val neutralFactor = ViabilityFactorUi(
            key = "projection_retention_factor",
            label = "Projection Retention",
            value = "1.0",
            numericValue = 1.0
        )

        assertEquals(ViabilityFactorTrend.HAIRCUT, haircutFactor.trend())
        assertEquals(ViabilityFactorTrend.NEUTRAL, neutralFactor.trend())
    }

    @Test
    fun `trend descriptions explain projection impact`() {
        assertEquals(
            "Lift factors are above x1.00 and increase the projection.",
            ViabilityFactorTrend.LIFT.description()
        )
        assertEquals(
            "Haircut factors are below x1.00 and reduce the projection.",
            ViabilityFactorTrend.HAIRCUT.description()
        )
        assertEquals(
            "Neutral factors stay around x1.00 with little or no impact.",
            ViabilityFactorTrend.NEUTRAL.description()
        )
    }

    @Test
    fun `factor descriptions explain selected factor meaning`() {
        val reserveFactor = ViabilityFactorUi(
            key = "reserve_adequacy_factor",
            label = "Reserve Adequacy",
            value = "1.04",
            numericValue = 1.04
        )
        val unknownFactor = ViabilityFactorUi(
            key = "custom_factor",
            label = "Custom Factor",
            value = "1.0",
            numericValue = 1.0
        )

        assertEquals(
            "Rewards stronger reserve cover and signals when savings buffers are thin.",
            reserveFactor.description()
        )
        assertEquals(
            "Explains how this multiplier changes the final viability projection.",
            unknownFactor.description()
        )
    }

    @Test
    fun `toViabilityFactors for ROSCA excludes unrelated burial factors`() {
        val plan = createDefaultPlan(
            activeMemberRatio = 0.88,
            projectionRetentionFactor = 0.96,
            defaultRiskFactor = 1.07,
            cycleSlippageFactor = 1.03,
            claimReadinessFactor = 1.25
        )

        val factors = plan.toViabilityFactors(groupType = GroupType.ROSCA)

        assertTrue(factors.any { it.key == "default_risk_factor" })
        assertTrue(factors.any { it.key == "cycle_slippage_factor" })
        assertTrue(factors.any { it.key == "projection_retention_factor" })
        assertTrue(factors.none { it.key == "claim_readiness_factor" })
    }

    @Test
    fun `toViabilityPanels returns bespoke panel titles per group type`() {
        val plan = createDefaultPlan(
            activeMemberRatio = 0.9,
            projectionRetentionFactor = 0.99,
            claimReadinessFactor = 1.20,
            mortalityBufferFactor = 1.01,
            reserveAdequacyFactor = 1.10
        )

        val panels = plan.toViabilityPanels(groupType = GroupType.BURIAL_SOCIETY)

        assertTrue(panels.any { it.title == "Contribution Delivery" })
        assertTrue(panels.any { it.title == "Claim Protection" })
    }
}
