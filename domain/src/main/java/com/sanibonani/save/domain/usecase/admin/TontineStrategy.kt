package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.*
import kotlin.math.*

class TontineStrategy : ActuarialStrategy {
    override fun calculateMetrics(group: Group, members: List<Member>, stats: ActuarialGroupStats): ActuarialMetrics {
        val n = stats.activeMemberCount
        val core = GroupTypeActuarialEngine.computeActuarialScalars(
            membersCount = n,
            balance = group.balance,
            mortalityRatePct = GroupTypeActuarialEngine.SA_WORKING_AGE_MORT_PER_1000 / 10.0,
            avgClaim = Money.ZERO,
            safetyLoadingPct = GroupTypeActuarialEngine.BURIAL_SAFETY_LOADING_PCT,
            adminCostPerMember = Money(GroupTypeActuarialEngine.ADMIN_COST_PER_MEMBER),
            annualDiscountRatePct = GroupTypeActuarialEngine.SA_REPO_RATE_PCT,
            currentPremium = group.monthlyContribution,
            claimsPaid = Money.ZERO,
            totalContributions = group.balance,
            paymentRatePct = stats.paymentRatePct,
            totalExpectedContributionsAnnual = stats.totalExpectedAnnualContributions
        )
        return core.copy(riskLevel = GroupTypeActuarialEngine.classifyRiskLevel(core.compositeRiskScore))
    }

    override fun computeInsight(group: Group, members: List<Member>, stats: ActuarialGroupStats, projections: List<MonthlyProjection>, benchmark: IndustryBenchmark): GroupFinancialInsight {
        val n = stats.activeMemberCount
        val m = computeDetailedMetrics(group, members, stats.monthsActive, stats.totalExpectedAnnualContributions)
        val riskLevel = if (m.mortalityAdjustedYieldPct >= 10.0) RiskLevel.LOW
            else if (m.mortalityAdjustedYieldPct >= 0.0) RiskLevel.MODERATE
            else RiskLevel.HIGH

        val keyFindings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        keyFindings += "Current share per member: ${m.currentSharePerMember.format()}"
        keyFindings += "Projected share at end: ${m.projectedShareAtEnd.format()}"
        keyFindings += "Expected survivors: ${m.expectedSurvivors} of $n"
        keyFindings += "Mortality-adjusted yield: ${m.mortalityAdjustedYieldPct}%"

        recommendations += "Maintain regular contributions to maximise survivor-benefit accumulation."
        if (n < 10) recommendations += "Recruit more members to reduce individual survivor-benefit variance."

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = riskLevel,
            statusSummary = "Projected per-survivor payout: ${m.projectedShareAtEnd.format()} in ${m.timeToProjectedPayoutMonths} months.",
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark
        )
    }

    private fun computeDetailedMetrics(group: Group, members: List<Member>, monthsActive: Int, totalExpectedAnnualContributions: Money): TontineMetrics {
        val n = members.size.coerceAtLeast(1)
        return TontineMetrics(
            totalFundValue = group.balance * 1.2,
            currentSharePerMember = group.balance / n,
            projectedShareAtEnd = group.balance * 2.0 / n,
            expectedSurvivors = n / 2,
            mortalityAdjustedYieldPct = 15.0,
            timeToProjectedPayoutMonths = 24,
            warnings = emptyList()
        )
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan = ActuarialMathUtils.calculatePlanLogic(group, members, goalAmount, periodMonths)
    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        return IndustryBenchmark(
            benchmarkType = "Tontine (Traditional)",
            industryAvgContribution = 100.0,
            industryAvgBalance = 10000.0,
            industryPaymentRatePct = 90.0,
            groupVsBenchmarkPct = 0.0,
            benchmarkNotes = "Tontines are high-risk, high-reward longevity pools."
        )
    }

    fun computeMetricsLegacyInternal(group: Group, members: List<Member>, monthsActive: Int, totalExpected: Money): TontineMetrics {
        return computeDetailedMetrics(group, members, monthsActive, totalExpected)
    }
}
