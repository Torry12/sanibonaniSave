package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.*
import kotlin.math.*

object ActuarialMathUtils {

    fun calculatePlanLogic(
        group: Group,
        members: List<Member>,
        goalAmount: Money,
        periodMonths: Int
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
            ((ActuarialStrategy.SA_CPI_INFLATION_PCT / 100.0) * (effectivePeriod / 12.0))
        val investmentMonthlyRate = ActuarialStrategy.SA_REPO_RATE_PCT / 100.0 / 12.0
        val investmentAnnuityFactor =
            ((1.0 + investmentMonthlyRate).pow(effectivePeriod.toDouble()) - 1.0) / investmentMonthlyRate
        val tontineMonthlyRate = 10.0 / 100.0 / 12.0
        val tontineAnnuityFactor =
            ((1.0 + tontineMonthlyRate).pow(effectivePeriod.toDouble()) - 1.0) / tontineMonthlyRate
        val emergencyCoverageTarget = group.monthlyContribution * n * 6.0

        val defaultGoal = when (group.type) {
            GroupType.BURIAL_SOCIETY -> if (group.goalAmount.isPositive) group.goalAmount else Money(30000.0)
            GroupType.STOKVEL -> if (group.goalAmount.isPositive) group.goalAmount else (currentBalance + (group.monthlyContribution * n * effectivePeriod))
            GroupType.ROSCA -> if (group.goalAmount.isPositive) group.goalAmount else (group.monthlyContribution * n)
            GroupType.INVESTMENT_CLUB -> if (group.goalAmount.isPositive) group.goalAmount else (currentBalance * (1.0 + investmentMonthlyRate).pow(effectivePeriod.toDouble()) + (group.monthlyContribution * n * effectivePeriod))
            GroupType.EMERGENCY_FUND -> if (group.goalAmount >= emergencyCoverageTarget) group.goalAmount else emergencyCoverageTarget
            GroupType.TONTINE -> if (group.goalAmount.isPositive) group.goalAmount else (currentBalance * (1.0 + tontineMonthlyRate).pow(effectivePeriod.toDouble()) + (group.monthlyContribution * n * effectivePeriod))
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
                mortalityBufferFactor = 1.0 + (8.2 / 1000.0)
                val reserveCoverageMonths = if (n > 0 && group.monthlyContribution.isPositive) {
                    (currentBalance.toDouble() / (group.monthlyContribution.toDouble() * n)).coerceAtLeast(0.0)
                } else 0.0
                reserveAdequacyFactor = if (reserveCoverageMonths >= 3.0) 1.0 else 1.15
                claimReadinessFactor = 1.25
                messages += "Burial Society factors: claim_readiness=${r(claimReadinessFactor)}, mortality_buffer=${r(mortalityBufferFactor)}, reserve_adequacy=${r(reserveAdequacyFactor)}."
                baseMonthly * claimReadinessFactor * mortalityBufferFactor * reserveAdequacyFactor
            }
            GroupType.INVESTMENT_CLUB -> {
                val openingBalanceFutureValue = currentBalance * (1.0 + investmentMonthlyRate).pow(effectivePeriod.toDouble())
                val futureGap = (effectiveGoal - openingBalanceFutureValue).max(Money.ZERO)
                marketReturnPremiumFactor = 0.93
                volatilityHaircutFactor = if (effectivePeriod < 18) 1.10 else 1.04
                messages += "Investment Club factors: annual_rate=${ActuarialStrategy.SA_REPO_RATE_PCT}%, return_premium=${r(marketReturnPremiumFactor)}, volatility_haircut=${r(volatilityHaircutFactor)}."
                if (futureGap.isZero) Money.ZERO else (futureGap / (n * investmentAnnuityFactor)) * marketReturnPremiumFactor * volatilityHaircutFactor
            }
            GroupType.STOKVEL -> {
                collectionEfficiencyFactor = if (group.allowPartialPayment) 0.98 else 1.0
                festivePayoutPressureFactor = if (effectivePeriod >= 11) 1.03 else 1.0
                messages += "Stokvel factors: collection_efficiency=${r(collectionEfficiencyFactor)}, payout_pressure=${r(festivePayoutPressureFactor)}."
                baseMonthly * (festivePayoutPressureFactor / collectionEfficiencyFactor)
            }
            GroupType.ROSCA -> {
                val baseDefault = 2.5 / 100.0
                val participationPenalty = (1.0 - activeRatio).coerceAtLeast(0.0) * 0.30
                val cycleLengthPenalty = (n - 12).coerceAtLeast(0) * 0.003
                defaultRiskFactor = 1.0 + baseDefault + participationPenalty + cycleLengthPenalty
                cycleSlippageFactor = when {
                    activeRatio >= 0.95 -> 1.0
                    activeRatio >= 0.85 -> 1.03
                    else -> 1.07
                }
                messages += "ROSCA factors: default_risk=${r(defaultRiskFactor)}, cycle_slippage=${r(cycleSlippageFactor)}, active_ratio=${r(activeRatio * 100.0)}%."
                ((effectiveGoal / n) * defaultRiskFactor * cycleSlippageFactor).max(Money.ZERO)
            }
            GroupType.EMERGENCY_FUND -> {
                withdrawalPressureFactor = if (group.autoSuspendAfter <= 1) 1.10 else 1.03
                inflationSafetyFactor = inflationAdjustmentFactor
                messages += "Emergency Fund factors: withdrawal_pressure=${r(withdrawalPressureFactor)}, inflation_safety=${r(inflationSafetyFactor)}."
                baseMonthly * withdrawalPressureFactor * inflationSafetyFactor
            }
            GroupType.TONTINE -> {
                survivorUncertaintyFactor = if (n < 10) 1.08 else 1.03
                horizonCompoundingFactor = if (effectivePeriod >= 24) 0.97 else 1.0
                messages += "Tontine factors: survivor_uncertainty=${r(survivorUncertaintyFactor)}, horizon_compounding=${r(horizonCompoundingFactor)}."
                val openingBalanceFutureValue = currentBalance * (1.0 + tontineMonthlyRate).pow(effectivePeriod.toDouble())
                val futureGap = (effectiveGoal - openingBalanceFutureValue).max(Money.ZERO)
                if (futureGap.isZero) Money.ZERO else (futureGap / (n * tontineAnnuityFactor)) * survivorUncertaintyFactor * horizonCompoundingFactor
            }
            else -> {
                goalStretchRatio = if (group.balance.isPositive) (effectiveGoal / group.balance).coerceAtLeast(1.0) else 1.2
                growthConservatismFactor = if (goalStretchRatio > 4.0) 1.10 else 1.03
                messages += "Savings factors: goal_stretch_ratio=${r(goalStretchRatio)}, conservatism=${r(growthConservatismFactor)}."
                baseMonthly * growthConservatismFactor
            }
        }

