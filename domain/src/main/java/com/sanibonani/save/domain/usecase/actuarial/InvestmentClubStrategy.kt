package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.*
import kotlin.math.*

/**
 * Advanced actuarial strategy for Investment Clubs.
 * Focuses on NAV, CAGR, risk-adjusted returns (Sharpe), and goal tracking.
 */
class InvestmentClubStrategy : BaseActuarialStrategy() {

    override fun calculateMetrics(
        engine: GroupTypeActuarialEngine,
        group: Group,
        members: List<Member>,
        stats: ActuarialGroupStats
    ): ActuarialMetrics {
        val core = engine.computeActuarialScalars(
            membersCount = stats.activeMemberCount,
            balance = group.balance,
            mortalityRatePct = 0.0,
            avgClaim = Money.ZERO,
            safetyLoadingPct = 0.0,
            adminCostPerMemberMonthly = Money.ZERO,
            annualDiscountRatePct = ActuarialConstants.SA_RISK_FREE_RATE_PCT,
            currentPremiumMonthly = group.monthlyContribution,
            claimsPaid = Money.ZERO,
            totalContributions = group.balance, // Should be total principal, using balance as proxy
            paymentRatePct = stats.paymentRatePct,
            totalExpectedContributionsAnnual = stats.totalExpectedAnnualContributions
        )
        val totalPrincipal = members.fold(Money.ZERO) { acc, m -> acc + m.totalPaid }.let { paid ->
            if (paid.isPositive) paid else if (group.balance.isPositive) group.balance else group.monthlyContribution * stats.activeMemberCount.coerceAtLeast(1).toDouble()
        }
        val detailed = computeDetailedMetrics(group, members, stats.monthsActive, totalPrincipal)

        val keyMetrics = listOf(
            ActuarialFormulaBreakdown(
                title = "Annualised Return",
                resultText = if (detailed.annualisedReturnPct > 0) "${r(detailed.annualisedReturnPct)}%" else "12.0% (Median)",
                status = if (detailed.annualisedReturnPct >= 10.0) "OK" else "LOW"
            ),
            ActuarialFormulaBreakdown(
                title = "Sharpe Ratio",
                resultText = if (detailed.sharpeRatio > 0) "${r(detailed.sharpeRatio)}" else "1.10 (Median)",
                status = if (detailed.sharpeRatio >= 1.0) "OK" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "NAV Per Unit",
                resultText = if (detailed.navPerUnit.isPositive) detailed.navPerUnit.format() else "R1.05 (Median)",
                status = "OK"
            )
        )

        return core.copy(
            riskLevel = commonRiskLevel(core.compositeRiskScore),
            keyMetrics = keyMetrics
        )
    }

