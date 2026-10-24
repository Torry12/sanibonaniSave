package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.*

/**
 * Detailed, user-friendly actuarial calculation engine for Burial Society groups.
 * Strictly aligned with South African Financial Services Board (FSB) solvency requirements
 * and ASISA working-age mortality benchmarks.
 */
class BurialSocietyStrategy : BaseActuarialStrategy() {

    override fun calculateMetrics(
        engine: GroupTypeActuarialEngine,
        group: Group,
        members: List<Member>,
        stats: ActuarialGroupStats,
    ): ActuarialMetrics {
        val n = stats.activeMemberCount
        val benefit = if (group.burialClaimAmount.isPositive) group.burialClaimAmount else if (group.goalAmount.isPositive) group.goalAmount else ActuarialConstants.SA_AVG_FUNERAL_COST
        val defaultAnnualClaim = benefit * (n * (ActuarialConstants.SA_WORKING_AGE_MORT_PER_1000 / 1000.0))

        val core = engine.computeActuarialScalars(
            membersCount = n,
            balance = group.balance,
            mortalityRatePct = ActuarialConstants.SA_WORKING_AGE_MORT_PER_1000 / 10.0,
            avgClaim = if (n > 0) benefit else Money.ZERO,
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

        val detailed = computeDetailedMetrics(engine, group, members, stats.monthsActive, stats.totalExpectedAnnualContributions)

        val keyMetrics = listOf(
            ActuarialFormulaBreakdown(
                title = "Reserve Adequacy",
                resultText = if (detailed.reserveCoverageMonths > 0) formatPct(core.reserveAdequacyPct) else "85% (Median)",
                status = if (detailed.reserveCoverageMonths >= 3.0) "OK" else "LOW"
            ),
            ActuarialFormulaBreakdown(
                title = "Solvency Margin",
                resultText = if (detailed.solvencyRatio > 0) "${r(detailed.solvencyRatio)}x" else "1.15x (Median)",
                status = if (detailed.solvencyRatio >= 1.3) "OK" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "Expected Claims (Ann)",
                resultText = if (detailed.projectedClaimsNextYear.isPositive) detailed.projectedClaimsNextYear.format() else (ActuarialConstants.DEFAULT_ANNUAL_EXPECTED_CLAIM_PER_MEMBER * stats.activeMemberCount).format() + " (Est)",
                status = "OK"
            )
        )

        return core.copy(
            riskLevel = commonRiskLevel(core.compositeRiskScore),
            warnings = detailed.warnings,
            keyMetrics = keyMetrics
        )
    }

    private fun formatPct(v: Double): String = "${r(v)}%"

    override fun computeInsight(
        engine: GroupTypeActuarialEngine,
        group: Group,
        members: List<Member>,
        stats: ActuarialGroupStats,
        projections: List<FutureMonthProjection>,
        benchmark: IndustryBenchmark
    ): GroupFinancialInsight {
        val m = computeDetailedMetrics(engine, group, members, stats.monthsActive, stats.totalExpectedAnnualContributions)
        val riskLevel = commonRiskLevel(buildRiskScore(m))

        val keyFindings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        keyFindings += "Solvency ratio: ${m.solvencyRatio}x (FSB Minimum Target: ${ActuarialConstants.BURIAL_MIN_SOLVENCY_RATIO}x)"
        keyFindings += "Immediate claim capacity: ${m.maxSimultaneousClaimsCovered} full claim(s) payable from cash"
        keyFindings += "Liquidity reserve: ${m.reserveCoverageMonths} months of claims covered"
        keyFindings += "Recommended monthly premium: ${m.recommendedMonthlyContributionPerMember.format()} / member"
        keyFindings += "Benefit adequacy: ${m.benefitAdequacyPct}% of market funeral standard (R30,000)"

        if (!m.isSolvent) {
            recommendations += "Critical Solvency: Increase monthly contributions to at least ${m.grossPremium.format()} to meet FSB 1.30x solvency target."
        }
        if (!m.isReserveAdequate) {
            recommendations += "Build Liquidity Reserve: Target a reserve buffer of ${m.requiredReserveAmount.format()} (3 months of claims)."
        }
        if (m.maxSimultaneousClaimsCovered < 1) {
            recommendations += "High Vulnerability: Current balance cannot cover even 1 full claim. Urgently require initial capital injection."
        }
        if (m.benefitAdequacyPct < 70.0) {
            recommendations += "Under-Insured Policy: Current death benefit of ${group.goalAmount.format()} is significantly below South African market average (R30,000)."
        }

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = riskLevel,
            statusSummary = m.userFriendlySummary,
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark,
            isSolvent = m.isSolvent,
            solvencyRatio = m.solvencyRatio,
            expectedAnnualClaimsCount = m.expectedAnnualClaimsCount,
            capitalAdequacyPct = m.capitalAdequacyPct,
            reserveCoverageMonths = m.reserveCoverageMonths,
            benefitAdequacyPct = m.benefitAdequacyPct,
            maxSimultaneousClaimsCovered = m.maxSimultaneousClaimsCovered,
            recommendedMonthlyContributionPerMember = m.recommendedMonthlyContributionPerMember,
            calculationBreakdowns = m.calculationBreakdowns
        )
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        return IndustryBenchmark(
            benchmarkType = "Burial Society (ASISA / FSB Standard)",
            industryAvgContribution = Money("350.0"),
            industryAvgBalance = Money("45000.0"),
            industryPaymentRatePct = 88.0,
            benchmarkNotes = "Standard SA burial society supporting R30,000 funeral benefit with 130% solvency target."
        )
    }

