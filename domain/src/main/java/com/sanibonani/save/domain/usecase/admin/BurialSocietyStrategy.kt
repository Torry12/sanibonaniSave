package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.*
import kotlin.math.*

class BurialSocietyStrategy : ActuarialStrategy {
    override fun calculateMetrics(group: Group, members: List<Member>, stats: ActuarialGroupStats): ActuarialMetrics {
        val n = stats.activeMemberCount
        val balance = group.balance
        val q = GroupTypeActuarialEngine.SA_WORKING_AGE_MORT_PER_1000 / 1000.0
        val benefit = if (group.goalAmount.isPositive) group.goalAmount else Money(GroupTypeActuarialEngine.SA_AVG_FUNERAL_COST)
        val estimatedAnnualClaims = benefit * (n.toDouble() * q)

        val core = GroupTypeActuarialEngine.computeActuarialScalars(
            membersCount = n,
            balance = balance,
            mortalityRatePct = GroupTypeActuarialEngine.SA_WORKING_AGE_MORT_PER_1000 / 10.0,
            avgClaim = if (n > 0) estimatedAnnualClaims / n.toDouble() else Money.ZERO,
            safetyLoadingPct = GroupTypeActuarialEngine.BURIAL_SAFETY_LOADING_PCT,
            adminCostPerMember = Money(GroupTypeActuarialEngine.ADMIN_COST_PER_MEMBER),
            annualDiscountRatePct = GroupTypeActuarialEngine.SA_REPO_RATE_PCT,
            currentPremium = if (n > 0) stats.totalExpectedAnnualContributions / 12.0 / n.toDouble() else group.monthlyContribution,
            claimsPaid = estimatedAnnualClaims * 0.1,
            totalContributions = balance,
            paymentRatePct = stats.paymentRatePct,
            totalExpectedContributionsAnnual = stats.totalExpectedAnnualContributions
        )

        val burialMetrics = computeDetailedMetrics(group, members, stats.monthsActive, stats.totalExpectedAnnualContributions)
        return core.copy(
            riskLevel = GroupTypeActuarialEngine.classifyRiskLevel(core.compositeRiskScore),
            warnings = burialMetrics.warnings
        )
    }

