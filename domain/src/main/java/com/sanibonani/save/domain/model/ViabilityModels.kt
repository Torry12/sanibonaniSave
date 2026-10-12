package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

@Serializable
@Parcelize
data class ActuarialSettings(
    // Burial / Protection Risk Factors
    val mortalityRatePerThousand: Double = 8.2,
    val safetyLoadingPct: Double = 35.0,
    val claimReadinessFactor: Double = 1.25,
    val adminCostPerMemberMonthly: Money = Money("25.00"),
    val targetSolvencyRatio: Double = 1.30,
    val averageBeneficiariesPerMember: Double = 1.0,

    // ROSCA / Rotational Risk Factors
    val expectedDefaultRatePct: Double = 5.0,
    val latePenaltyPct: Double = 10.0,
    val cycleSlippageFactor: Double = 1.10,

    // Investment Club / Growth Risk Factors
    val targetAnnualReturnPct: Double = 12.0,
    val volatilityHaircutPct: Double = 15.0,

    // Stokvel / Collection Risk Factors
    val collectionEfficiencyPct: Double = 95.0,
    val festivePayoutPressureFactor: Double = 1.15,

    // Emergency Fund / Liquidity Risk Factors
    val emergencyReserveCoverageMonths: Double = 3.0,
    val withdrawalPressureFactor: Double = 1.05,

    // Tontine / Survivor Risk Factors
    val survivorUncertaintyFactor: Double = 1.05,
    val horizonCompoundingFactor: Double = 0.97,

    // Community Savings / General Risk Factors
    val growthConservatismFactor: Double = 1.03
) : Parcelable

@Serializable
@Parcelize
data class ViabilityPlan(
    val initialContribution: Money = Money.ZERO,
    val suggestedMonthlyContribution: Money = Money.ZERO,
    val projectedValue: Money = Money.ZERO,
    val isViable: Boolean = true,
    val goalAmount: Money = Money.ZERO,
    val periodMonths: Int = 12,
    val messages: List<String> = emptyList(),
    val requiredMonthlyToMeetGoal: Money = Money.ZERO,
    val shortfallAmount: Money = Money.ZERO,
    val breakEvenMonths: Int = 0,
    val compoundedProjectedValue: Money = Money.ZERO,
    val optimisticProjectedValue: Money = Money.ZERO,
    val pessimisticProjectedValue: Money = Money.ZERO,
    val activeMemberRatio: Double = 1.0,
    val inflationAdjustmentFactor: Double = 1.0,
    val projectionRetentionFactor: Double = 1.0,
    val claimReadinessFactor: Double = 1.0,
    val mortalityBufferFactor: Double = 1.0,
    val reserveAdequacyFactor: Double = 1.0,
    val marketReturnPremiumFactor: Double = 1.0,
    val volatilityHaircutFactor: Double = 1.0,
    val collectionEfficiencyFactor: Double = 1.0,
    val festivePayoutPressureFactor: Double = 1.0,
    val defaultRiskFactor: Double = 1.0,
    val cycleSlippageFactor: Double = 1.0,
    val withdrawalPressureFactor: Double = 1.0,
    val inflationSafetyFactor: Double = 1.0,
    val survivorUncertaintyFactor: Double = 1.0,
    val horizonCompoundingFactor: Double = 1.0,
    val goalStretchRatio: Double = 1.0,
    val growthConservatismFactor: Double = 1.0,
    val appliedActuarialSettings: ActuarialSettings = ActuarialSettings(),
    val calculationBreakdowns: List<ActuarialFormulaBreakdown> = emptyList()
) : Parcelable
