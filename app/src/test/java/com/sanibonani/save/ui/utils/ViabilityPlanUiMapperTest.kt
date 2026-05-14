package com.sanibonani.save.ui.utils

import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.ViabilityPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViabilityPlanUiMapperTest {

    @Test
    fun `toViabilityFactors returns non-neutral factors by default`() {
        val plan = ViabilityPlan(
            initialContribution = 1000.0,
            suggestedMonthlyContribution = 300.0,
            projectedValue = 12000.0,
            isViable = true,
            activeMemberRatio = 0.9,
            defaultRiskFactor = 1.03,
            cycleSlippageFactor = 1.05
        )

        val factors = plan.toViabilityFactors()

        assertTrue(factors.any { it.key == "active_member_ratio" && it.value == 0.9 })
        assertTrue(factors.any { it.key == "default_risk_factor" && it.value == 1.03 })
        assertTrue(factors.any { it.key == "cycle_slippage_factor" && it.value == 1.05 })
        assertTrue(factors.none { it.key == "projection_retention_factor" })
    }

    @Test
    fun `toViabilityFactors includeNeutral true returns full factor set`() {
        val plan = ViabilityPlan(
            initialContribution = 1000.0,
            suggestedMonthlyContribution = 300.0,
            projectedValue = 12000.0,
            isViable = true
        )

        val factors = plan.toViabilityFactors(includeNeutral = true)

        assertEquals(18, factors.size)
    }

    @Test
    fun `toViabilityFactors keeps active member ratio even when neutral`() {
        val plan = ViabilityPlan(
            initialContribution = 1000.0,
            suggestedMonthlyContribution = 300.0,
            projectedValue = 12000.0,
            isViable = true,
            activeMemberRatio = 1.0
        )

        val factors = plan.toViabilityFactors()

        assertTrue(factors.any { it.key == "active_member_ratio" && it.value == 1.0 })
    }

    @Test
    fun `trend classifies values above one as lift`() {
        val factor = ViabilityFactorUi(
            key = "market_return_premium_factor",
            label = "Market Return Premium",
            value = 1.08
        )

        assertEquals(ViabilityFactorTrend.LIFT, factor.trend())
    }

    @Test
    fun `trend classifies values below one as haircut and near one as neutral`() {
        val haircutFactor = ViabilityFactorUi(
            key = "volatility_haircut_factor",
            label = "Volatility Haircut",
            value = 0.91
        )
        val neutralFactor = ViabilityFactorUi(
            key = "projection_retention_factor",
            label = "Projection Retention",
            value = 1.0
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
            value = 1.04
        )
        val unknownFactor = ViabilityFactorUi(
            key = "custom_factor",
            label = "Custom Factor",
            value = 1.0
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
        val plan = ViabilityPlan(
            initialContribution = 1000.0,
            suggestedMonthlyContribution = 300.0,
            projectedValue = 12000.0,
            isViable = true,
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
        val plan = ViabilityPlan(
            initialContribution = 1000.0,
            suggestedMonthlyContribution = 300.0,
            projectedValue = 12000.0,
            isViable = true,
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

