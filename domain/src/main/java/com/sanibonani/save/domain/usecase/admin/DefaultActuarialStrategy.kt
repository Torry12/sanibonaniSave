package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.*
import kotlin.math.*

class DefaultActuarialStrategy : ActuarialStrategy {
    override fun calculateMetrics(group: Group, members: List<Member>, stats: ActuarialGroupStats): ActuarialMetrics {
        val n = stats.activeMemberCount
        val core = GroupTypeActuarialEngine.computeActuarialScalars(
            membersCount = n,
            balance = group.balance,
            mortalityRatePct = GroupTypeActuarialEngine.SA_WORKING_AGE_MORT_PER_1000 / 10.0,
            avgClaim = Money(n * 1000.0) / n.toDouble(),
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
        val riskLevel = when {
            m.goalProgressPct >= 80.0 -> RiskLevel.LOW
            m.goalProgressPct >= 40.0 -> RiskLevel.MODERATE
            else -> RiskLevel.HIGH
        }

        val keyFindings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        keyFindings += "Goal progress: ${m.goalProgressPct}%"
        keyFindings += "Savings per member: ${m.savingsPerMember.format()}"
        keyFindings += "Annual dividend projection (money-market): ${m.annualDividendProjection.format()}"

        recommendations += m.projectedGoalReachDescription
        recommendations += "Channel pooled community savings into a low-risk money-market account to preserve liquidity and earn yield."

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = riskLevel,
            statusSummary = "Community goal is ${m.goalProgressPct}% funded. ${m.projectedGoalReachDescription}.",
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark
        )
    }

    private fun computeDetailedMetrics(group: Group, members: List<Member>, monthsActive: Int, totalExpectedAnnualContributions: Money): CommunitySavingsMetrics {
        val n = members.size.coerceAtLeast(1)
        val progress = if (group.goalAmount.isPositive) min(100.0, (group.balance.toDouble() / group.goalAmount.toDouble()) * 100.0) else 100.0
        val reach = if (progress >= 100.0) "Goal reached ✅" else "On track"
        return CommunitySavingsMetrics(
            savingsPerMember = group.balance / n,
            annualDividendProjection = group.balance * 0.06,
            growthRatePct = 5.0,
            goalProgressPct = ActuarialStrategy.r(progress),
            projectedGoalReachDescription = reach,
            warnings = emptyList()
        )
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan = ActuarialMathUtils.calculatePlanLogic(group, members, goalAmount, periodMonths)
    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        return IndustryBenchmark(
            benchmarkType = "Community Savings",
            industryAvgContribution = 150.0,
            industryAvgBalance = 8000.0,
            industryPaymentRatePct = 85.0,
            groupVsBenchmarkPct = 0.0,
            benchmarkNotes = "Focus on meeting specific local goals."
        )
    }

    fun computeMetricsLegacyInternal(group: Group, members: List<Member>, monthsActive: Int, totalExpected: Money): CommunitySavingsMetrics {
        return computeDetailedMetrics(group, members, monthsActive, totalExpected)
    }
}