        val initialContribution = if (group.type == GroupType.BURIAL_SOCIETY) suggestedMonthly * 2.0 else suggestedMonthly

        val projectionRetentionFactor = when (group.type) {
            GroupType.ROSCA -> (0.99 - ((1.0 - activeRatio).coerceAtLeast(0.0) * 0.08)).coerceIn(0.90, 0.99)
            GroupType.BURIAL_SOCIETY, GroupType.EMERGENCY_FUND -> 0.99
            GroupType.STOKVEL -> if (group.allowPartialPayment) 0.98 else 1.0
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
            GroupType.INVESTMENT_CLUB -> {
                val openingBalanceFutureValue = openingBalanceCredit * (1.0 + investmentMonthlyRate).pow(effectivePeriod.toDouble())
                openingBalanceFutureValue + (periodicContribution * (investmentAnnuityFactor * projectionRetentionFactor))
            }
            GroupType.TONTINE -> {
                val openingBalanceFutureValue = openingBalanceCredit * (1.0 + tontineMonthlyRate).pow(effectivePeriod.toDouble())
                openingBalanceFutureValue + (periodicContribution * (tontineAnnuityFactor * projectionRetentionFactor))
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
            GroupType.INVESTMENT_CLUB -> {
                val openingBalanceFutureValue = openingBalanceCredit * (1.0 + investmentMonthlyRate).pow(effectivePeriod.toDouble())
                val futureGap = (effectiveGoal - openingBalanceFutureValue).max(Money.ZERO)
                if (n > 0 && investmentAnnuityFactor > 0.0) futureGap / (n * investmentAnnuityFactor) else suggestedMonthly
            }
            GroupType.TONTINE -> {
                val openingBalanceFutureValue = openingBalanceCredit * (1.0 + tontineMonthlyRate).pow(effectivePeriod.toDouble())
                val futureGap = (effectiveGoal - openingBalanceFutureValue).max(Money.ZERO)
                if (n > 0 && tontineAnnuityFactor > 0.0) futureGap / (n * tontineAnnuityFactor) else suggestedMonthly
            }
            else -> if (n > 0 && effectivePeriod > 0) targetGap / (n * effectivePeriod) else suggestedMonthly
        }
        val breakEvenMonths = when {
            targetGap.isZero -> 0
            group.type == GroupType.ROSCA && periodicContribution.isPositive -> 1
            periodicContribution.isPositive -> ceil(targetGap.toDouble() / (periodicContribution.toDouble() * projectionRetentionFactor.coerceAtLeast(0.01))).toInt()
            else -> effectivePeriod
        }

        if (n < 5) messages += "Warning: Low member count ($n) increases individual burden."

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
            growthConservatismFactor       = r(growthConservatismFactor)
        )
    }

    private fun r(v: Double): Double = (round(v * 100.0) / 100.0)
}