    override fun computeInsight(
        group: Group,
        members: List<Member>,
        stats: ActuarialGroupStats,
        projections: List<MonthlyProjection>,
        benchmark: IndustryBenchmark
    ): GroupFinancialInsight {
        val n = stats.activeMemberCount
        val m = computeDetailedMetrics(group, members, stats.monthsActive, stats.totalExpectedAnnualContributions)
        val riskLevel = GroupTypeActuarialEngine.classifyRiskLevel(buildBurialRiskScore(m))

        val keyFindings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        keyFindings += "Solvency ratio: ${m.solvencyRatio} (FSB min: ${GroupTypeActuarialEngine.BURIAL_MIN_SOLVENCY_RATIO})"
        keyFindings += "Reserve coverage: ${m.reserveCoverageMonths} months"
        keyFindings += "Benefit adequacy: ${m.benefitAdequacyPct}% of market funeral cost"
        keyFindings += "Expected claims next year: ${m.projectedClaimsNextYear.format()}"

        if (!m.isSolvent) recommendations += "Increase contributions by at least ${(m.grossPremium - group.monthlyContribution).max(Money.ZERO).format()} to restore solvency."
        if (!m.isReserveAdequate) recommendations += "Build reserve to ${m.requiredReserveAmount.format()} (3-month claims buffer)."
        if (!m.isCapitalAdequate) recommendations += "Raise capital to ${m.minimumCapitalAmount.format()} to meet FSB 15% adequacy threshold."
        if (m.benefitAdequacyPct < 80.0) {
            recommendations += "Consider increasing death benefit closer to ${Money(GroupTypeActuarialEngine.SA_AVG_FUNERAL_COST).format()} (market rate)."
        } else {
            recommendations += "Contributions and benefits are well balanced — maintain current trajectory."
        }

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = riskLevel,
            statusSummary = buildBurialStatus(m, riskLevel),
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark
        )
    }

    private fun computeDetailedMetrics(group: Group, members: List<Member>, monthsActive: Int, totalExpectedAnnualContributions: Money): BurialSocietyActuarialMetrics {
        val n = members.size.coerceAtLeast(1)
        val balance = group.balance
        val q = GroupTypeActuarialEngine.SA_WORKING_AGE_MORT_PER_1000 / 1000.0
        val benefitAmount = if (group.goalAmount.isPositive) group.goalAmount else Money(GroupTypeActuarialEngine.SA_AVG_FUNERAL_COST)

        val expectedAnnualClaimsCount = n * q
        val expectedAnnualClaimsValue = benefitAmount * expectedAnnualClaimsCount

        val i = ActuarialStrategy.SA_REPO_RATE_PCT / 100.0
        val d = i / (1.0 + i)
        val apv = expectedAnnualClaimsValue * (1.0 - d)

        val purePremium = if (n > 0) expectedAnnualClaimsValue / n else Money.ZERO
        val safetyLoading = purePremium * (GroupTypeActuarialEngine.BURIAL_SAFETY_LOADING_PCT / 100.0)
        val adminCost = group.monthlyContribution * 0.05
        val safetyLoadedPremium = purePremium + safetyLoading
        val grossPremium = safetyLoadedPremium + adminCost

        val periodYears = group.periodMonths.coerceAtLeast(12).toDouble() / 12.0
        val expectedFutureClaimsNpv = apv * periodYears
        val solvencyRatio = if (expectedFutureClaimsNpv.isPositive) (balance + totalExpectedAnnualContributions) / expectedFutureClaimsNpv else GroupTypeActuarialEngine.BURIAL_MIN_SOLVENCY_RATIO * 2.0

        val minimumCapitalAmount = expectedAnnualClaimsValue * 0.15
        val capitalAdequacyPct = if (expectedAnnualClaimsValue.isPositive) (balance / expectedAnnualClaimsValue) * 100.0 else 100.0
        val monthlyExpectedClaims = expectedAnnualClaimsValue / 12.0
        val requiredReserveAmount = monthlyExpectedClaims * 3.0
        val reserveCoverageMonths = if (monthlyExpectedClaims.isPositive) balance / monthlyExpectedClaims else 12.0

        val benefitAdequacyPct = (benefitAmount.toDouble() / GroupTypeActuarialEngine.SA_AVG_FUNERAL_COST) * 100.0
        val claimRatePerThousand = if (n > 0) (expectedAnnualClaimsCount / n) * 1000.0 else 0.0

        val isCapitalAdequate = balance >= minimumCapitalAmount
        val isReserveAdequate = reserveCoverageMonths >= 3.0
        val isSolvent = solvencyRatio >= GroupTypeActuarialEngine.BURIAL_MIN_SOLVENCY_RATIO

        val adminCostTotal = adminCost * n * 12.0
        val annualNetFlow = totalExpectedAnnualContributions - expectedAnnualClaimsValue - adminCostTotal
        val yearsToInsolvency = if (annualNetFlow.isPositive || annualNetFlow.isZero) -1.0 else (-balance / annualNetFlow).coerceAtLeast(0.0)

        val warnings = buildList {
            if (!isSolvent) add("⚠️ Below FSB minimum solvency ratio (${ActuarialStrategy.r(solvencyRatio)} < ${GroupTypeActuarialEngine.BURIAL_MIN_SOLVENCY_RATIO}).")
            if (!isCapitalAdequate) add("⚠️ Capital adequacy below 15% regulatory minimum.")
            if (!isReserveAdequate) add("⚠️ Reserve covers ${ActuarialStrategy.r(reserveCoverageMonths)} months (min: 3 months).")
            if (benefitAdequacyPct < 60.0) add("⚠️ Benefit is below 60% of average SA funeral cost.")
            if (yearsToInsolvency in 0.0..3.0) add("⚠️ Projected insolvency in ${ActuarialStrategy.r(yearsToInsolvency)} years.")
        }

        return BurialSocietyActuarialMetrics(
            expectedAnnualClaimsCount = ActuarialStrategy.r(expectedAnnualClaimsCount),
            purePremium = purePremium,
            safetyLoadedPremium = safetyLoadedPremium,
            grossPremium = grossPremium,
            actuarialPresentValue = apv,
            solvencyRatio = ActuarialStrategy.r(solvencyRatio),
            capitalAdequacyPct = ActuarialStrategy.r(capitalAdequacyPct),
            requiredReserveAmount = requiredReserveAmount,
            reserveCoverageMonths = ActuarialStrategy.r(reserveCoverageMonths),
            minimumCapitalAmount = minimumCapitalAmount,
            isSolvent = isSolvent,
            isCapitalAdequate = isCapitalAdequate,
            isReserveAdequate = isReserveAdequate,
            benefitAdequacyPct = ActuarialStrategy.r(benefitAdequacyPct),
            claimRatePerThousand = ActuarialStrategy.r(claimRatePerThousand),
            projectedClaimsNextYear = expectedAnnualClaimsValue,
            yearsToInsolvencyAtCurrentRate = yearsToInsolvency,
            warnings = warnings
        )
    }

    private fun buildBurialRiskScore(m: BurialSocietyActuarialMetrics): Int {
        var score = 100
        if (!m.isSolvent)          score -= 40
        if (!m.isCapitalAdequate)  score -= 25
        if (!m.isReserveAdequate)  score -= 20
        if (m.benefitAdequacyPct < 60.0) score -= 15
        return score.coerceIn(0, 100)
    }

    private fun buildBurialStatus(m: BurialSocietyActuarialMetrics, riskLevel: RiskLevel): String {
        val solvencyLabel = if (m.isSolvent) "✅ Solvent" else "❌ Below solvency"
        val reserveLabel  = if (m.isReserveAdequate) "✅ Reserve adequate" else "⚠️ Reserve low"
        return "$solvencyLabel | $reserveLabel | Ratio: ${m.solvencyRatio} | ${riskLevel.displayName}"
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan = ActuarialMathUtils.calculatePlanLogic(group, members, goalAmount, periodMonths)
    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        val avg = 350.0
        val groupAvg = if (memberCount > 0) group.monthlyContribution.toDouble() else 0.0
        val vsBenchmark = if (avg > 0) ((groupAvg / avg) - 1.0) * 100.0 else 0.0
        return IndustryBenchmark(
            benchmarkType = "Burial Society (ASISA Standard)",
            industryAvgContribution = avg,
            industryAvgBalance = 45000.0,
            industryPaymentRatePct = 88.0,
            groupVsBenchmarkPct = ActuarialStrategy.r(vsBenchmark),
            benchmarkNotes = "Industry average burial society premium is R350/month with 88% collection efficiency."
        )
    }

    fun computeMetricsLegacyInternal(group: Group, members: List<Member>, monthsActive: Int, totalExpected: Money): BurialSocietyActuarialMetrics {
        return computeDetailedMetrics(group, members, monthsActive, totalExpected)
    }
}
