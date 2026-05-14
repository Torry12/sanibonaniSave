package com.sanibonani.save.ui.utils

import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.ViabilityPlan
import kotlin.math.abs

data class ViabilityFactorUi(
    val key: String,
    val label: String,
    val value: Double
)

data class ViabilityPanelUi(
    val key: String,
    val title: String,
    val subtitle: String,
    val factors: List<ViabilityFactorUi>
)

enum class ViabilityFactorTrend {
    LIFT,
    HAIRCUT,
    NEUTRAL
}

fun ViabilityFactorTrend.description(): String {
    return when (this) {
        ViabilityFactorTrend.LIFT -> "Lift factors are above x1.00 and increase the projection."
        ViabilityFactorTrend.HAIRCUT -> "Haircut factors are below x1.00 and reduce the projection."
        ViabilityFactorTrend.NEUTRAL -> "Neutral factors stay around x1.00 with little or no impact."
    }
}

/**
 * Exposes numeric viability factors as a stable list for charts/tables.
 */
fun ViabilityPlan.toViabilityFactors(
    groupType: GroupType? = null,
    includeNeutral: Boolean = false
): List<ViabilityFactorUi> {
    if (groupType != null) {
        return toViabilityPanels(groupType = groupType, includeNeutral = includeNeutral)
            .flatMap { it.factors }
    }

    val factors = allFactors()

    if (includeNeutral) return factors

    return factors.filter { factor ->
        factor.key == "active_member_ratio" || abs(factor.value - 1.0) > 0.0001
    }
}

fun ViabilityPlan.toViabilityPanels(
    groupType: GroupType,
    includeNeutral: Boolean = false
): List<ViabilityPanelUi> {
    val factorByKey = allFactors().associateBy { it.key }

    return panelSpecsFor(groupType).mapNotNull { spec ->
        val scopedFactors = spec.factorKeys.mapNotNull { factorByKey[it] }
            .filter { factor ->
                includeNeutral || factor.key == "active_member_ratio" || abs(factor.value - 1.0) > 0.0001
            }

        if (scopedFactors.isEmpty()) {
            null
        } else {
            ViabilityPanelUi(
                key = spec.key,
                title = spec.title,
                subtitle = spec.subtitle,
                factors = scopedFactors
            )
        }
    }
}

private fun ViabilityPlan.allFactors(): List<ViabilityFactorUi> {
    return listOf(
        ViabilityFactorUi("active_member_ratio", "Active Member Ratio", activeMemberRatio),
        ViabilityFactorUi("inflation_adjustment_factor", "Inflation Adjustment", inflationAdjustmentFactor),
        ViabilityFactorUi("projection_retention_factor", "Projection Retention", projectionRetentionFactor),
        ViabilityFactorUi("claim_readiness_factor", "Claim Readiness", claimReadinessFactor),
        ViabilityFactorUi("mortality_buffer_factor", "Mortality Buffer", mortalityBufferFactor),
        ViabilityFactorUi("reserve_adequacy_factor", "Reserve Adequacy", reserveAdequacyFactor),
        ViabilityFactorUi("market_return_premium_factor", "Market Return Premium", marketReturnPremiumFactor),
        ViabilityFactorUi("volatility_haircut_factor", "Volatility Haircut", volatilityHaircutFactor),
        ViabilityFactorUi("collection_efficiency_factor", "Collection Efficiency", collectionEfficiencyFactor),
        ViabilityFactorUi("festive_payout_pressure_factor", "Festive Payout Pressure", festivePayoutPressureFactor),
        ViabilityFactorUi("default_risk_factor", "Default Risk", defaultRiskFactor),
        ViabilityFactorUi("cycle_slippage_factor", "Cycle Slippage", cycleSlippageFactor),
        ViabilityFactorUi("withdrawal_pressure_factor", "Withdrawal Pressure", withdrawalPressureFactor),
        ViabilityFactorUi("inflation_safety_factor", "Inflation Safety", inflationSafetyFactor),
        ViabilityFactorUi("survivor_uncertainty_factor", "Survivor Uncertainty", survivorUncertaintyFactor),
        ViabilityFactorUi("horizon_compounding_factor", "Horizon Compounding", horizonCompoundingFactor),
        ViabilityFactorUi("goal_stretch_ratio", "Goal Stretch Ratio", goalStretchRatio),
        ViabilityFactorUi("growth_conservatism_factor", "Growth Conservatism", growthConservatismFactor)
    )
}

private data class ViabilityPanelSpec(
    val key: String,
    val title: String,
    val subtitle: String,
    val factorKeys: List<String>
)

