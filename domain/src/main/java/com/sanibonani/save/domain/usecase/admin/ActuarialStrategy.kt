package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.system.sortRoscaParticipants
import kotlin.math.*

/**
 * Base strategy for group-type specific actuarial logic.
 */
interface ActuarialStrategy {
    fun computeMetrics(
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpectedAnnualContributions: Money
    ): Any // Type specific metrics or ActuarialMetrics

    fun calculateViabilityPlan(
        group: Group,
        members: List<Member>,
        goalAmount: Money,
        periodMonths: Int
    ): ViabilityPlan

    fun getIndustryBenchmark(
        group: Group,
        memberCount: Int,
        balance: Money
    ): IndustryBenchmark

    companion object {
        // SA Macro-economic Constants (2025)
        const val SA_REPO_RATE_PCT = 8.25
        const val SA_PRIME_RATE_PCT = 11.75
        const val SA_CPI_INFLATION_PCT = 5.5
        const val SA_RISK_FREE_RATE_PCT = 9.5
        const val SA_EQUITY_RISK_PREMIUM_PCT = 6.5

        /** Round to 2 decimal places. */
        fun r(v: Double): Double = (round(v * 100.0) / 100.0)
    }
}

// ══════════════════════════════════════════════════════════════════════
//  BURIAL SOCIETY STRATEGY
// ══════════════════════════════════════════════════════════════════════

class BurialSocietyStrategy : ActuarialStrategy {
    private val SA_WORKING_AGE_MORT_PER_1000 = 8.2
    private val SA_AVG_FUNERAL_COST = 30_000.0
    private val MIN_SOLVENCY_RATIO = 1.30
    private val MIN_RESERVE_MONTHS = 3.0
    private val CAPITAL_ADEQUACY_PCT = 15.0
    private val SAFETY_LOADING_PCT = 35.0

