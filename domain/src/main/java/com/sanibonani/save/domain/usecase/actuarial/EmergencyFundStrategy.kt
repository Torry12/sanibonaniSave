package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.*
import kotlin.math.*

/**
 * Strategy for Emergency Funds focusing on liquidity coverage and replenishment.
 */
class EmergencyFundStrategy : BaseActuarialStrategy() {

    override fun calculateMetrics(
        group: Group,
        members: List<Member>,
        stats: ActuarialGroupStats
    ): ActuarialMetrics {
        val n = stats.activeMemberCount
        val core = ActuarialScalars.compute(
            membersCount = n,
            balance = group.balance,
            mortalityRatePct = ActuarialConstants.SA_WORKING_AGE_MORT_PER_1000 / 10.0,
            avgClaim = Money("2000"),
            safetyLoadingPct = ActuarialConstants.BURIAL_SAFETY_LOADING_PCT,
            adminCostPerMemberMonthly = ActuarialConstants.ADMIN_COST_PER_MEMBER_MONTHLY,
            annualDiscountRatePct = ActuarialConstants.SA_RISK_FREE_RATE_PCT,
            currentPremiumMonthly = group.monthlyContribution,
            claimsPaid = Money.ZERO,
            totalContributions = group.balance,
            paymentRatePct = stats.paymentRatePct,
            totalExpectedContributionsAnnual = stats.totalExpectedAnnualContributions
        )
        val detailed = computeDetailedMetrics(group, members)

        val keyMetrics = listOf(
            ActuarialFormulaBreakdown(
                title = "Liquidity Coverage",
                resultText = if (detailed.coverageMonths > 0) "${detailed.coverageMonths} months" else "4.5 months (Median)",
                status = if (detailed.isMeetingTarget) "OK" else "LOW"
            ),
            ActuarialFormulaBreakdown(
                title = "Replenishment Rate",
                resultText = if (detailed.replenishmentRatePct > 0) "${detailed.replenishmentRatePct}%" else "15% (Median)",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "Target Velocity",
                resultText = if (detailed.monthsToTarget > 0) "${detailed.monthsToTarget} months to goal" else "N/A",
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
        val m = computeDetailedMetrics(group, members)
        val riskLevel = if (m.isMeetingTarget) RiskLevel.LOW else RiskLevel.MODERATE

        val keyFindings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        keyFindings += "Coverage: ${m.coverageMonths} months of outflows"
        keyFindings += "Target: ${m.targetCoverageMonths} months"
        keyFindings += "Replenishment: ${m.replenishmentRatePct}% monthly"

        if (!m.isMeetingTarget) {
            recommendations += "Liquidity Gap: Increase monthly contributions by ${m.coverageGap.dividedBy(12.0).format()} to reach 6-month safety buffer."
        }
        recommendations += "Asset Allocation: Keep at least 40% of funds in an immediate-access savings pocket."

        val breakdowns = listOf(
            ActuarialFormulaBreakdown(
                title = "1. Liquidity Coverage Ratio (Months)",
                formula = "Coverage = FundBalance / AverageMonthlyOutflows",
                resultText = "${m.coverageMonths} months of cover",
                explanation = "Estimates how many months the fund can sustain emergency withdrawals at current churn levels.",
                status = if (m.isMeetingTarget) "OK" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "2. Target Capital Replenishment",
                formula = "Replenishment = (Balance / LiquidityTarget) × 100",
                resultText = "${m.replenishmentRatePct}% of target reached",
                explanation = "Measures the current fund size against the benchmark 6-month emergency liquidity target.",
                status = if (m.replenishmentRatePct >= 80.0) "OK" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "3. Immediate Liquidity Gap",
                formula = "Gap = LiquidityTarget - CurrentBalance",
                resultText = "${m.coverageGap.format()} shortfall",
                explanation = "The additional capital required to meet the group's desired safety buffer.",
                status = if (m.coverageGap.isZero) "OK" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "4. Recovery Velocity",
                formula = "MonthsToTarget = Gap / MonthlyContributionTotal",
                resultText = "${m.monthsToTarget} months to reach target",
                explanation = "How long it will take to close the liquidity gap at the current contribution rate.",
                status = "OK"
            )
        )

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = riskLevel,
            statusSummary = if (m.isMeetingTarget) "Emergency fund meets target liquidity levels." else "Emergency fund has a liquidity shortfall of ${m.coverageGap.format()}.",
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark,
            reserveCoverageMonths = m.coverageMonths,
            capitalAdequacyPct = m.replenishmentRatePct,
            calculationBreakdowns = breakdowns
        )
    }

    fun computeDetailedMetrics(group: Group, members: List<Member>): EmergencyFundMetrics {
        val n = members.size.coerceAtLeast(1)
        val monthlyOutflowProxy = (group.monthlyContribution * n).percent(10) // Assume 10% monthly churn
        val coverage = if (monthlyOutflowProxy.isPositive) group.balance.ratio(monthlyOutflowProxy) else 12.0
        val target = ActuarialConstants.EMERGENCY_TARGET_MONTHS

        val targetAmount = monthlyOutflowProxy * target
        val gap = (targetAmount - group.balance).max(Money.ZERO)

        return EmergencyFundMetrics(
            coverageMonths = r(coverage),
            targetCoverageMonths = target,
            coverageGap = gap,
            replenishmentRatePct = r(if (targetAmount.isPositive) (group.balance.ratio(targetAmount)) * 100.0 else 100.0),
            isMeetingTarget = coverage >= target,
            monthsToTarget = if (group.monthlyContribution.isPositive) (gap.ratio(group.monthlyContribution * n)).toInt() else 0,
            warnings = emptyList()
        )
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        return IndustryBenchmark(
            benchmarkType = "Emergency Fund (Liquidity Standard)",
            industryAvgContribution = Money("250.0"),
            industryAvgBalance = Money("15000.0"),
            industryPaymentRatePct = 90.0,
            benchmarkNotes = "Aims for 6 months of average household expenses coverage."
        )
    }
}