    override fun computeMetricsLegacyInternal(
        engine: GroupTypeActuarialEngine,
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpected: Money
    ): BurialSocietyActuarialMetrics {
        return computeDetailedMetrics(engine, group, members, monthsActive, totalExpected)
    }

    fun computeDetailedMetrics(
        engine: GroupTypeActuarialEngine,
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpectedAnnual: Money
    ): BurialSocietyActuarialMetrics {
        val activeMembers = members.filter { it.status == MemberStatus.ACTIVE || it.status == MemberStatus.PROBATION }
        val n = activeMembers.size.coerceAtLeast(1)
        val mortalityRatePerThousand = ActuarialConstants.SA_WORKING_AGE_MORT_PER_1000
        val mortalityRate = mortalityRatePerThousand / 1000.0
        val benefit = if (group.goalAmount.isPositive) group.goalAmount else ActuarialConstants.SA_AVG_FUNERAL_COST

        // 1. Expected Annual Claims
        val expectedAnnualClaimsCount = n * mortalityRate
        val expectedAnnualClaimsValue = benefit * expectedAnnualClaimsCount

        // 2. Pure Risk Premium (Monthly cost per member for pure risk)
        val purePremiumMonthly = (expectedAnnualClaimsValue / n) / 12.0

        // 3. Safety Loading & Admin Fees
        val safetyLoadedPremiumMonthly = purePremiumMonthly * (1.0 + ActuarialConstants.BURIAL_SAFETY_LOADING_PCT / 100.0)
        val adminFeeMonthly = ActuarialConstants.ADMIN_COST_PER_MEMBER_MONTHLY
        val grossPremiumMonthly = safetyLoadedPremiumMonthly + adminFeeMonthly

        // 4. Actuarial Present Value (APV) & FSB Solvency Ratio
        val apv = ActuarialMath.calculateAPV(expectedAnnualClaimsValue, ActuarialConstants.SA_RISK_FREE_RATE_PCT, 1.0)
        val periodYears = group.periodMonths.coerceAtLeast(12).toDouble() / 12.0
        val futureLiabilities = apv * periodYears
        val totalAssets = group.balance + totalExpectedAnnual * periodYears
        val solvencyRatio = if (futureLiabilities.isPositive) totalAssets.ratio(futureLiabilities) else 2.0

        // 5. Liquidity Reserve Coverage (3-month target)
        val monthlyClaims = expectedAnnualClaimsValue / 12.0
        val requiredReserve = monthlyClaims * ActuarialConstants.LIQUIDITY_RESERVE_MONTHS
        val reserveCoverageMonths = if (monthlyClaims.isPositive) group.balance.ratio(monthlyClaims) else 12.0

        // 6. Capital Adequacy & Benefit Adequacy
        val minimumCapital = expectedAnnualClaimsValue * ActuarialConstants.CAPITAL_ADEQUACY_THRESHOLD
        val capAdequacy = if (expectedAnnualClaimsValue.isPositive) (group.balance.ratio(expectedAnnualClaimsValue)) * 100.0 else 100.0
        val benefitAdequacy = benefit.ratio(ActuarialConstants.SA_AVG_FUNERAL_COST) * 100.0

        // 7. Max Immediate Claim Payout Load
        val maxSimultaneousClaimsCovered = if (benefit.isPositive) group.balance.ratio(benefit).toInt() else 0

        // 8. Solvency & Health Flags
        val isSolvent = solvencyRatio >= ActuarialConstants.BURIAL_MIN_SOLVENCY_RATIO
        val isReserveAdequate = reserveCoverageMonths >= ActuarialConstants.LIQUIDITY_RESERVE_MONTHS
        val isCapitalAdequate = group.balance >= minimumCapital

        // 9. Recommended Monthly Premium
        val recommendedMonthlyContribution = if (!isSolvent) {
            grossPremiumMonthly.max(group.monthlyContribution * 1.25)
        } else {
            grossPremiumMonthly
        }

        // 10. Years to Insolvency (if outflow > inflow)
        val monthlyInflow = group.monthlyContribution * n
        val monthlyOutflow = monthlyClaims + (adminFeeMonthly * n)
        val netMonthlyFlow = monthlyInflow - monthlyOutflow
        val yearsToInsolvency = if (netMonthlyFlow.isNegative && group.balance.isPositive) {
            (group.balance.ratio(-netMonthlyFlow)) / 12.0
        } else {
            -1.0
        }

        // 11. Clear Step-by-Step Actuarial Calculation Explanations
        val breakdowns = listOf(
            ActuarialFormulaBreakdown(
                title = "1. Pure Risk Premium",
                formula = "(Benefit ${benefit.format()} × ${r(mortalityRatePerThousand)} deaths/1,000) ÷ 12 months",
                resultText = "${purePremiumMonthly.format()} / member / month",
                explanation = "The baseline monthly cost required solely to pay expected funeral claims based on ASISA mortality rates.",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "2. Recommended Gross Premium",
                formula = "Pure Premium (${purePremiumMonthly.format()}) + 35% Safety Loading + R25.00 Admin Fee",
                resultText = "${grossPremiumMonthly.format()} / member / month",
                explanation = "Includes a 35% safety loading to absorb unexpected mortality surges and R25 monthly administration costs.",
                status = if (group.monthlyContribution >= grossPremiumMonthly) "OPTIMAL" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "3. FSB Solvency Ratio",
                formula = "(Current Cash ${group.balance.format()} + Projected Contributions) ÷ Future Liabilities",
                resultText = "${r(solvencyRatio)}x (FSB Target: ${ActuarialConstants.BURIAL_MIN_SOLVENCY_RATIO}x)",
                explanation = "FSB regulatory standard requiring at least 130% asset coverage relative to future liabilities.",
                status = if (solvencyRatio >= ActuarialConstants.BURIAL_MIN_SOLVENCY_RATIO) "OPTIMAL" else if (solvencyRatio >= 1.0) "WARNING" else "CRITICAL"
            ),
            ActuarialFormulaBreakdown(
                title = "4. Immediate Claim Payout Capacity",
                formula = "Cash Balance (${group.balance.format()}) ÷ Funeral Benefit (${benefit.format()})",
                resultText = "$maxSimultaneousClaimsCovered full claim(s) payable immediately",
                explanation = "The number of full funeral claims the group can pay right now from cash reserves without waiting for future contributions.",
                status = if (maxSimultaneousClaimsCovered >= 2) "OPTIMAL" else if (maxSimultaneousClaimsCovered >= 1) "WARNING" else "CRITICAL"
            ),
            ActuarialFormulaBreakdown(
                title = "5. Liquidity Reserve Coverage",
                formula = "Cash Balance ÷ Average Monthly Claim Load (${monthlyClaims.format()})",
                resultText = "${r(reserveCoverageMonths)} month(s) of claims covered",
                explanation = "Target is at least 3 months of claims reserve to prevent liquidity bottlenecks during claim spikes.",
                status = if (reserveCoverageMonths >= ActuarialConstants.LIQUIDITY_RESERVE_MONTHS) "OPTIMAL" else "WARNING"
            )
        )

        // 12. Warnings
        val warnings = buildList {
            if (!isSolvent) add("Critical: Solvency ratio (${r(solvencyRatio)}x) is below FSB standard (${ActuarialConstants.BURIAL_MIN_SOLVENCY_RATIO}x).")
            if (!isReserveAdequate) add("Liquidity Warning: Reserve coverage (${r(reserveCoverageMonths)} months) is below the 3-month target.")
            if (maxSimultaneousClaimsCovered < 1) add("Vulnerability Warning: Current cash balance cannot cover 1 full claim of ${benefit.format()}.")
            if (benefitAdequacy < 50.0) add("Policy Warning: Death benefit is severely below market average (R30,000).")
        }

        // 13. Plain-English Summary
        val summaryText = if (isSolvent && isReserveAdequate) {
            "Burial Society is in strong actuarial health ($n active members). Current balance of ${group.balance.format()} can payout $maxSimultaneousClaimsCovered immediate claim(s) and maintains a solvency ratio of ${r(solvencyRatio)}x (exceeding the 1.30x FSB requirement)."
        } else {
            "Burial Society requires capital adjustment ($n active members). Solvency ratio is ${r(solvencyRatio)}x (FSB Target: 1.30x). Recommended monthly contribution is ${recommendedMonthlyContribution.format()} per member to ensure long-term claim stability."
        }

        return BurialSocietyActuarialMetrics(
            expectedAnnualClaimsCount = r(expectedAnnualClaimsCount),
            purePremium = purePremiumMonthly,
            safetyLoadedPremium = safetyLoadedPremiumMonthly,
            grossPremium = grossPremiumMonthly,
            actuarialPresentValue = apv,
            solvencyRatio = r(solvencyRatio),
            capitalAdequacyPct = r(capAdequacy),
            requiredReserveAmount = requiredReserve,
            reserveCoverageMonths = r(reserveCoverageMonths),
            minimumCapitalAmount = minimumCapital,
            isSolvent = isSolvent,
            isCapitalAdequate = isCapitalAdequate,
            isReserveAdequate = isReserveAdequate,
            benefitAdequacyPct = r(benefitAdequacy),
            claimRatePerThousand = mortalityRatePerThousand,
            projectedClaimsNextYear = expectedAnnualClaimsValue,
            yearsToInsolvencyAtCurrentRate = r(yearsToInsolvency),
            maxSimultaneousClaimsCovered = maxSimultaneousClaimsCovered,
            recommendedMonthlyContributionPerMember = recommendedMonthlyContribution,
            userFriendlySummary = summaryText,
            calculationBreakdowns = breakdowns,
            warnings = warnings
        )
    }

    private fun buildRiskScore(m: BurialSocietyActuarialMetrics): Int {
        var score = 100
        if (!m.isSolvent) score -= 40
        if (!m.isReserveAdequate) score -= 30
        if (m.maxSimultaneousClaimsCovered < 1) score -= 20
        if (m.benefitAdequacyPct < 60.0) score -= 10
        return score.coerceIn(0, 100)
    }
}
