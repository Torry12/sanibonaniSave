package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.*

/**
 * Strategy for Hybrid Savings Groups (Integrated Multi-Pot Cooperative Model).
 */
class HybridSavingsStrategy : BaseActuarialStrategy() {

    override fun calculateMetrics(
        group: Group,
        members: List<Member>,
        stats: ActuarialGroupStats
    ): ActuarialMetrics {
        val core = ActuarialScalars.compute(
            membersCount = stats.activeMemberCount,
            balance = group.balance,
            mortalityRatePct = 0.0,
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

        val split = HybridPotAllocation().calculateSplit(group.balance)

        val keyMetrics = listOf(
            ActuarialFormulaBreakdown(
                title = "Personal Savings Pot",
                resultText = split.personalSavings.format(),
                explanation = "60% allocated to individual member accumulated savings.",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "Lending Pool Pot",
                resultText = split.lendingPool.format(),
                explanation = "20% allocated for member loan liquidity.",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "Social Fund Pot",
                resultText = split.socialFund.format(),
                explanation = "10% allocated for emergency member relief.",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "Group Investment Pot",
                resultText = split.investment.format(),
                explanation = "10% allocated for long-term growth investments.",
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
        val allocation = HybridPotAllocation()
        val split = allocation.calculateSplit(group.balance)
        val keyFindings = listOf(
            "Integrated Hybrid Model: ${group.balance.format()} active total balance.",
            "Personal Savings Pot: ${split.personalSavings.format()} (${allocation.personalSavingsPct.toInt()}%)",
            "Lending Pool Pot: ${split.lendingPool.format()} (${allocation.lendingPoolPct.toInt()}%)",
            "Social Emergency Fund: ${split.socialFund.format()} (${allocation.socialFundPct.toInt()}%)",
            "Group Investment Pot: ${split.investment.format()} (${allocation.investmentPct.toInt()}%)"
        )

        val recommendations = listOf(
            "Maintain balanced ${allocation.personalSavingsPct.toInt()}/${allocation.lendingPoolPct.toInt()}/${allocation.socialFundPct.toInt()}/${allocation.investmentPct.toInt()} multi-pot allocation to optimize member liquidity and capital growth."
        )

        val breakdowns = split.toFormulaBreakdowns(allocation)

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = RiskLevel.LOW,
            statusSummary = "Hybrid Savings Group operates with 4 virtual pots totaling ${group.balance.format()}.",
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark,
            calculationBreakdowns = breakdowns
        )
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        return IndustryBenchmark(
            benchmarkType = "Hybrid Financial Cooperative",
            industryAvgContribution = Money("500.00"),
            industryAvgBalance = Money("50000.00"),
            industryPaymentRatePct = 95.0,
            benchmarkNotes = "Standard multi-pot model (60% Savings / 20% Lending / 10% Social / 10% Investment)."
        )
    }
}