private fun panelSpecsFor(groupType: GroupType): List<ViabilityPanelSpec> {
    val common = ViabilityPanelSpec(
        key = "common_delivery",
        title = "Contribution Delivery",
        subtitle = "Tracks participation and retained value across the plan horizon.",
        factorKeys = listOf("active_member_ratio", "projection_retention_factor")
    )

    val bespoke = when (groupType) {
        GroupType.BURIAL_SOCIETY -> ViabilityPanelSpec(
            key = "burial_claim_protection",
            title = "Claim Protection",
            subtitle = "Ensures claims, mortality and reserves are funded with prudence.",
            factorKeys = listOf(
                "claim_readiness_factor",
                "mortality_buffer_factor",
                "reserve_adequacy_factor"
            )
        )
        GroupType.INVESTMENT_CLUB -> ViabilityPanelSpec(
            key = "investment_return_quality",
            title = "Return Quality",
            subtitle = "Balances growth assumptions against investment volatility risk.",
            factorKeys = listOf(
                "market_return_premium_factor",
                "volatility_haircut_factor"
            )
        )
        GroupType.STOKVEL -> ViabilityPanelSpec(
            key = "stokvel_collection_discipline",
            title = "Collection Discipline",
            subtitle = "Reflects payment capture consistency and festive-season pressure.",
            factorKeys = listOf(
                "collection_efficiency_factor",
                "festive_payout_pressure_factor"
            )
        )
        GroupType.ROSCA -> ViabilityPanelSpec(
            key = "rosca_cycle_reliability",
            title = "Cycle Reliability",
            subtitle = "Focuses on default and slippage risk in rotational payout cycles.",
            factorKeys = listOf(
                "default_risk_factor",
                "cycle_slippage_factor"
            )
        )
        GroupType.EMERGENCY_FUND -> ViabilityPanelSpec(
            key = "emergency_liquidity_resilience",
            title = "Liquidity Resilience",
            subtitle = "Protects emergency purchasing power and withdrawal resilience.",
            factorKeys = listOf(
                "withdrawal_pressure_factor",
                "inflation_safety_factor"
            )
        )
        GroupType.TONTINE -> ViabilityPanelSpec(
            key = "tontine_survivor_projection",
            title = "Survivor Projection",
            subtitle = "Models survivor uncertainty and long-horizon accumulation effects.",
            factorKeys = listOf(
                "survivor_uncertainty_factor",
                "horizon_compounding_factor"
            )
        )
        GroupType.COMMUNITY_SAVINGS,
        GroupType.OTHER -> ViabilityPanelSpec(
            key = "community_goal_stretch",
            title = "Goal Stretch",
            subtitle = "Measures goal ambition and conservative growth assumptions.",
            factorKeys = listOf(
                "goal_stretch_ratio",
                "growth_conservatism_factor"
            )
        )
    }

    return listOf(common, bespoke)
}

fun ViabilityFactorUi.trend(epsilon: Double = 0.0001): ViabilityFactorTrend {
    return when {
        value > 1.0 + epsilon -> ViabilityFactorTrend.LIFT
        value < 1.0 - epsilon -> ViabilityFactorTrend.HAIRCUT
        else -> ViabilityFactorTrend.NEUTRAL
    }
}

fun ViabilityFactorUi.description(): String {
    return when (key) {
        "active_member_ratio" -> "Shows how many enrolled members are actively contributing to support the plan."
        "inflation_adjustment_factor" -> "Adjusts the target for inflation so the future goal keeps its purchasing power."
        "projection_retention_factor" -> "Reflects how much of projected savings is expected to remain after normal leakage or churn."
        "claim_readiness_factor" -> "Builds in a buffer for expected claim obligations that must be funded on time."
        "mortality_buffer_factor" -> "Adds extra protection for member mortality uncertainty in risk-heavy group models."
        "reserve_adequacy_factor" -> "Rewards stronger reserve cover and signals when savings buffers are thin."
        "market_return_premium_factor" -> "Captures upside from expected investment or market-linked returns."
        "volatility_haircut_factor" -> "Applies a cautionary reduction to account for unstable or volatile returns."
        "collection_efficiency_factor" -> "Measures how reliably the group converts scheduled contributions into cash collected."
        "festive_payout_pressure_factor" -> "Models seasonal withdrawal or payout pressure that can reduce retained savings."
        "default_risk_factor" -> "Reflects the contribution drag caused by expected missed payments or defaults."
        "cycle_slippage_factor" -> "Accounts for timing delays when members pay later than the planned cycle."
        "withdrawal_pressure_factor" -> "Represents how early withdrawals can reduce the amount left to compound."
        "inflation_safety_factor" -> "Adds a protective margin above baseline inflation assumptions for prudence."
        "survivor_uncertainty_factor" -> "Captures uncertainty around survivor or dependant-related payout assumptions."
        "horizon_compounding_factor" -> "Shows the extra effect of compounding across the full planning horizon."
        "goal_stretch_ratio" -> "Measures how ambitious the target goal is relative to the current contribution base."
        "growth_conservatism_factor" -> "Keeps growth assumptions realistic by applying a conservative planning bias."
        else -> "Explains how this multiplier changes the final viability projection."
    }
}