    override fun computeMetrics(
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpectedAnnualContributions: Money
    ): BurialSocietyMetrics {
        val n = members.size.coerceAtLeast(1)
        val balance = group.balance
        val q = SA_WORKING_AGE_MORT_PER_1000 / 1000.0
        val benefitAmount = if (group.goalAmount.isPositive) group.goalAmount else Money(SA_AVG_FUNERAL_COST)

        val expectedAnnualClaimsCount = n * q
        val expectedAnnualClaimsValue = benefitAmount * expectedAnnualClaimsCount

        val i = ActuarialStrategy.SA_REPO_RATE_PCT / 100.0
        val d = i / (1.0 + i)
        val apv = expectedAnnualClaimsValue * (1.0 - d)

        val purePremium = if (n > 0) expectedAnnualClaimsValue / n else Money.ZERO
        val safetyLoading = purePremium * (SAFETY_LOADING_PCT / 100.0)
        val adminCost = group.monthlyContribution * 0.05
        val safetyLoadedPremium = purePremium + safetyLoading
        val grossPremium = safetyLoadedPremium + adminCost

        val periodYears = group.periodMonths.coerceAtLeast(12).toDouble() / 12.0
        val expectedFutureClaimsNpv = apv * periodYears
        val solvencyRatio = if (expectedFutureClaimsNpv.isPositive)
            (balance + totalExpectedAnnualContributions) / expectedFutureClaimsNpv
        else
            MIN_SOLVENCY_RATIO * 2.0

        val minimumCapitalAmount = expectedAnnualClaimsValue * (CAPITAL_ADEQUACY_PCT / 100.0)
        val capitalAdequacyPct = if (expectedAnnualClaimsValue.isPositive)
            (balance / expectedAnnualClaimsValue) * 100.0
        else 100.0

        val monthlyExpectedClaims = expectedAnnualClaimsValue / 12.0
        val requiredReserveAmount = monthlyExpectedClaims * MIN_RESERVE_MONTHS
        val reserveCoverageMonths = if (monthlyExpectedClaims.isPositive)
            balance / monthlyExpectedClaims
        else
            MIN_RESERVE_MONTHS * 4.0

        val benefitAdequacyPct = (benefitAmount.toDouble() / SA_AVG_FUNERAL_COST) * 100.0
        val claimRatePerThousand = if (n > 0) (expectedAnnualClaimsCount / n) * 1000.0 else 0.0

        val isCapitalAdequate = balance >= minimumCapitalAmount
        val isReserveAdequate = reserveCoverageMonths >= MIN_RESERVE_MONTHS
        val isSolvent = solvencyRatio >= MIN_SOLVENCY_RATIO

        val adminCostTotal = adminCost * n * 12.0
        val annualNetFlow = totalExpectedAnnualContributions - expectedAnnualClaimsValue - adminCostTotal
        val yearsToInsolvency = if (annualNetFlow >= 0) -1.0
        else (-balance / annualNetFlow).coerceAtLeast(0.0)

        val warnings = buildList {
            if (!isSolvent)
                add("⚠️ Below FSB minimum solvency ratio (${ActuarialStrategy.r(solvencyRatio)} < $MIN_SOLVENCY_RATIO).")
            if (!isCapitalAdequate)
                add("⚠️ Capital adequacy below 15% regulatory minimum. Required: ${minimumCapitalAmount.format()}.")
            if (!isReserveAdequate)
                add("⚠️ Reserve covers ${ActuarialStrategy.r(reserveCoverageMonths)} months (min: $MIN_RESERVE_MONTHS months).")
            if (benefitAdequacyPct < 60.0)
                add("⚠️ Benefit ${benefitAmount.format()} is below 60% of average SA funeral cost (R${SA_AVG_FUNERAL_COST.toInt()}).")
            if (yearsToInsolvency in 0.0..3.0)
                add("⚠️ Projected insolvency in ${ActuarialStrategy.r(yearsToInsolvency)} years at current rate. Increase contributions.")
        }

        return BurialSocietyMetrics(
            expectedAnnualClaimsCount = ActuarialStrategy.r(expectedAnnualClaimsCount),
            purePremium               = purePremium,
            safetyLoadedPremium       = safetyLoadedPremium,
            grossPremium              = grossPremium,
            actuarialPresentValue     = apv,
            solvencyRatio             = ActuarialStrategy.r(solvencyRatio),
            capitalAdequacyPct        = ActuarialStrategy.r(capitalAdequacyPct),
            requiredReserveAmount     = requiredReserveAmount,
            reserveCoverageMonths     = ActuarialStrategy.r(reserveCoverageMonths),
            minimumCapitalAmount      = minimumCapitalAmount,
            isSolvent                 = isSolvent,
            isCapitalAdequate         = isCapitalAdequate,
            isReserveAdequate         = isReserveAdequate,
            benefitAdequacyPct        = ActuarialStrategy.r(benefitAdequacyPct),
            claimRatePerThousand      = ActuarialStrategy.r(claimRatePerThousand),
            projectedClaimsNextYear   = expectedAnnualClaimsValue,
            yearsToInsolvencyAtCurrentRate = yearsToInsolvency,
            warnings                  = warnings
        )
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan {
        // Reuse common math but with burial societies factors
        return CommonActuarialMath.calculatePlan(group, members, goalAmount, periodMonths)
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        val n = memberCount.coerceAtLeast(1)
        return IndustryBenchmark(
            benchmarkType            = "FSCA Friendly Society Standard",
            industryAvgContribution  = 250.0,
            industryAvgBalance       = n * 1_500.0,
            industryPaymentRatePct   = 93.0,
            groupVsBenchmarkPct      = (balance.toDouble() / n - 1_500.0) / 1_500.0 * 100.0,
            benchmarkNotes           = "FSB solvency min: 1.30. Reserve: 3 months claims. Capital adequacy: 15%."
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
//  STOKVEL STRATEGY
// ══════════════════════════════════════════════════════════════════════

class StokvelStrategy : ActuarialStrategy {
    private val AVG_MEMBERS = 38.0
    private val AVG_MONTHLY_CONTRIB = 150.0

    override fun computeMetrics(
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpectedAnnualContributions: Money
    ): GroupTypeActuarialEngine.StokvelActuarialMetrics {
        val n = members.size.coerceAtLeast(1)
        val balance = group.balance
        val monthInYear = (monthsActive % 12).let { if (it == 0) 12 else it }
        val monthsToTarget = (12 - monthInYear).let { if (it == 0) 0 else it }

        val futureContribs = group.monthlyContribution * n * monthsToTarget
        val totalProjectedFund = balance + futureContribs
        val projectedPayoutPerMember = totalProjectedFund / n

        val annualContributionTarget = group.monthlyContribution * n * 12
        val potMilestonePct = if (annualContributionTarget.isPositive)
            min(100.0, balance.toDouble() / annualContributionTarget.toDouble() * 100.0)
        else 0.0

        val monthlyExpected = totalExpectedAnnualContributions / 12.0
        val expectedByNow = monthlyExpected * monthInYear.coerceAtLeast(1)
        val paymentCompliancePct = if (expectedByNow.isPositive)
            min(100.0, balance.toDouble() / expectedByNow.toDouble() * 100.0)
        else 100.0

        val nasasaAvgAnnualPayout = AVG_MONTHLY_CONTRIB * 12.0
        val benchmarkVsNasasa = (projectedPayoutPerMember.toDouble() / nasasaAvgAnnualPayout - 1.0) * 100.0

        val savingsScore = (min(100.0, potMilestonePct) * 0.4 + min(100.0, paymentCompliancePct) * 0.6)
            .toInt().coerceIn(0, 100)

        val warnings = buildList {
            if (paymentCompliancePct < 80.0)
                add("⚠️ Payment compliance at ${ActuarialStrategy.r(paymentCompliancePct)}%. Target: 95%+.")
            if (potMilestonePct < 30.0 && monthsActive > 6)
                add("⚠️ Fund is only ${ActuarialStrategy.r(potMilestonePct)}% toward annual target after $monthsActive months.")
            if (group.monthlyContribution < AVG_MONTHLY_CONTRIB)
                add("💡 Monthly contribution ${group.monthlyContribution.format()} is below the NASASA average (R${AVG_MONTHLY_CONTRIB.toInt()}). Consider increasing.")
            if (n > 50)
                add("💡 Large stokvel ($n members). NASASA recommends sub-groups or a committee structure.")
        }

        return GroupTypeActuarialEngine.StokvelActuarialMetrics(
            totalProjectedFund         = totalProjectedFund,
            projectedPayoutPerMember   = projectedPayoutPerMember,
            potMilestonePct            = ActuarialStrategy.r(potMilestonePct),
            paymentCompliancePct       = ActuarialStrategy.r(paymentCompliancePct),
            benchmarkVsNasasaAvg       = ActuarialStrategy.r(benchmarkVsNasasa),
            savingsEfficiencyScore     = savingsScore,
            targetPayoutMonth          = 12,
            monthsToTarget             = monthsToTarget,
            annualContributionTarget   = annualContributionTarget,
            warnings                   = warnings
        )
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan {
        return CommonActuarialMath.calculatePlan(group, members, goalAmount, periodMonths)
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        return IndustryBenchmark(
            benchmarkType            = "NASASA Stokvel Standard (2024)",
            industryAvgContribution  = AVG_MONTHLY_CONTRIB,
            industryAvgBalance       = AVG_MEMBERS * AVG_MONTHLY_CONTRIB * 6.0,
            industryPaymentRatePct   = 95.0,
            groupVsBenchmarkPct      = (group.monthlyContribution.toDouble() / AVG_MONTHLY_CONTRIB - 1.0) * 100.0,
            benchmarkNotes           = "NASASA 2024: 11.5M members, 820K stokvels. Avg: 38 members @ R150/month."
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
//  ROSCA STRATEGY
// ══════════════════════════════════════════════════════════════════════

class RoscaStrategy : ActuarialStrategy {
    private val DEFAULT_RISK_BASE_PCT = 2.5

    override fun computeMetrics(
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpectedAnnualContributions: Money
    ): GroupTypeActuarialEngine.RoscaActuarialMetrics {
        val participants = members.filter { it.status != MemberStatus.SUSPENDED }
        val n = participants.size.coerceAtLeast(1)
        val r = ActuarialStrategy.SA_PRIME_RATE_PCT / 100.0 / 12.0
        val monthlyPot = group.monthlyContribution * n

        val cycleLength = n
        val currentCycleMonth = if (monthsActive <= 0) 1 else ((monthsActive - 1) % n) + 1

        val paidOutInCycle = (currentCycleMonth - 1).coerceIn(0, n)
        val potCompletionPct = if (n > 0) paidOutInCycle.toDouble() / n * 100.0 else 0.0

        val pvSavingAlone = group.monthlyContribution * (1.0 - (1.0 + r).pow(-n.toDouble())) / r
        val pvEarly = monthlyPot / (1.0 + r)
        val welfareGainEarly = (pvEarly - pvSavingAlone).max(Money.ZERO)
        val pvLate = monthlyPot / (1.0 + r).pow(n.toDouble())
        val welfareGainLate = (pvLate - pvSavingAlone).max(Money.ZERO)

        val baseDefault = DEFAULT_RISK_BASE_PCT / 100.0
        val activeRatio = if (members.isEmpty()) 1.0 else participants.size.toDouble() / members.size.toDouble()
        val paidFraction = potCompletionPct / 100.0
        val distressedMembers = members.count {
            it.status == MemberStatus.PENDING_PAYMENT || it.status == MemberStatus.SUSPENDED
        }
        val delinquencyRatio = if (members.isNotEmpty()) distressedMembers.toDouble() / members.size else 0.0
        val pDefault = (baseDefault + (paidFraction * 0.04) + (delinquencyRatio * 0.08)).coerceIn(0.0, 0.35)
        val defaultRiskScore = (pDefault * 1000.0).toInt().coerceIn(0, 100)
        val expectedDefaultsPerCycle = n * pDefault
        val cycleCompletionProbability = (1.0 - pDefault).pow(n.toDouble()) * 100.0

        val sortedMembers = sortRoscaParticipants(group, participants)
        val currentPayoutMember = sortedMembers.getOrNull(currentCycleMonth - 1)?.fullName ?: "Unknown"
        val nextPayoutMember = sortedMembers.getOrNull(currentCycleMonth % n)?.fullName ?: "Unknown"

        val warnings = buildList {
            if (n < 4)
                add("⚠️ Very small ROSCA ($n members). High cycle-completion volatility.")
            if (defaultRiskScore > 50)
                add("⚠️ Elevated default risk score ($defaultRiskScore/100). Earlier receivers may drop out.")
            if (cycleCompletionProbability < 80.0)
                add("⚠️ Estimated cycle-completion probability: ${ActuarialStrategy.r(cycleCompletionProbability)}%.")
            if (n > 20)
                add("💡 Large ROSCA ($n members = ${n}-month cycle). Consider splitting into sub-groups.")
            if (participants.size != members.size)
                add("⚠️ ${members.size - participants.size} suspended member(s) excluded from the payout queue.")
        }

        return GroupTypeActuarialEngine.RoscaActuarialMetrics(
            monthlyPot                 = monthlyPot,
            cycleLength                = cycleLength,
            currentCycleMonth          = currentCycleMonth,
            potCompletionPct           = ActuarialStrategy.r(potCompletionPct),
            welfareGainEarlyReceiver   = welfareGainEarly,
            welfareGainLateReceiver    = welfareGainLate,
            defaultRiskScore           = defaultRiskScore,
            expectedDefaultsPerCycle   = ActuarialStrategy.r(expectedDefaultsPerCycle),
            cycleCompletionProbability = ActuarialStrategy.r(cycleCompletionProbability),
            currentPayoutMember        = currentPayoutMember,
            nextPayoutMember           = nextPayoutMember,
            warnings                   = warnings
        )
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan {
        return CommonActuarialMath.calculatePlan(group, members, goalAmount, periodMonths)
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        val n = memberCount.coerceAtLeast(1)
        return IndustryBenchmark(
            benchmarkType            = "ROSCA Best Practice (NASASA / Academic)",
            industryAvgContribution  = 300.0,
            industryAvgBalance       = n * group.monthlyContribution.toDouble() * 0.5,
            industryPaymentRatePct   = 92.0,
            groupVsBenchmarkPct      = 0.0,
            benchmarkNotes           = "Ideal: 6-12 members. Cycle completion rate target: 90%+."
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
//  INVESTMENT CLUB STRATEGY
// ══════════════════════════════════════════════════════════════════════

class InvestmentClubStrategy : ActuarialStrategy {
    override fun computeMetrics(
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpectedAnnualContributions: Money
    ): GroupTypeActuarialEngine.InvestmentClubMetrics {
        val n = members.size.coerceAtLeast(1)
        val balance = group.balance
        val totalContributed = members.fold(Money.ZERO) { acc, m -> acc + m.totalPaid }
        val periodYears = (monthsActive.coerceAtLeast(1)).toDouble() / 12.0

        val totalReturnPct = if (totalContributed.isPositive)
            ((balance.toDouble() - totalContributed.toDouble()) / totalContributed.toDouble()) * 100.0
        else 0.0

        val annualisedReturn = if (periodYears > 0.0 && totalContributed.isPositive && balance.isPositive)
            (balance / totalContributed).pow(1.0 / periodYears) - 1.0
        else 0.0

        val historicalConfidence = min(1.0, periodYears / 3.0)
        val marketCagr = (ActuarialStrategy.SA_RISK_FREE_RATE_PCT + ActuarialStrategy.SA_EQUITY_RISK_PREMIUM_PCT) / 100.0
        val projectedCagr = annualisedReturn * historicalConfidence + marketCagr * (1.0 - historicalConfidence)

        val navPerUnit = if (totalContributed.isPositive) balance / totalContributed.toDouble() else Money(1.0)
        val capitalAtRiskPerMember = (balance / n) * 0.30

        val rfr = ActuarialStrategy.SA_RISK_FREE_RATE_PCT / 100.0
        val estimatedVol = 0.15
        val sharpeRatio = (annualisedReturn - rfr) / estimatedVol

        val remainingMonths = (group.periodMonths - monthsActive).coerceAtLeast(0)
        val r = projectedCagr / 12.0
        val pmt = group.monthlyContribution * n
        val projectedFv = if (remainingMonths > 0 && r > 0.0)
            balance * (1.0 + r).pow(remainingMonths.toDouble()) +
                pmt * ((1.0 + r).pow(remainingMonths.toDouble()) - 1.0) / r
        else
            balance + pmt * remainingMonths

        val goalAmount = if (group.goalAmount.isPositive) group.goalAmount else balance * 1.5
        val requiresReturnPct = if (remainingMonths > 0 && balance.isPositive)
            ((goalAmount / balance).pow(12.0 / remainingMonths.toDouble()) - 1.0) * 100.0
        else 0.0

        val warnings = buildList {
            if (annualisedReturn < ActuarialStrategy.SA_CPI_INFLATION_PCT / 100.0 && periodYears > 1.0)
                add("📉 Real return (${ActuarialStrategy.r(annualisedReturn * 100)}%) is below SA inflation (${ActuarialStrategy.SA_CPI_INFLATION_PCT}%). Improve diversification.")
            if (sharpeRatio < 0.0 && periodYears > 1.0)
                add("📉 Negative Sharpe ratio — risk-adjusted return is below the risk-free rate.")
            if (capitalAtRiskPerMember > group.monthlyContribution * 3)
                add("⚠️ Capital at risk per member (${capitalAtRiskPerMember.format()}) exceeds 3 months of contributions.")
            if (projectedFv < goalAmount)
                add("📊 Current trajectory may fall short of goal ${goalAmount.format()}. Required return: ${ActuarialStrategy.r(requiresReturnPct)}% p.a.")
        }

        return GroupTypeActuarialEngine.InvestmentClubMetrics(
            netAssetValue           = balance,
            navPerUnit              = navPerUnit,
            totalReturnPct          = ActuarialStrategy.r(totalReturnPct),
            annualisedReturnPct     = ActuarialStrategy.r(annualisedReturn * 100.0),
            projectedCagrPct        = ActuarialStrategy.r(projectedCagr * 100.0),
            capitalAtRiskPerMember  = capitalAtRiskPerMember,
            sharpeRatio             = ActuarialStrategy.r(sharpeRatio),
            projectedFvAtCagr       = projectedFv,
            requiresReturnPct       = ActuarialStrategy.r(requiresReturnPct),
            isOnTrackForGoal        = projectedFv >= goalAmount,
            warnings                = warnings
        )
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan {
        return CommonActuarialMath.calculatePlan(group, members, goalAmount, periodMonths)
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        val n = memberCount.coerceAtLeast(1)
        return IndustryBenchmark(
            benchmarkType            = "JSE Investment Club / NASAA Standard",
            industryAvgContribution  = 500.0,
            industryAvgBalance       = n * 5_000.0,
            industryPaymentRatePct   = 90.0,
            groupVsBenchmarkPct      = (balance.toDouble() / n - 5_000.0) / 5_000.0 * 100.0,
            benchmarkNotes           = "Target CAGR: 10-14% p.a. (JSE All-Share). Beat inflation + 5%."
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
//  EMERGENCY FUND STRATEGY
// ══════════════════════════════════════════════════════════════════════

class EmergencyFundStrategy : ActuarialStrategy {
    private val TARGET_MONTHS = 6.0

    override fun computeMetrics(
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpectedAnnualContributions: Money
    ): GroupTypeActuarialEngine.EmergencyFundMetrics {
        val n = members.size.coerceAtLeast(1)
        val balance = group.balance
        val estimatedMonthlyExpenses = group.monthlyContribution * n * 1.5

        val coverageMonths = if (estimatedMonthlyExpenses.isPositive) balance / estimatedMonthlyExpenses else 0.0
        val targetBalance = estimatedMonthlyExpenses * TARGET_MONTHS
        val coverageGap = (targetBalance - balance).max(Money.ZERO)

        val monthlyContribTotal = group.monthlyContribution * n
        val monthsToTarget = if (monthlyContribTotal.isPositive && coverageGap.isPositive)
            ceil(coverageGap.toDouble() / monthlyContribTotal.toDouble()).toInt()
        else 0

        val replenishmentRatePct = if (targetBalance.isPositive)
            monthlyContribTotal.toDouble() / targetBalance.toDouble() * 100.0
        else 0.0

        val warnings = buildList {
            if (coverageMonths < 3.0)
                add("⚠️ Emergency fund covers only ${ActuarialStrategy.r(coverageMonths)} months (min recommended: 3 months).")
            if (coverageMonths < TARGET_MONTHS)
                add("📊 ${coverageGap.format()} still needed to reach ${TARGET_MONTHS.toInt()}-month coverage target (~$monthsToTarget more months).")
        }

        return GroupTypeActuarialEngine.EmergencyFundMetrics(
            coverageMonths       = ActuarialStrategy.r(coverageMonths),
            targetCoverageMonths = TARGET_MONTHS,
            coverageGap          = coverageGap,
            replenishmentRatePct = ActuarialStrategy.r(replenishmentRatePct),
            isMeetingTarget      = coverageMonths >= TARGET_MONTHS,
            monthsToTarget       = monthsToTarget,
            warnings             = warnings
        )
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan {
        return CommonActuarialMath.calculatePlan(group, members, goalAmount, periodMonths)
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        val n = memberCount.coerceAtLeast(1)
        return IndustryBenchmark(
            benchmarkType            = "SA Financial Planning Standard",
            industryAvgContribution  = 200.0,
            industryAvgBalance       = n * group.monthlyContribution.toDouble() * TARGET_MONTHS,
            industryPaymentRatePct   = 90.0,
            groupVsBenchmarkPct      = run {
                val target = TARGET_MONTHS * group.monthlyContribution.toDouble() * n
                if (target > 0.0) (balance.toDouble() / target - 1.0) * 100.0 else 0.0
            },
            benchmarkNotes           = "Standard: 3-6 months of pooled group expenses. Target: 6 months."
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
//  TONTINE STRATEGY
// ══════════════════════════════════════════════════════════════════════

class TontineStrategy : ActuarialStrategy {
    private val MORTALITY_RATE_PER_1000 = 8.2
    private val DISCOUNT_RATE_PCT = 10.0

    override fun computeMetrics(
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpectedAnnualContributions: Money
    ): GroupTypeActuarialEngine.TontineMetrics {
        val n = members.size.coerceAtLeast(1)
        val balance = group.balance
        val remainingMonths = (group.periodMonths - monthsActive).coerceAtLeast(0)
        val yearsRemaining = remainingMonths.toDouble() / 12.0
        val q = MORTALITY_RATE_PER_1000 / 1000.0

        val expectedSurvivors = max(1, (n * (1.0 - q).pow(yearsRemaining * 12.0)).toInt())

        val r = DISCOUNT_RATE_PCT / 100.0 / 12.0
        val pmt = group.monthlyContribution * n
        val projectedFund = if (remainingMonths > 0)
            balance * (1.0 + r).pow(remainingMonths.toDouble()) +
                pmt * ((1.0 + r).pow(remainingMonths.toDouble()) - 1.0) / r
        else
            balance + pmt * remainingMonths

        val currentSharePerMember = balance / n
        val projectedShareAtEnd = projectedFund / expectedSurvivors
        val totalContribPerMember = group.monthlyContribution * group.periodMonths.coerceAtLeast(1)
        val mortalityAdjustedYield = if (totalContribPerMember.isPositive)
            (projectedShareAtEnd.toDouble() / totalContribPerMember.toDouble() - 1.0) * 100.0
        else 0.0

        val warnings = buildList {
            if (n < 10)
                add("⚠️ Tontine with fewer than 10 members has high survivor-benefit variance.")
            if (mortalityAdjustedYield < 0.0)
                add("⚠️ Projected per-member payout is below total contributions — consider increasing contributions.")
        }

        return GroupTypeActuarialEngine.TontineMetrics(
            totalFundValue                = projectedFund,
            currentSharePerMember         = currentSharePerMember,
            projectedShareAtEnd           = projectedShareAtEnd,
            expectedSurvivors             = expectedSurvivors,
            mortalityAdjustedYieldPct     = ActuarialStrategy.r(mortalityAdjustedYield),
            timeToProjectedPayoutMonths   = remainingMonths,
            warnings                      = warnings
        )
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan {
        return CommonActuarialMath.calculatePlan(group, members, goalAmount, periodMonths)
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        val n = memberCount.coerceAtLeast(1)
        return IndustryBenchmark(
            benchmarkType            = "Tontine Actuarial Standard",
            industryAvgContribution  = 300.0,
            industryAvgBalance       = n * 3_000.0,
            industryPaymentRatePct   = 95.0,
            groupVsBenchmarkPct      = (balance.toDouble() / n - 3_000.0) / 3_000.0 * 100.0,
            benchmarkNotes           = "Minimum 10 members recommended. Survivor benefit grows over time."
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
//  DEFAULT / COMMUNITY SAVINGS STRATEGY
// ══════════════════════════════════════════════════════════════════════

class DefaultActuarialStrategy : ActuarialStrategy {
    private val SAVINGS_RATE_PCT = 6.0

    override fun computeMetrics(
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpectedAnnualContributions: Money
    ): GroupTypeActuarialEngine.CommunitySavingsMetrics {
        val n = members.size.coerceAtLeast(1)
        val balance = group.balance
        val savingsPerMember = balance / n
        val annualDividend = balance * (SAVINGS_RATE_PCT / 100.0)

        val expectedByNow = group.monthlyContribution * n * monthsActive.coerceAtLeast(1)
        val growthRatePct = if (expectedByNow.isPositive)
            (balance.toDouble() - expectedByNow.toDouble()) / expectedByNow.toDouble() * 100.0
        else 0.0

        val goalAmount = if (group.goalAmount.isPositive) group.goalAmount else group.monthlyContribution * n * 12
        val goalProgressPct = if (goalAmount.isPositive) min(100.0, balance.toDouble() / goalAmount.toDouble() * 100.0) else 100.0

        val monthlyContribTotal = group.monthlyContribution * n
        val projectedGoalDesc = if (balance >= goalAmount) {
            "Goal reached ✅"
        } else if (monthlyContribTotal.isPositive) {
            val needed = ceil((goalAmount.toDouble() - balance.toDouble()) / monthlyContribTotal.toDouble()).toInt()
            "~$needed more months"
        } else {
            "Increase contributions to reach goal"
        }

        val warnings = buildList {
            if (growthRatePct < -10.0)
                add("⚠️ Fund is below expected savings trajectory by ${ActuarialStrategy.r(abs(growthRatePct))}%.")
            if (goalProgressPct < 20.0 && monthsActive > 3)
                add("⚠️ Fund at ${ActuarialStrategy.r(goalProgressPct)}% of goal after $monthsActive months. Review contribution schedule.")
        }

        return GroupTypeActuarialEngine.CommunitySavingsMetrics(
            savingsPerMember              = savingsPerMember,
            annualDividendProjection      = annualDividend,
            growthRatePct                 = ActuarialStrategy.r(growthRatePct),
            goalProgressPct               = ActuarialStrategy.r(goalProgressPct),
            projectedGoalReachDescription = projectedGoalDesc,
            warnings                      = warnings
        )
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan {
        return CommonActuarialMath.calculatePlan(group, members, goalAmount, periodMonths)
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        val n = memberCount.coerceAtLeast(1)
        return IndustryBenchmark(
            benchmarkType            = "General SA Savings Group Standard",
            industryAvgContribution  = 200.0,
            industryAvgBalance       = n * 2_000.0,
            industryPaymentRatePct   = 90.0,
            groupVsBenchmarkPct      = (balance.toDouble() / n - 2_000.0) / 2_000.0.coerceAtLeast(1.0) * 100.0,
            benchmarkNotes           = "SA National Treasury small savings group benchmark."
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
//  COMMON MATH (Internal)
// ══════════════════════════════════════════════════════════════════════

object CommonActuarialMath {
    fun calculatePlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan {
        // This is a complex logic that I'll keep as-is for now but encapsulated here
        // to avoid duplication across strategies while keeping them polymorphic.
        // It uses the same procedural logic from the original engine but as a shared utility.
        return GroupTypeActuarialEngine.calculateViabilityPlanProcedural(group, members, goalAmount, periodMonths)
    }
}
