package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.*
import kotlin.math.*

object ActuarialMathUtils {

    fun calculatePlanLogic(
        group: Group,
        members: List<Member>,
        goalAmount: Money,
        periodMonths: Int,
        actuarialSettings: ActuarialSettings = ActuarialSettings()
    ): ViabilityPlan {
        val activeList = members.filter {
            it.status == MemberStatus.ACTIVE || it.status == MemberStatus.PROBATION
        }
        val n = max(activeList.size, 1)
        val messages = mutableListOf<String>()

        val currentBalance = group.balance.max(Money.ZERO)
        val effectivePeriod = if (periodMonths > 0) periodMonths else max(group.periodMonths, 1)
        val activeRatio = if (members.isEmpty()) 1.0 else n.toDouble() / members.size.toDouble()
        val inflationAdjustmentFactor = 1.0 +
            ((ActuarialConstants.SA_CPI_INFLATION_PCT / 100.0) * (effectivePeriod.toDouble() / 12.0))
        val investmentMonthlyRate = ActuarialConstants.SA_RISK_FREE_RATE_PCT / 100.0 / 12.0
        val investmentAnnuityFactor =
            ((1.0 + investmentMonthlyRate).pow(effectivePeriod.toDouble()) - 1.0) / investmentMonthlyRate
        val emergencyCoverageTarget = group.monthlyContribution * n * actuarialSettings.emergencyReserveCoverageMonths.toInt()

        val defaultGoal = when (group.type) {
            GroupType.BURIAL_SOCIETY -> if (group.goalAmount.isPositive) group.goalAmount else Money(30000)
            GroupType.STOKVEL -> if (group.goalAmount.isPositive) group.goalAmount else (currentBalance + (group.monthlyContribution * n * effectivePeriod))
            GroupType.ROSCA -> if (group.goalAmount.isPositive) group.goalAmount else (group.monthlyContribution * n)
            GroupType.INVESTMENT_CLUB -> if (group.goalAmount.isPositive) group.goalAmount else (currentBalance * (1.0 + investmentMonthlyRate).pow(effectivePeriod.toDouble()) + (group.monthlyContribution * n * effectivePeriod))
            GroupType.EMERGENCY_FUND -> if (group.goalAmount >= emergencyCoverageTarget) group.goalAmount else emergencyCoverageTarget
            GroupType.HYBRID_SAVINGS_GROUP -> if (group.goalAmount.isPositive) group.goalAmount else (currentBalance * (1.0 + investmentMonthlyRate).pow(effectivePeriod.toDouble()) + (group.monthlyContribution * n * effectivePeriod))
            else -> if (group.goalAmount.isPositive) group.goalAmount else (currentBalance + (group.monthlyContribution * n * effectivePeriod))
        }
        val requestedGoal = if (goalAmount.isPositive) goalAmount else defaultGoal
        val effectiveGoal = when (group.type) {
            GroupType.EMERGENCY_FUND -> requestedGoal.max(emergencyCoverageTarget)
            GroupType.ROSCA -> requestedGoal.max(group.monthlyContribution * n)
            else -> requestedGoal
        }
        val openingBalanceCredit = when (group.type) {
            GroupType.ROSCA -> Money.ZERO
            else -> currentBalance
        }
        val targetGap = (effectiveGoal - openingBalanceCredit).max(Money.ZERO)
        val baseMonthly = if (n > 0 && effectivePeriod > 0) targetGap / (n * effectivePeriod) else Money.ZERO

        var claimReadinessFactor = 1.0
        var mortalityBufferFactor = 1.0
        var reserveAdequacyFactor = 1.0
        var marketReturnPremiumFactor = 1.0
        var volatilityHaircutFactor = 1.0
        var collectionEfficiencyFactor = 1.0
        var festivePayoutPressureFactor = 1.0
        var defaultRiskFactor = 1.0
        var cycleSlippageFactor = 1.0
        var withdrawalPressureFactor = 1.0
        var inflationSafetyFactor = 1.0
        var survivorUncertaintyFactor = 1.0
        var horizonCompoundingFactor = 1.0
        var goalStretchRatio = 1.0
        var growthConservatismFactor = 1.0

        val suggestedMonthly = when (group.type) {
            GroupType.BURIAL_SOCIETY -> {
                mortalityBufferFactor = 1.0 + (actuarialSettings.mortalityRatePerThousand / 1000.0)
                val totalMemberMonthly = group.monthlyContribution * n.toDouble()
                val reserveCoverageMonths = if (n > 0 && totalMemberMonthly.isPositive) {
                    currentBalance.ratio(totalMemberMonthly)
                } else 0.0
                reserveAdequacyFactor = if (reserveCoverageMonths >= actuarialSettings.emergencyReserveCoverageMonths) 1.0 else (actuarialSettings.targetSolvencyRatio / 1.13).coerceAtLeast(1.0)
                claimReadinessFactor = actuarialSettings.claimReadinessFactor
                val safetyLoadingFactor = 1.0 + (actuarialSettings.safetyLoadingPct / 100.0)

                messages += "Burial Society factors: claim_readiness=${r(claimReadinessFactor)}, mortality_rate=${actuarialSettings.mortalityRatePerThousand}/1000, safety_loading=${actuarialSettings.safetyLoadingPct}%, target_solvency=${actuarialSettings.targetSolvencyRatio}x, admin_fee=${actuarialSettings.adminCostPerMemberMonthly.format()}."
                (baseMonthly * claimReadinessFactor * mortalityBufferFactor * reserveAdequacyFactor * safetyLoadingFactor) + actuarialSettings.adminCostPerMemberMonthly
            }
            GroupType.INVESTMENT_CLUB -> {
                val invRate = actuarialSettings.targetAnnualReturnPct / 100.0 / 12.0
                val invAnnuity = if (invRate > 0) ((1.0 + invRate).pow(effectivePeriod.toDouble()) - 1.0) / invRate else effectivePeriod.toDouble()
                val openingBalanceFutureValue = currentBalance * (1.0 + invRate).pow(effectivePeriod.toDouble())
                val futureGap = (effectiveGoal - openingBalanceFutureValue).max(Money.ZERO)
                marketReturnPremiumFactor = 1.0 - (actuarialSettings.targetAnnualReturnPct / 100.0 * 0.15)
                volatilityHaircutFactor = 1.0 + (actuarialSettings.volatilityHaircutPct / 100.0 * 0.1)
                messages += "Investment Club factors: target_cagr=${actuarialSettings.targetAnnualReturnPct}%, return_premium=${r(marketReturnPremiumFactor)}, volatility_haircut=${r(volatilityHaircutFactor)}, admin_fee=${actuarialSettings.adminCostPerMemberMonthly.format()}."
                if (futureGap.isZero) actuarialSettings.adminCostPerMemberMonthly else (futureGap / (n * invAnnuity)) * marketReturnPremiumFactor * volatilityHaircutFactor + actuarialSettings.adminCostPerMemberMonthly
            }
            GroupType.STOKVEL -> {
                val collectionEff = (actuarialSettings.collectionEfficiencyPct / 100.0).coerceIn(0.5, 1.0)
                collectionEfficiencyFactor = collectionEff
                festivePayoutPressureFactor = actuarialSettings.festivePayoutPressureFactor
                messages += "Stokvel factors: collection_efficiency=${r(collectionEfficiencyFactor * 100)}%, payout_pressure=${r(festivePayoutPressureFactor)}."
                (baseMonthly * festivePayoutPressureFactor) / collectionEfficiencyFactor
            }
            GroupType.ROSCA -> {
                val baseDefault = actuarialSettings.expectedDefaultRatePct / 100.0
                val participationPenalty = (1.0 - activeRatio).coerceAtLeast(0.0) * (actuarialSettings.latePenaltyPct / 100.0)
                val cycleLengthPenalty = (n - 12).coerceAtLeast(0) * 0.003
                defaultRiskFactor = 1.0 + baseDefault + participationPenalty + cycleLengthPenalty
                cycleSlippageFactor = actuarialSettings.cycleSlippageFactor * when {
                    activeRatio >= 0.95 -> 1.0
                    activeRatio >= 0.85 -> 1.03
                    else -> 1.07
                }
                messages += "ROSCA factors: default_risk=${r(defaultRiskFactor)}, cycle_slippage=${r(cycleSlippageFactor)}, active_ratio=${r(activeRatio * 100.0)}%."
                ((effectiveGoal / n) * defaultRiskFactor * cycleSlippageFactor).max(Money.ZERO)
            }
            GroupType.EMERGENCY_FUND -> {
                withdrawalPressureFactor = actuarialSettings.withdrawalPressureFactor
                inflationSafetyFactor = inflationAdjustmentFactor
                val targetMonths = actuarialSettings.emergencyReserveCoverageMonths.toInt()
                messages += "Emergency Fund factors: withdrawal_pressure=${r(withdrawalPressureFactor)}, target_months=$targetMonths, inflation_safety=${r(inflationSafetyFactor)}."
                baseMonthly * withdrawalPressureFactor * inflationSafetyFactor
            }
            GroupType.HYBRID_SAVINGS_GROUP -> {
                val hybridRate = 12.0 / 100.0 / 12.0
                val hybridAnnuity = ((1.0 + hybridRate).pow(effectivePeriod.toDouble()) - 1.0) / hybridRate
                val openingBalanceFutureValue = currentBalance * (1.0 + hybridRate).pow(effectivePeriod.toDouble())
                val futureGap = (effectiveGoal - openingBalanceFutureValue).max(Money.ZERO)
                if (futureGap.isZero) Money.ZERO else (futureGap / (n * hybridAnnuity))
            }
            else -> {
                goalStretchRatio = if (group.balance.isPositive) (effectiveGoal.ratio(group.balance)).coerceAtLeast(1.0) else 1.2
                growthConservatismFactor = actuarialSettings.growthConservatismFactor
                messages += "Savings factors: goal_stretch_ratio=${r(goalStretchRatio)}, conservatism=${r(growthConservatismFactor)}."
                baseMonthly * growthConservatismFactor
            }
        }

        val initialContribution = if (group.type == GroupType.BURIAL_SOCIETY) suggestedMonthly * 2.0 else suggestedMonthly

        val projectionRetentionFactor = when (group.type) {
            GroupType.ROSCA -> (0.99 - ((1.0 - activeRatio).coerceAtLeast(0.0) * 0.08)).coerceIn(0.90, 0.99)
            GroupType.BURIAL_SOCIETY, GroupType.EMERGENCY_FUND -> 0.99
            GroupType.STOKVEL -> if (group.allowPartialPayment) (actuarialSettings.collectionEfficiencyPct / 100.0).coerceIn(0.90, 1.0) else 1.0
            else -> 1.0
        }

        val periodicContribution = suggestedMonthly * n
        val roscaCycles = ceil(effectivePeriod.toDouble() / n).toInt().coerceAtLeast(1)
        val projectedValue = when (group.type) {
            GroupType.ROSCA -> periodicContribution * (roscaCycles.toDouble() * projectionRetentionFactor)
            else -> openingBalanceCredit + (periodicContribution * (effectivePeriod.toDouble() * projectionRetentionFactor))
        }

        val compoundedProjectedValue = when (group.type) {
            GroupType.ROSCA -> projectedValue
            GroupType.INVESTMENT_CLUB, GroupType.HYBRID_SAVINGS_GROUP -> {
                val openingBalanceFutureValue = openingBalanceCredit * (1.0 + investmentMonthlyRate).pow(effectivePeriod.toDouble())
                openingBalanceFutureValue + (periodicContribution * (investmentAnnuityFactor * projectionRetentionFactor))
            }
            else -> {
                val openingBalanceFutureValue = openingBalanceCredit * (1.0 + investmentMonthlyRate).pow(effectivePeriod.toDouble())
                openingBalanceFutureValue + (periodicContribution * (investmentAnnuityFactor * projectionRetentionFactor))
            }
        }

        val optimistic  = when (group.type) {
            GroupType.ROSCA -> periodicContribution * (1.15 * projectionRetentionFactor)
            else -> openingBalanceCredit + (periodicContribution * (1.15 * effectivePeriod.toDouble() * projectionRetentionFactor))
        }
        val pessimistic = when (group.type) {
            GroupType.ROSCA -> periodicContribution * (0.85 * projectionRetentionFactor)
            else -> openingBalanceCredit + (periodicContribution * (0.85 * effectivePeriod.toDouble() * projectionRetentionFactor))
        }
        val shortfall   = (effectiveGoal - projectedValue).max(Money.ZERO)
        val requiredMonthly = when (group.type) {
            GroupType.ROSCA -> if (n > 0) effectiveGoal / n else suggestedMonthly
            GroupType.INVESTMENT_CLUB, GroupType.HYBRID_SAVINGS_GROUP -> {
                val openingBalanceFutureValue = openingBalanceCredit * (1.0 + investmentMonthlyRate).pow(effectivePeriod.toDouble())
                val futureGap = (effectiveGoal - openingBalanceFutureValue).max(Money.ZERO)
                if (n > 0 && investmentAnnuityFactor > 0.0) futureGap / (n * investmentAnnuityFactor) else suggestedMonthly
            }
            else -> if (n > 0 && effectivePeriod > 0) targetGap / (n * effectivePeriod) else suggestedMonthly
        }
        val breakEvenMonths = when {
            targetGap.isZero -> 0
            group.type == GroupType.ROSCA && periodicContribution.isPositive -> 1
            periodicContribution.isPositive -> ceil(targetGap.ratio(periodicContribution * projectionRetentionFactor.coerceAtLeast(0.01))).toInt()
            else -> effectivePeriod
        }

        if (n < 5) messages += "Warning: Low member count ($n) increases individual burden."

        // ── Calculation Breakdowns ──
        val breakdowns = mutableListOf<ActuarialFormulaBreakdown>()
        breakdowns += ActuarialFormulaBreakdown(
            title = "1. Active Participation Ratio",
            formula = "Ratio = N_active / TotalMembers",
            resultText = "${r(activeRatio * 100.0)}% active",
            explanation = "The percentage of members currently paying into the fund. High ratios reduce the burden per member.",
            status = if (activeRatio >= 0.90) "OK" else "WARNING"
        )
        if (inflationAdjustmentFactor > 1.0) {
            breakdowns += ActuarialFormulaBreakdown(
                title = "2. Inflation Target Adjustment",
                formula = "Target = Goal × (1 + Inflation)^Years",
                resultText = "x${r(inflationAdjustmentFactor)} multiplier",
                explanation = "Adjusts your goal for 5.5% annual inflation (SA CPI) to ensure future purchasing power.",
                status = "OK"
            )
        }
        breakdowns += ActuarialFormulaBreakdown(
            title = "3. Retention & Churn Factor",
            formula = "Retention = 1 - EstimatedLeakage",
            resultText = "${r(projectionRetentionFactor * 100.0)}% efficiency",
            explanation = "Models expected missed payments or partial collections based on group type history.",
            status = if (projectionRetentionFactor >= 0.95) "OK" else "WARNING"
        )

        when (group.type) {
            GroupType.BURIAL_SOCIETY -> {
                breakdowns += ActuarialFormulaBreakdown(
                    title = "4. Prudence & Mortality Buffers",
                    formula = "Loading = ClaimReadiness × MortalityBuffer × Reserves",
                    resultText = "x${r(claimReadinessFactor * mortalityBufferFactor * reserveAdequacyFactor)} safety multiplier",
                    explanation = "Aggregated safety margin to ensure the fund remains solvent during high-mortality months.",
                    status = "OK"
                )
            }
            GroupType.INVESTMENT_CLUB -> {
                breakdowns += ActuarialFormulaBreakdown(
                    title = "4. Volatility Risk Haircut",
                    formula = "Return = MarketPremium × VolatilityReduction",
                    resultText = "x${r(marketReturnPremiumFactor * volatilityHaircutFactor)} conservative bias",
                    explanation = "Reduces projected investment returns to account for market fluctuations and risk-free rate floors.",
                    status = "OK"
                )
            }
            else -> {}
        }

        return ViabilityPlan(
            initialContribution            = initialContribution,
            suggestedMonthlyContribution   = suggestedMonthly,
            projectedValue                 = projectedValue,
            isViable                       = when (group.type) {
                GroupType.ROSCA -> projectedValue >= effectiveGoal
                else -> compoundedProjectedValue >= effectiveGoal
            },
            goalAmount                     = effectiveGoal,
            periodMonths                   = effectivePeriod,
            messages                       = messages,
            requiredMonthlyToMeetGoal      = requiredMonthly,
            shortfallAmount                = shortfall,
            breakEvenMonths                = breakEvenMonths,
            compoundedProjectedValue       = compoundedProjectedValue,
            optimisticProjectedValue       = optimistic,
            pessimisticProjectedValue      = pessimistic,
            activeMemberRatio              = r(activeRatio),
            inflationAdjustmentFactor      = r(inflationAdjustmentFactor),
            projectionRetentionFactor      = r(projectionRetentionFactor),
            claimReadinessFactor           = r(claimReadinessFactor),
            mortalityBufferFactor          = r(mortalityBufferFactor),
            reserveAdequacyFactor          = r(reserveAdequacyFactor),
            marketReturnPremiumFactor      = r(marketReturnPremiumFactor),
            volatilityHaircutFactor        = r(volatilityHaircutFactor),
            collectionEfficiencyFactor     = r(collectionEfficiencyFactor),
            festivePayoutPressureFactor    = r(festivePayoutPressureFactor),
            defaultRiskFactor              = r(defaultRiskFactor),
            cycleSlippageFactor            = r(cycleSlippageFactor),
            withdrawalPressureFactor       = r(withdrawalPressureFactor),
            inflationSafetyFactor          = r(inflationSafetyFactor),
            survivorUncertaintyFactor      = r(survivorUncertaintyFactor),
            horizonCompoundingFactor       = r(horizonCompoundingFactor),
            goalStretchRatio               = r(goalStretchRatio),
            growthConservatismFactor       = r(growthConservatismFactor),
            appliedActuarialSettings       = actuarialSettings,
            calculationBreakdowns          = breakdowns
        )
    }

    private fun r(v: Double): Double = (round(v * 100.0) / 100.0)
}
