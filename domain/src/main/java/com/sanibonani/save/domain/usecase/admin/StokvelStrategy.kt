package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.*
import java.time.LocalDate
import kotlin.math.*

class StokvelStrategy : ActuarialStrategy {
    override fun calculateMetrics(group: Group, members: List<Member>, stats: ActuarialGroupStats): ActuarialMetrics {
        val n = stats.activeMemberCount
        val core = GroupTypeActuarialEngine.computeActuarialScalars(
            membersCount = n,
            balance = group.balance,
            mortalityRatePct = GroupTypeActuarialEngine.SA_WORKING_AGE_MORT_PER_1000 / 10.0,
            avgClaim = Money(n * 5000.0) / n.toDouble(),
            safetyLoadingPct = GroupTypeActuarialEngine.BURIAL_SAFETY_LOADING_PCT,
            adminCostPerMember = Money(GroupTypeActuarialEngine.ADMIN_COST_PER_MEMBER),
            annualDiscountRatePct = GroupTypeActuarialEngine.SA_REPO_RATE_PCT,
            currentPremium = if (n > 0) stats.totalExpectedAnnualContributions / 12.0 / n.toDouble() else group.monthlyContribution,
            claimsPaid = Money(n * 500.0),
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
            m.savingsEfficiencyScore >= 80 -> RiskLevel.LOW
            m.savingsEfficiencyScore >= 55 -> RiskLevel.MODERATE
            else -> RiskLevel.HIGH
        }

        val keyFindings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        keyFindings += "Pot milestone: ${m.potMilestonePct}% toward annual target"
        keyFindings += "Projected payout per member: ${m.projectedPayoutPerMember.format()}"
        keyFindings += "Payment compliance: ${m.paymentCompliancePct}%"
        keyFindings += "vs NASASA average: ${if (m.benchmarkVsNasasaAvg >= 0.0) "+" else ""}${m.benchmarkVsNasasaAvg}%"

        if (m.paymentCompliancePct < 90.0)
            recommendations += "Chase outstanding contributions. Send reminders 5 days before due date."
        recommendations += "December payout projection: ${m.projectedPayoutPerMember.format()} per member."
        if (m.benchmarkVsNasasaAvg < 0.0)
            recommendations += "Payout is below SA average. Consider increasing monthly contribution."

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = riskLevel,
            statusSummary = "Savings at ${m.potMilestonePct}% of target. Payout in ${m.monthsToTarget} months.",
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark
        )
    }

    private fun computeDetailedMetrics(group: Group, members: List<Member>, monthsActive: Int, totalExpectedAnnualContributions: Money): StokvelActuarialMetrics {
        val n = members.size.coerceAtLeast(1)
        val balance = group.balance
        val monthInYear = (monthsActive % 12).let { if (it == 0) 12 else it }
        val monthsToTarget = (12 - monthInYear).let { if (it == 0) 0 else it }

        val futureContribs = group.monthlyContribution * n * monthsToTarget
        val totalProjectedFund = balance + futureContribs
        val projectedPayoutPerMember = totalProjectedFund / n
        val annualContributionTarget = group.monthlyContribution * n * 12
        val potMilestonePct = if (annualContributionTarget.isPositive) min(100.0, balance.toDouble() / annualContributionTarget.toDouble() * 100.0) else 0.0

        val monthlyExpected = totalExpectedAnnualContributions / 12.0
        val expectedByNow = monthlyExpected * monthInYear.coerceAtLeast(1)
        val paymentCompliancePct = if (expectedByNow.isPositive) min(100.0, balance.toDouble() / expectedByNow.toDouble() * 100.0) else 100.0

        val nasasaAvgAnnualPayout = 150.0 * 12.0
        val benchmarkVsNasasa = (projectedPayoutPerMember.toDouble() / nasasaAvgAnnualPayout - 1.0) * 100.0
        val savingsScore = (min(100.0, potMilestonePct) * 0.4 + min(100.0, paymentCompliancePct) * 0.6).toInt().coerceIn(0, 100)

        return StokvelActuarialMetrics(
            totalProjectedFund = totalProjectedFund,
            projectedPayoutPerMember = projectedPayoutPerMember,
            potMilestonePct = ActuarialStrategy.r(potMilestonePct),
            paymentCompliancePct = ActuarialStrategy.r(paymentCompliancePct),
            benchmarkVsNasasaAvg = ActuarialStrategy.r(benchmarkVsNasasa),
            savingsEfficiencyScore = savingsScore,
            targetPayoutMonth = 12,
            monthsToTarget = monthsToTarget,
            annualContributionTarget = annualContributionTarget,
            warnings = emptyList()
        )
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan = ActuarialMathUtils.calculatePlanLogic(group, members, goalAmount, periodMonths)
    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        val avg = 500.0
        val groupAvg = if (memberCount > 0) group.monthlyContribution.toDouble() else 0.0
        val vsBenchmark = if (avg > 0) ((groupAvg / avg) - 1.0) * 100.0 else 0.0
        return IndustryBenchmark(
            benchmarkType = "Retail Stokvel (NASASA Standard)",
            industryAvgContribution = avg,
            industryAvgBalance = 12000.0,
            industryPaymentRatePct = 94.0,
            groupVsBenchmarkPct = ActuarialStrategy.r(vsBenchmark),
            benchmarkNotes = "NASASA reports average monthly stokvel contributions of R500 per member."
        )
    }

    fun computeMetricsLegacyInternal(group: Group, members: List<Member>, monthsActive: Int, totalExpected: Money): StokvelActuarialMetrics {
        return computeDetailedMetrics(group, members, monthsActive, totalExpected)
    }
}