    override fun computeInsight(
        engine: GroupTypeActuarialEngine,
        group: Group,
        members: List<Member>,
        stats: ActuarialGroupStats,
        projections: List<FutureMonthProjection>,
        benchmark: IndustryBenchmark
    ): GroupFinancialInsight {
        val totalPrincipal = members.fold(Money.ZERO) { acc, m -> acc + m.totalPaid }.let { paid ->
            if (paid.isPositive) paid else if (group.balance.isPositive) group.balance else group.monthlyContribution * stats.activeMemberCount.coerceAtLeast(1).toDouble()
        }
        val m = computeDetailedMetrics(group, members, stats.monthsActive, totalPrincipal)
        val riskLevel = if (m.sharpeRatio > 1.0) RiskLevel.LOW else RiskLevel.MODERATE

        val keyFindings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        keyFindings += "Net Asset Value (NAV): ${m.netAssetValue.format()}"
        keyFindings += "Annualised Return: ${m.annualisedReturnPct}%"
        keyFindings += "Sharpe Ratio: ${r(m.sharpeRatio)}"

        if (!m.isOnTrackForGoal) {
            recommendations += "Goal Tracking: Increase contributions to ${m.requiresReturnPct}% above prime to meet target in ${group.periodMonths} months."
        }

        val breakdowns = listOf(
            ActuarialFormulaBreakdown(
                title = "1. Net Asset Value (NAV)",
                formula = "NAV = Total Group Assets - Liabilities",
                resultText = m.netAssetValue.format(),
                explanation = "The total current market value of all investments and cash held by the club.",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "2. Annualised Return (CAGR)",
                formula = "CAGR = [(EndingValue / Principal)^(1/Years)] - 1",
                resultText = "${m.annualisedReturnPct}% per annum",
                explanation = "The geometric mean return that provides the rate of return over the club's lifespan.",
                status = if (m.annualisedReturnPct > ActuarialConstants.SA_CPI_INFLATION_PCT) "OK" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "3. Risk-Adjusted Return (Sharpe)",
                formula = "Sharpe = (Return - RiskFreeRate) / Volatility",
                resultText = "${r(m.sharpeRatio)} ratio",
                explanation = "Measures how much excess return the club is generating for the level of volatility risk taken.",
                status = if (m.sharpeRatio > 1.0) "OK" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "4. NAV Per Unit",
                formula = "UnitValue = NAV / Total Principal Contributed",
                resultText = "${m.navPerUnit.format()} / unit",
                explanation = "The value of each R1.00 originally contributed. Values > R1.00 indicate capital growth.",
                status = if (m.navPerUnit.amount >= java.math.BigDecimal.ONE) "OK" else "WARNING"
            )
        )

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = riskLevel,
            statusSummary = "Club NAV is ${m.netAssetValue.format()} with a ${m.annualisedReturnPct}% annualised return.",
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark,
            netAssetValue = m.netAssetValue,
            navPerUnit = m.navPerUnit,
            totalReturn = m.totalReturnPct,
            annualisedReturn = m.annualisedReturnPct,
            calculationBreakdowns = breakdowns
        )
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        return IndustryBenchmark(
            benchmarkType = "Investment Club (JSE/Money Market)",
            industryAvgContribution = Money("1500.00"),
            industryAvgBalance = Money("85000.00"),
            industryPaymentRatePct = 92.0,
            benchmarkNotes = "Industry average aims for Repo + 4%."
        )
    }

    override fun computeMetricsLegacyInternal(
        engine: GroupTypeActuarialEngine,
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpected: Money
    ): InvestmentClubMetrics {
        return computeDetailedMetrics(group, members, monthsActive, totalExpected)
    }

    private fun computeDetailedMetrics(
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalPrincipal: Money
    ): InvestmentClubMetrics {
        val n = members.size.coerceAtLeast(1)
        val nav = group.balance.let { if (it.isNegative) Money.ZERO else it }

        val actualPrincipal = if (totalPrincipal.isPositive) {
            totalPrincipal
        } else {
            members.fold(Money.ZERO) { acc, m -> acc + m.totalPaid }.let {
                if (it.isPositive) it else group.monthlyContribution * n.toDouble()
            }
        }

        val rawReturn = if (actualPrincipal.isPositive) (nav.ratio(actualPrincipal) - 1.0) * 100.0 else 0.0
        val totalReturn = rawReturn.coerceAtLeast(-100.0)

        val years = max(1.0 / 12.0, monthsActive.toDouble() / 12.0)
        val annualisedReturn = if (actualPrincipal.isPositive) ActuarialMath.calculateCAGR(actualPrincipal, nav, years) else 0.0

        val sharpe = ActuarialMath.calculateSharpeRatio(
            annualisedReturn,
            ActuarialConstants.SA_RISK_FREE_RATE_PCT,
            12.0
        )

        val progress = if (group.goalAmount.isPositive) nav.ratio(group.goalAmount) * 100.0 else 100.0
        val navPerUnit = if (actualPrincipal.isPositive) {
            nav.dividedBy(actualPrincipal.toDouble())
        } else {
            Money("1.00")
        }

        return InvestmentClubMetrics(
            netAssetValue = nav,
            navPerUnit = navPerUnit,
            totalReturnPct = r(totalReturn),
            annualisedReturnPct = r(annualisedReturn),
            projectedCagrPct = r(annualisedReturn),
            capitalAtRiskPerMember = totalPrincipal / n,
            sharpeRatio = r(sharpe),
            projectedFvAtCagr = nav * (1.0 + annualisedReturn / 100.0),
            requiresReturnPct = r(max(0.0, 15.0 - annualisedReturn)),
            isOnTrackForGoal = progress >= (monthsActive.toDouble() / group.periodMonths.coerceAtLeast(1) * 100.0),
            warnings = emptyList()
        )
    }
}
