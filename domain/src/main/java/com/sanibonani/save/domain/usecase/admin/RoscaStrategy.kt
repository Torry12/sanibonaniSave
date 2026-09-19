package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.system.sortRoscaParticipants
import kotlin.math.*

class RoscaStrategy : ActuarialStrategy {
    override fun calculateMetrics(group: Group, members: List<Member>, stats: ActuarialGroupStats): ActuarialMetrics {
        val n = stats.activeMemberCount
        val core = GroupTypeActuarialEngine.computeActuarialScalars(
            membersCount = n,
            balance = group.balance,
            mortalityRatePct = 0.0,
            avgClaim = Money.ZERO,
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
            m.defaultRiskScore < 20 && m.cycleCompletionProbability >= 90.0 -> RiskLevel.LOW
            m.defaultRiskScore < 50 -> RiskLevel.MODERATE
            else -> RiskLevel.HIGH
        }

        val keyFindings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        keyFindings += "Monthly pot: ${m.monthlyPot.format()} (${m.cycleLength}-month cycle)"
        keyFindings += "Cycle position: month ${m.currentCycleMonth} of ${m.cycleLength}"
        keyFindings += "Pot completion this cycle: ${m.potCompletionPct}%"
        keyFindings += "Cycle completion probability: ${m.cycleCompletionProbability}%"
        keyFindings += "This month's payout: ${m.currentPayoutMember}"
        keyFindings += "Next payout: ${m.nextPayoutMember}"

        recommendations += "Ensure all members have signed a written agreement before each cycle."
        if (m.defaultRiskScore > 30) recommendations += "Consider requiring a signature/deposit to reduce exit risk."
        if (n < 6) recommendations += "Recruiting 2-3 more members will reduce individual cycle wait time and default risk."

        return GroupFinancialInsight(
            groupId = group.id ?: "",
            groupType = group.type,
            riskLevel = riskLevel,
            statusSummary = "Cycle ${m.currentCycleMonth}/${m.cycleLength}. This month: ${m.currentPayoutMember}. Next: ${m.nextPayoutMember}.",
            recommendations = recommendations,
            keyFindings = keyFindings,
            monthlyProjections = projections,
            industryBenchmark = benchmark
        )
    }

    private fun computeDetailedMetrics(group: Group, members: List<Member>, monthsActive: Int, totalExpectedAnnualContributions: Money): RoscaActuarialMetrics {
        val participants = members.filter { it.status != MemberStatus.SUSPENDED }
        val n = participants.size.coerceAtLeast(1)
        val r = ActuarialStrategy.SA_PRIME_RATE_PCT / 100.0 / 12.0
        val monthlyPot = group.monthlyContribution * n
        val currentCycleMonth = if (monthsActive <= 0) 1 else ((monthsActive - 1) % n) + 1
        val potCompletionPct = (currentCycleMonth - 1).toDouble() / n * 100.0

        val pvSavingAlone = group.monthlyContribution * (1.0 - (1.0 + r).pow(-n.toDouble())) / r
        val pvEarly = monthlyPot / (1.0 + r)
        val welfareGainEarly = (pvEarly - pvSavingAlone).max(Money.ZERO)
        val pDefault = 0.05
        val defaultRiskScore = (pDefault * 1000.0).toInt().coerceIn(0, 100)

        val sortedMembers = sortRoscaParticipants(group, participants)
        val currentPayoutMember = sortedMembers.getOrNull(currentCycleMonth - 1)?.fullName ?: "Unknown"
        val nextPayoutMember = sortedMembers.getOrNull(currentCycleMonth % n)?.fullName ?: "Unknown"

        return RoscaActuarialMetrics(
            monthlyPot = monthlyPot,
            cycleLength = n,
            currentCycleMonth = currentCycleMonth,
            potCompletionPct = ActuarialStrategy.r(potCompletionPct),
            welfareGainEarlyReceiver = welfareGainEarly,
            welfareGainLateReceiver = Money.ZERO,
            defaultRiskScore = defaultRiskScore,
            expectedDefaultsPerCycle = n * pDefault,
            cycleCompletionProbability = (1.0 - pDefault).pow(n.toDouble()) * 100.0,
            currentPayoutMember = currentPayoutMember,
            nextPayoutMember = nextPayoutMember,
            warnings = emptyList()
        )
    }

    override fun calculateViabilityPlan(group: Group, members: List<Member>, goalAmount: Money, periodMonths: Int): ViabilityPlan = ActuarialMathUtils.calculatePlanLogic(group, members, goalAmount, periodMonths)
    override fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark {
        val avg = 400.0
        val groupAvg = if (memberCount > 0) group.monthlyContribution.toDouble() else 0.0
        val vsBenchmark = if (avg > 0) ((groupAvg / avg) - 1.0) * 100.0 else 0.0
        return IndustryBenchmark(
            benchmarkType = "ROSCA / Umgalelo (Local Standard)",
            industryAvgContribution = avg,
            industryAvgBalance = 0.0,
            industryPaymentRatePct = 98.0,
            groupVsBenchmarkPct = ActuarialStrategy.r(vsBenchmark),
            benchmarkNotes = "ROSCA cycles typically see very high compliance (98%+) due to social pressure."
        )
    }

    fun computeMetricsLegacyInternal(group: Group, members: List<Member>, monthsActive: Int, totalExpected: Money): RoscaActuarialMetrics {
        return computeDetailedMetrics(group, members, monthsActive, totalExpected)
    }
}
