package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.*
import kotlin.math.*

class EmergencyFundStrategy : ActuarialStrategy {
    override fun calculateMetrics(group: Group, members: List<Member>, stats: ActuarialGroupStats): ActuarialMetrics {
        val n = stats.activeMemberCount
        val core = GroupTypeActuarialEngine.computeActuarialScalars(
            membersCount = n,
            balance = group.balance,
            mortalityRatePct = GroupTypeActuarialEngine.SA_WORKING_AGE_MORT_PER_1000 / 10.0,
            avgClaim = Money(n * 10000.0) / n.toDouble(),
            safetyLoadingPct = GroupTypeActuarialEngine.BURIAL_SAFETY_LOADING_PCT,
            adminCostPerMember = Money(GroupTypeActuarialEngine.ADMIN_COST_PER_MEMBER),
            annualDiscountRatePct = GroupTypeActuarialEngine.SA_REPO_RATE_PCT,
            currentPremium = if (n > 0) stats.totalExpectedAnnualContributions / 12.0 / n.toDouble() else group.monthlyContribution,
            claimsPaid = Money(n * 1000.0),
            totalContributions = group.balance,
            paymentRatePct = stats.paymentRatePct,
            totalExpectedContributionsAnnual = stats.totalExpectedAnnualContributions
        )
        return core.copy(riskLevel = GroupTypeActuarialEngine.classifyRiskLevel(core.compositeRiskScore))
    }

    override fun computeInsight(group: Group, members: List<Member>, stats: ActuarialGroupStats, projections: List<MonthlyProjection>, benchmark: IndustryBenchmark): GroupFinancialInsight {
        val n = stats.activeMemberCount
        val m = computeDetailedMetrics(group, members, stats.monthsActive, stats.totalExpectedAnnualContributions)
        val riskLevel = when {
            m.isMeetingTarget -> RiskLevel.LOW
            m.coverageMonths >= 3.0 -> RiskLevel.MODERATE
            else -> RiskLevel.HIGH
        }

        val keyFindings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        keyFindings += "Coverage: ${m.coverageMonths} months (target: ${m.targetCoverageMonths.toInt()} months)"
        keyFindings += "Gap to target: ${m.coverageGap.format()}"
        keyFindings += "Replenishment rate: ${m.replenishmentRatePct}%/month toward target"

        recommendations += if (m.isMeetingTarget) "✅ Emergency fund meets the 6-month coverage standard."
            else "Build fund to ${m.coverageGap.format()} more to reach ${m.targetCoverageMonths.toInt()}-month target (~${m.monthsToTarget} months)."

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = riskLevel,
            statusSummary = "${if (m.isMeetingTarget) "✅" else "⚠️"} Coverage: ${m.coverageMonths}/${m.targetCoverageMonths.toInt()} months (${if (m.isMeetingTarget) "Target met" else "Building"}).",
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark
        )
    }

    private fun computeDetailedMetrics(group: Group, members: List<Member>, monthsActive: Int, totalExpectedAnnualContributions: Money): EmergencyFundMetrics {
        val n = members.size.coerceAtLeast(1)
        val balance = group.balance
        val monthlyExpenses = group.monthlyContribution * n * 1.5
        val coverageMonths = if (monthlyExpenses.isPositive) balance / monthlyExpenses else 0.0

        return EmergencyFundMetrics(
            coverageMonths = ActuarialStrategy.r(coverageMonths),
            targetCoverageMonths = 6.0,
            coverageGap = (monthlyExpenses * 6.0 - balance).max(Money.ZERO),
            replenishmentRatePct = 10.0,
            isMeetingTarget = coverageMonths >= 6.0,
            monthsToTarget = 5,
            warnings = emptyList()
        )
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan = ActuarialMathUtils.calculatePlanLogic(group, members, goalAmount, periodMonths)
    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        return IndustryBenchmark(
            benchmarkType = "Emergency Fund (Standard)",
            industryAvgContribution = 200.0,
            industryAvgBalance = 5000.0,
            industryPaymentRatePct = 95.0,
            groupVsBenchmarkPct = 0.0,
            benchmarkNotes = "Target is 6 months of group monthly contribution buffer."
        )
    }

    fun computeMetricsLegacyInternal(group: Group, members: List<Member>, monthsActive: Int, totalExpected: Money): EmergencyFundMetrics {
        return computeDetailedMetrics(group, members, monthsActive, totalExpected)
    }
}
