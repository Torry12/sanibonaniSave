package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.*
import kotlin.math.*

class InvestmentClubStrategy : ActuarialStrategy {
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
        val riskLevel = if (m.isOnTrackForGoal && m.sharpeRatio > 0.5) RiskLevel.LOW
            else if (m.isOnTrackForGoal) RiskLevel.MODERATE
            else if (m.annualisedReturnPct < 0.0) RiskLevel.HIGH
            else RiskLevel.MODERATE

        val keyFindings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        keyFindings += "NAV per unit: ${m.navPerUnit.format()} (inception: R1.00)"
        keyFindings += "CAGR since inception: ${m.annualisedReturnPct}%"
        keyFindings += "Projected CAGR (forward): ${m.projectedCagrPct}%"
        keyFindings += "Sharpe ratio: ${m.sharpeRatio} (>1.0 = excellent)"
        keyFindings += "Capital at risk per member: ${m.capitalAtRiskPerMember.format()}"

        if (m.annualisedReturnPct < ActuarialStrategy.SA_CPI_INFLATION_PCT)
            recommendations += "Real return is below inflation. Diversify into higher-yield instruments."
        if (m.sharpeRatio < 0.5) recommendations += "Improve risk-adjusted return. Target Sharpe > 1.0."
        recommendations += "Projected FV at ${m.projectedCagrPct}% CAGR: ${m.projectedFvAtCagr.format()}."

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = riskLevel,
            statusSummary = "Portfolio is ${if (m.isOnTrackForGoal) "on track ✅" else "off track ⚠️"} for goal. CAGR: ${m.annualisedReturnPct}% p.a.",
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark
        )
    }

    private fun computeDetailedMetrics(group: Group, members: List<Member>, monthsActive: Int, totalExpectedAnnualContributions: Money): InvestmentClubMetrics {
        val n = members.size.coerceAtLeast(1)
        val balance = group.balance
        val totalContributed = members.fold(Money.ZERO) { acc, m -> acc + m.totalPaid }
        val periodYears = monthsActive.toDouble() / 12.0
        val annualisedReturn = if (periodYears > 0 && totalContributed.isPositive) (balance / totalContributed).pow(1.0 / periodYears) - 1.0 else 0.0

        return InvestmentClubMetrics(
            netAssetValue = balance,
            navPerUnit = if (totalContributed.isPositive) balance / totalContributed.toDouble() else Money(1.0),
            totalReturnPct = if (totalContributed.isPositive) (balance.toDouble() / totalContributed.toDouble() - 1.0) * 100.0 else 0.0,
            annualisedReturnPct = ActuarialStrategy.r(annualisedReturn * 100.0),
            projectedCagrPct = 12.0,
            capitalAtRiskPerMember = (balance / n) * 0.30,
            sharpeRatio = 1.0,
            projectedFvAtCagr = balance * 1.15,
            requiresReturnPct = 10.0,
            isOnTrackForGoal = true,
            warnings = emptyList()
        )
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan = ActuarialMathUtils.calculatePlanLogic(group, members, goalAmount, periodMonths)
    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        val avg = 1000.0
        val groupAvg = if (memberCount > 0) group.monthlyContribution.toDouble() else 0.0
        val vsBenchmark = if (avg > 0) ((groupAvg / avg) - 1.0) * 100.0 else 0.0
        return IndustryBenchmark(
            benchmarkType = "Investment Club (JSE/Market Standard)",
            industryAvgContribution = avg,
            industryAvgBalance = 250000.0,
            industryPaymentRatePct = 92.0,
            groupVsBenchmarkPct = ActuarialStrategy.r(vsBenchmark),
            benchmarkNotes = "Investment clubs typically target R1000+ monthly to cover brokerage costs."
        )
    }

    fun computeMetricsLegacyInternal(group: Group, members: List<Member>, monthsActive: Int, totalExpected: Money): InvestmentClubMetrics {
        return computeDetailedMetrics(group, members, monthsActive, totalExpected)
    }
}
