package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.*
import kotlin.math.*

/**
 * Enhanced actuarial strategy for Stokvels.
 * Focuses on savings efficiency, pot milestones, and annual payout stability.
 */
class StokvelStrategy : BaseActuarialStrategy() {

    override fun calculateMetrics(
        group: Group,
        members: List<Member>,
        stats: ActuarialGroupStats
    ): ActuarialMetrics {
        val n = stats.activeMemberCount
        val core = ActuarialScalars.compute(
            membersCount = n,
            balance = group.balance,
            mortalityRatePct = 0.0, // Not applicable for standard Stokvel
            avgClaim = Money.ZERO,
            safetyLoadingPct = 0.0,
            adminCostPerMemberMonthly = Money.ZERO,
            annualDiscountRatePct = ActuarialConstants.SA_RISK_FREE_RATE_PCT,
            currentPremiumMonthly = group.monthlyContribution,
            claimsPaid = Money.ZERO,
            totalContributions = group.balance,
            paymentRatePct = stats.paymentRatePct,
            totalExpectedContributionsAnnual = stats.totalExpectedAnnualContributions
        )
        val detailed = computeDetailedMetrics(group, members, stats.monthsActive, stats.totalExpectedAnnualContributions)

        val keyMetrics = listOf(
            ActuarialFormulaBreakdown(
                title = "Payment Compliance",
                resultText = if (stats.paymentRatePct > 0) "${r(stats.paymentRatePct)}%" else "94% (Median)",
                status = if (stats.paymentRatePct >= 90.0) "OK" else "LOW"
            ),
            ActuarialFormulaBreakdown(
                title = "Savings Efficiency",
                resultText = if (detailed.savingsEfficiencyScore > 0) "${detailed.savingsEfficiencyScore}/100" else "88/100 (Median)",
                status = if (detailed.savingsEfficiencyScore >= 70) "OK" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "Annual Pot Milestone",
                resultText = "${r(detailed.potMilestonePct)}%",
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
        val riskLevel = commonRiskLevel(m.savingsEfficiencyScore)

        val keyFindings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        keyFindings += "Pot milestone: ${m.potMilestonePct}% toward annual target"
        keyFindings += "Payment compliance: ${m.paymentCompliancePct}%"
        keyFindings += "Efficiency score: ${m.savingsEfficiencyScore}/100"

        if (m.paymentCompliancePct < 90.0) recommendations += "Immediate Action: Collect outstanding contributions to meet year-end payout targets."
        if (m.monthsToTarget <= 2) recommendations += "Liquidity Alert: Group is approaching payout date. Ensure funds are in a high-interest, accessible account."

        val n = members.size.coerceAtLeast(1)
        val annualTarget = group.monthlyContribution * n * 12
        val breakdowns = listOf(
            ActuarialFormulaBreakdown(
                title = "1. Annual Pot Milestone Target",
                formula = "Milestone = (FundBalance / AnnualTarget) × 100",
                resultText = "${m.potMilestonePct}% achieved (Target: ${annualTarget.format()})",
                explanation = "Measures accumulated pool balance against the annual group contribution goal.",
                status = if (m.potMilestonePct >= 75.0) "OK" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "2. Contribution Collection Compliance",
                formula = "Compliance = (Balance / ExpectedToDate) × 100",
                resultText = "${m.paymentCompliancePct}% compliance rate",
                explanation = "Measures how reliably member contributions are collected relative to scheduled dues.",
                status = if (m.paymentCompliancePct >= 90.0) "OK" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "3. Projected Year-End Payout Per Member",
                formula = "Payout = (FundBalance + (MonthlyContrib × N × MonthsLeft)) / N",
                resultText = "${m.projectedPayoutPerMember.format()} / member (Pool: ${m.totalProjectedFund.format()})",
                explanation = "Projects individual lump-sum payout available at year-end based on current payment velocity.",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "4. Savings Efficiency Index",
                formula = "Score = (Milestone × 0.4) + (Compliance × 0.6)",
                resultText = "${m.savingsEfficiencyScore} / 100 efficiency score",
                explanation = "Weighted index balancing overall fund accumulation and monthly payment compliance.",
                status = if (m.savingsEfficiencyScore >= 70) "OK" else "WARNING"
            )
        )

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = riskLevel,
            statusSummary = "Savings are at ${m.potMilestonePct}% of the annual goal. Payout projected in ${m.monthsToTarget} months.",
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark,
            totalProjectedFund = m.totalProjectedFund,
            projectedPayoutPerMember = m.projectedPayoutPerMember,
            potMilestonePct = m.potMilestonePct,
            calculationBreakdowns = breakdowns
        )
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        return IndustryBenchmark(
            benchmarkType = "Retail Stokvel (NASASA Standard)",
            industryAvgContribution = Money("500.00"),
            industryAvgBalance = Money("12000.00"),
            industryPaymentRatePct = 94.0,
            benchmarkNotes = "NASASA benchmark for grocery/cash stokvels."
        )
    }

    fun computeDetailedMetrics(
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpectedAnnual: Money
    ): StokvelActuarialMetrics {
        val n = members.size.coerceAtLeast(1)
        val monthInYear = (monthsActive % 12).let { if (it == 0) 12 else it }
        val monthsToTarget = (12 - monthInYear).coerceAtLeast(0)

        val annualTarget = group.monthlyContribution * n * 12
        val potMilestone = if (annualTarget.isPositive) (group.balance.ratio(annualTarget)) * 100.0 else 100.0

        val totalProjected = group.balance + (group.monthlyContribution * n * monthsToTarget)

        val monthlyExpected = totalExpectedAnnual / 12
        val expectedToDate = monthlyExpected * monthInYear
        val compliance = if (expectedToDate.isPositive) (group.balance.ratio(expectedToDate)) * 100.0 else 100.0

        // Efficiency score combines progress and compliance
        val score = (min(100.0, potMilestone) * 0.4 + min(100.0, compliance) * 0.6).toInt()

        return StokvelActuarialMetrics(
            totalProjectedFund = totalProjected,
            projectedPayoutPerMember = totalProjected / n,
            potMilestonePct = r(potMilestone),
            paymentCompliancePct = r(compliance),
            benchmarkVsNasasaAvg = r(((totalProjected / n).ratio(Money("6000.00")) - 1.0) * 100.0),
            savingsEfficiencyScore = score,
            targetPayoutMonth = 12,
            monthsToTarget = monthsToTarget,
            annualContributionTarget = annualTarget,
            warnings = emptyList()
        )
    }
}
