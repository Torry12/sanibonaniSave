package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculator
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.*

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modern actuarial calculation engine for South African savings groups.
 * Orchestrates group-specific strategies using high-precision financial logic.
 */
@Singleton
class GroupTypeActuarialEngine @Inject constructor(
    private val paymentCalculator: PaymentCalculator,
) {

    /**
     * Maps a group type to its specific actuarial logic.
     */
    fun getStrategy(type: GroupType): ActuarialStrategy = when (type) {
        GroupType.BURIAL_SOCIETY -> BurialSocietyStrategy()
        GroupType.STOKVEL -> StokvelStrategy()
        GroupType.ROSCA -> RoscaStrategy()
        GroupType.INVESTMENT_CLUB -> InvestmentClubStrategy()
        GroupType.EMERGENCY_FUND -> EmergencyFundStrategy()
        GroupType.COMMUNITY_SAVINGS -> DefaultActuarialStrategy()
        GroupType.HYBRID_SAVINGS_GROUP -> HybridSavingsStrategy()
        else -> DefaultActuarialStrategy()
    }

    // ── Public API Facade ───────────────────────────────────────────────────

    /**
     * Calculates core actuarial metrics (Solvency, Pure Premium, Risk Score).
     */
    fun calculateMetrics(group: Group, members: List<Member>): ActuarialMetrics {
        val stats = getGroupStats(group, members)
        return getStrategy(group.type).calculateMetrics(group, members, stats)
    }

    /**
     * Generates a comprehensive financial insight report with projections and benchmarks.
     */
    fun computeGroupInsight(group: Group, members: List<Member>): GroupFinancialInsight {
        val stats = getGroupStats(group, members)
        val projections = computeCashFlowProjections(group, stats.activeMemberCount, group.balance, stats.paymentRatePct)
        val benchmark = getIndustryBenchmark(group, stats.activeMemberCount, group.balance)
        return getStrategy(group.type).computeInsight(group, members, stats, projections, benchmark)
    }

    /**
     * Aggregates member-level data into group statistics for actuarial analysis.
     */
    fun getGroupStats(group: Group, members: List<Member>): ActuarialGroupStats {
        val activeList = members.filter {
            (it.status == MemberStatus.ACTIVE) || (it.status == MemberStatus.PROBATION)
        }
        val n = max(1, activeList.size)
        val totalExpectedAnnual = activeList.fold(Money.ZERO) { acc, member ->
            acc + paymentCalculator.calculateExpectedMemberGroupPart(group, member)
        } * 12
        val monthsActive = estimateMonthsActive(group, members)

        // Payment rate is balance vs expected contributions over life of group
        val paymentRatePct = if (totalExpectedAnnual.isPositive) {
            val totalExpectedToDate = (totalExpectedAnnual / 12.0) * monthsActive.toDouble()
            if (totalExpectedToDate.isPositive) min(100.0, group.balance.ratio(totalExpectedToDate) * 100.0) else 100.0
        } else 100.0

        return ActuarialGroupStats(
            activeMemberCount = n,
            monthsActive = monthsActive,
            totalExpectedAnnualContributions = totalExpectedAnnual,
            paymentRatePct = paymentRatePct,
            activeMembers = activeList,
        )
    }

    private fun estimateMonthsActive(group: Group, members: List<Member>): Int {
        val dateStr = group.createdAt ?: members.asSequence().mapNotNull { it.joinedAt }.minOrNull()
        return try {
            val created = LocalDate.parse(dateStr?.substringBefore("T") ?: "")
            ChronoUnit.MONTHS.between(created, LocalDate.now()).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            12
        }
    }

    // ── Group-type detailed metrics ─────────────────────────────────────────

    fun computeBurialSocietyMetrics(group: Group, members: List<Member>, monthsActive: Int, totalExpected: Money): BurialSocietyActuarialMetrics {
        return BurialSocietyStrategy().computeDetailedMetrics(group, members, monthsActive, totalExpected)
    }

    fun computeStokvelMetrics(group: Group, members: List<Member>, monthsActive: Int, totalExpected: Money): StokvelActuarialMetrics {
        return StokvelStrategy().computeDetailedMetrics(group, members, monthsActive, totalExpected)
    }

    fun computeRoscaMetrics(group: Group, members: List<Member>, monthsActive: Int): RoscaActuarialMetrics {
        return RoscaStrategy().computeDetailedMetrics(group, members, monthsActive)
    }

    fun computeInvestmentMetrics(group: Group, members: List<Member>, monthsActive: Int, totalContributed: Money): InvestmentClubMetrics {
        return InvestmentClubStrategy().computeDetailedMetrics(group, members, monthsActive, totalContributed)
    }

    fun computeEmergencyFundMetrics(group: Group, members: List<Member>): EmergencyFundMetrics {
        return EmergencyFundStrategy().computeDetailedMetrics(group, members)
    }

    fun computeCommunitySavingsMetrics(group: Group, members: List<Member>, monthsActive: Int): CommunitySavingsMetrics {
        return DefaultActuarialStrategy().computeDetailedMetrics(group, members, monthsActive, Money.ZERO)
    }

    fun computeCashFlowProjections(group: Group, memberCount: Int, currentBalance: Money, paymentCompliancePct: Double, months: Int = 12): List<FutureMonthProjection> {
        val n = memberCount.coerceAtLeast(1)
        val compliancePct = paymentCompliancePct.coerceIn(0.0, 100.0)
        val monthlyInflow = (group.monthlyContribution * n).percent(compliancePct)

        val monthlyOutflow = when (group.type) {
            GroupType.BURIAL_SOCIETY -> (if (group.goalAmount.isPositive) group.goalAmount else ActuarialConstants.SA_AVG_FUNERAL_COST) * (n * ActuarialConstants.SA_WORKING_AGE_MORT_PER_1000 / 1000.0 / 12.0) + monthlyInflow.percent(5)
            GroupType.ROSCA -> group.monthlyContribution * n
            GroupType.STOKVEL, GroupType.INVESTMENT_CLUB -> monthlyInflow.percent(2)
            else -> monthlyInflow.percent(3)
        }

        var runningBalance = currentBalance
        return (1..months).map { m ->
            val netFlow = monthlyInflow - monthlyOutflow
            runningBalance = (runningBalance + netFlow).max(Money.ZERO)
            FutureMonthProjection(
                month = m,
                label = "Month $m",
                projectedBalance = runningBalance,
                inflow = monthlyInflow,
                outflow = monthlyOutflow,
                netFlow = netFlow,
                riskFlag = runningBalance < (monthlyOutflow * ActuarialConstants.LIQUIDITY_RESERVE_MONTHS)
            )
        }
    }

    fun calculateViabilityPlan(
        group: Group,
        members: List<Member>,
        goalAmount: Money,
        periodMonths: Int,
        actuarialSettings: ActuarialSettings = ActuarialSettings()
    ): ViabilityPlan = getStrategy(group.type).calculateViabilityPlan(group, members, goalAmount, periodMonths, actuarialSettings)
    fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Money): IndustryBenchmark = getStrategy(group.type).getIndustryBenchmark(group, memberCount, balance)
}
