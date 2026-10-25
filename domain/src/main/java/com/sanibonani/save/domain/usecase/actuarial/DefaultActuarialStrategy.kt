package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.*
import kotlin.math.*

/**
 * Standard strategy for general savings groups.
 */
class DefaultActuarialStrategy : BaseActuarialStrategy() {

    override fun calculateMetrics(
        group: Group,
        members: List<Member>,
        stats: ActuarialGroupStats
    ): ActuarialMetrics {
        val n = stats.activeMemberCount
        val defaultAnnualClaim = ActuarialConstants.DEFAULT_ANNUAL_EXPECTED_CLAIM_PER_MEMBER * n

        val core = ActuarialScalars.compute(
            membersCount = n,
            balance = group.balance,
            mortalityRatePct = ActuarialConstants.SA_WORKING_AGE_MORT_PER_1000 / 10.0,
            avgClaim = Money("1000"),
            safetyLoadingPct = ActuarialConstants.BURIAL_SAFETY_LOADING_PCT,
            adminCostPerMemberMonthly = ActuarialConstants.ADMIN_COST_PER_MEMBER_MONTHLY,
            annualDiscountRatePct = ActuarialConstants.SA_RISK_FREE_RATE_PCT,
            currentPremiumMonthly = group.monthlyContribution,
            claimsPaid = Money.ZERO,
            totalContributions = group.balance,
            paymentRatePct = stats.paymentRatePct,
            totalExpectedContributionsAnnual = stats.totalExpectedAnnualContributions,
            defaultAnnualExpectedClaim = defaultAnnualClaim
        )
        val detailed = computeDetailedMetrics(group, members, stats.monthsActive, stats.totalExpectedAnnualContributions)

        val keyMetrics = listOf(
            ActuarialFormulaBreakdown(
                title = "Goal Progress",
                resultText = if (detailed.goalProgressPct > 0) "${r(detailed.goalProgressPct)}%" else "65% (Median)",
                status = if (detailed.goalProgressPct >= 50.0) "OK" else "LOW"
            ),
            ActuarialFormulaBreakdown(
                title = "Savings Per Member",
                resultText = detailed.savingsPerMember.format(),
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "Assumed Growth",
                resultText = "${detailed.growthRatePct}% p.a.",
                status = "OK"
            )
        )

        return core.copy(
            riskLevel = commonRiskLevel(core.compositeRiskScore),
            keyMetrics = keyMetrics
        )
    }

    override fun computeInsight(
        group: Group,
        members: List<Member>,
        stats: ActuarialGroupStats,
        projections: List<FutureMonthProjection>,
        benchmark: IndustryBenchmark
    ): GroupFinancialInsight {
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
        keyFindings += "Annual dividend projection: ${m.annualDividendProjection.format()}"

        recommendations += m.projectedGoalReachDescription
        recommendations += "Maintain consistent contributions to reach the group goal on schedule."

        val breakdowns = listOf(
            ActuarialFormulaBreakdown(
                title = "1. Goal Progress",
                formula = "Progress = (Balance / Goal) × 100",
                resultText = "${m.goalProgressPct}% achieved",
                explanation = "Percentage of the group's target financial goal that has been funded by current reserves.",
                status = if (m.goalProgressPct >= 50.0) "OK" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "2. Savings Per Member",
                formula = "Share = FundBalance / CurrentMembers",
                resultText = "${m.savingsPerMember.format()} / member",
                explanation = "The average amount saved per member based on the total current group balance.",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "3. Growth Rate Assumption",
                formula = "Return = RiskFreeRate + Margin",
                resultText = "${m.growthRatePct}% p.a.",
                explanation = "The assumed annual growth rate for reinvested community funds.",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "4. Dividend Projection",
                formula = "Dividend = Balance × 0.06",
                resultText = "${m.annualDividendProjection.format()} (Pool)",
                explanation = "Estimated surplus available for distribution if the fund maintains current growth targets.",
                status = "OK"
            )
        )

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = riskLevel,
            statusSummary = "Community goal is ${m.goalProgressPct}% funded. ${m.projectedGoalReachDescription}.",
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark,
            calculationBreakdowns = breakdowns
        )
    }

    fun computeDetailedMetrics(group: Group, members: List<Member>, monthsActive: Int, totalExpectedAnnualContributions: Money): CommunitySavingsMetrics {
        val n = members.size.coerceAtLeast(1)
        val progress = if (group.goalAmount.isPositive) min(100.0, (group.balance.ratio(group.goalAmount)) * 100.0) else 100.0
        val reach = if (progress >= 100.0) "Goal reached ✅" else "On track"
        return CommunitySavingsMetrics(
            savingsPerMember = group.balance / n,
            annualDividendProjection = group.balance * 0.06,
            growthRatePct = 5.0,
            goalProgressPct = r(progress),
            projectedGoalReachDescription = reach,
            warnings = emptyList()
        )
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        return IndustryBenchmark(
            benchmarkType = "Community Savings",
            industryAvgContribution = Money("150.0"),
            industryAvgBalance = Money("8000.0"),
            industryPaymentRatePct = 85.0,
            groupVsBenchmarkPct = 0.0,
            benchmarkNotes = "Focus on meeting specific local goals."
        )
    }
}
