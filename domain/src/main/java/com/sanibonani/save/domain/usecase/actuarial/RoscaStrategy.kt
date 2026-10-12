package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.*
import kotlin.math.*

/**
 * Strategy for ROSCA (Rotating Savings and Credit Associations).
 * Focuses on cycle completion, default risk, and welfare gains.
 */
class RoscaStrategy : BaseActuarialStrategy() {

    override fun calculateMetrics(
        engine: GroupTypeActuarialEngine,
        group: Group,
        members: List<Member>,
        stats: ActuarialGroupStats
    ): ActuarialMetrics {
        val n = stats.activeMemberCount
        val core = engine.computeActuarialScalars(
            membersCount = n,
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
        val detailed = computeDetailedMetrics(group, members, stats.monthsActive)

        val keyMetrics = listOf(
            ActuarialFormulaBreakdown(
                title = "Payment Compliance",
                resultText = if (stats.paymentRatePct > 0) "${r(stats.paymentRatePct)}%" else "96% (Median)",
                status = if (stats.paymentRatePct >= 90.0) "OK" else "LOW"
            ),
            ActuarialFormulaBreakdown(
                title = "Cycle Prob. (Completion)",
                resultText = if (detailed.cycleCompletionProbability > 0) "${r(detailed.cycleCompletionProbability * 100.0)}%" else "92% (Median)",
                status = if (detailed.cycleCompletionProbability >= 0.85) "OK" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "Monthly Pot (Est)",
                resultText = if (detailed.monthlyPot.isPositive) detailed.monthlyPot.format() else (group.monthlyContribution * stats.activeMemberCount).format(),
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
        val m = computeDetailedMetrics(group, members, stats.monthsActive)
        val riskLevel = commonRiskLevel(m.defaultRiskScore)

        val keyFindings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        keyFindings += "Monthly pot: ${m.monthlyPot.format()}"
        keyFindings += "Cycle progress: ${m.currentCycleMonth}/${m.cycleLength}"
        keyFindings += "Completion probability: ${r(m.cycleCompletionProbability * 100.0)}%"

        if (m.cycleCompletionProbability < 0.90) {
            recommendations += "Default Risk: High probability of cycle disruption. Consider adding a small welfare levy to cover shortfalls."
        }
        recommendations += "Payout Sequence: Ensure the next payout member (${m.nextPayoutMember}) is fully KYC-verified before disbursement."

        val breakdowns = listOf(
            ActuarialFormulaBreakdown(
                title = "1. Monthly Cycle Pot",
                formula = "Pot = MonthlyContribution × ActiveMembers",
                resultText = m.monthlyPot.format(),
                explanation = "The total cash amount collected and disbursed to one member every month.",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "2. Cycle Completion Probability",
                formula = "P(Completion) = (1 - MonthlyDefaultRate)^N",
                resultText = "${r(m.cycleCompletionProbability * 100.0)}% probability",
                explanation = "The statistical likelihood that all members will complete the current rotation without a default.",
                status = if (m.cycleCompletionProbability >= 0.90) "OK" else "WARNING"
            ),
            ActuarialFormulaBreakdown(
                title = "3. Economic Welfare Gain (Time Value)",
                formula = "Gain = Pot × [PrimeRate × (N - Position) / 12]",
                resultText = "${m.welfareGainEarlyReceiver.format()} (Early) vs ${m.welfareGainLateReceiver.format()} (Late)",
                explanation = "Measures the implicit interest-free credit benefit gained by members who receive the pot early in the cycle.",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "4. Rotational Progress",
                formula = "Progress = (CurrentMonth / TotalMembers) × 100",
                resultText = "${r(m.potCompletionPct)}% through cycle",
                explanation = "Current position in the rotation. High progress reduces remaining credit risk for early receivers.",
                status = "OK"
            )
        )

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = riskLevel,
            statusSummary = "Cycle is ${m.potCompletionPct}% complete. Next payout: ${m.nextPayoutMember}.",
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark,
            monthlyPot = m.monthlyPot,
            cycleLength = m.cycleLength,
            currentCycleMonth = m.currentCycleMonth,
            currentPayoutMember = m.currentPayoutMember,
            nextPayoutMember = m.nextPayoutMember,
            calculationBreakdowns = breakdowns
        )
    }

    private fun computeDetailedMetrics(group: Group, members: List<Member>, monthsActive: Int): RoscaActuarialMetrics {
        val activeMembers = members.filter { it.status == MemberStatus.ACTIVE || it.status == MemberStatus.PROBATION }
        val n = activeMembers.size.coerceAtLeast(1)
        val monthlyPot = group.monthlyContribution * n
        val cycleMonth = (monthsActive % n).let { if (it == 0) n else it }

        val primeRate = ActuarialConstants.SA_PRIME_RATE_PCT / 100.0
        val welfareGainEarly = monthlyPot * (primeRate * (n - cycleMonth) / 12.0)
        val welfareGainLate = -monthlyPot * (primeRate * cycleMonth / 12.0)

        // Probability of completion P = (1 - default_rate)^n
        val pDefault = 0.02 // 2% assumed monthly default rate
        val pCompletion = (1.0 - pDefault).pow(n.toDouble())

        return RoscaActuarialMetrics(
            monthlyPot = monthlyPot,
            cycleLength = n,
            currentCycleMonth = cycleMonth,
            potCompletionPct = r((cycleMonth.toDouble() / n) * 100.0),
            welfareGainEarlyReceiver = welfareGainEarly,
            welfareGainLateReceiver = welfareGainLate,
            defaultRiskScore = (pCompletion * 100).toInt(),
            expectedDefaultsPerCycle = n * pDefault,
            cycleCompletionProbability = pCompletion,
            currentPayoutMember = activeMembers.getOrNull(cycleMonth - 1)?.fullName ?: "Unknown",
            nextPayoutMember = activeMembers.getOrNull(cycleMonth % n)?.fullName ?: "End of Cycle",
            warnings = emptyList()
        )
    }

    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        return IndustryBenchmark(
            benchmarkType = "ROSCA (Kholisano/Stokvel Standard)",
            industryAvgContribution = Money(1000.0),
            industryAvgBalance = Money.ZERO,
            industryPaymentRatePct = 96.0,
            benchmarkNotes = "Aims for 100% redistribution with zero retained balance."
        )
    }

    override fun computeMetricsLegacyInternal(
        engine: GroupTypeActuarialEngine,
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpected: Money
    ): RoscaActuarialMetrics {
        return computeDetailedMetrics(group, members, monthsActive)
    }
}
