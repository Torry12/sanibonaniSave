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
        return getStrategy(group.type).calculateMetrics(this, group, members, stats)
    }

    /**
     * Generates a comprehensive financial insight report with projections and benchmarks.
     */
    fun computeGroupInsight(group: Group, members: List<Member>): GroupFinancialInsight {
        val stats = getGroupStats(group, members)
        val projections = computeCashFlowProjections(group, stats.activeMemberCount, group.balance, stats.paymentRatePct)
        val benchmark = getIndustryBenchmark(group, stats.activeMemberCount, group.balance)
        return getStrategy(group.type).computeInsight(this, group, members, stats, projections, benchmark)
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
        } * 12.0
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

    // ── Legacy Support ─────────────────────────────────────────────────────

    fun computeBurialSocietyMetrics(group: Group, members: List<Member>, monthsActive: Int, totalExpected: Money): BurialSocietyActuarialMetrics {
        return BurialSocietyStrategy().computeMetricsLegacyInternal(this, group, members, monthsActive, totalExpected) as BurialSocietyActuarialMetrics
    }

    fun computeStokvelMetrics(group: Group, members: List<Member>, monthsActive: Int, totalExpected: Money): StokvelActuarialMetrics {
        return StokvelStrategy().computeMetricsLegacyInternal(this, group, members, monthsActive, totalExpected) as StokvelActuarialMetrics
    }

    fun computeRoscaMetrics(group: Group, members: List<Member>, monthsActive: Int): RoscaActuarialMetrics {
        return RoscaStrategy().computeMetricsLegacyInternal(this, group, members, monthsActive, Money.ZERO) as RoscaActuarialMetrics
    }

    fun computeInvestmentMetrics(group: Group, members: List<Member>, monthsActive: Int, totalContributed: Money): InvestmentClubMetrics {
        return InvestmentClubStrategy().computeMetricsLegacyInternal(this, group, members, monthsActive, totalContributed) as InvestmentClubMetrics
    }

    fun computeEmergencyFundMetrics(group: Group, members: List<Member>): EmergencyFundMetrics {
        return EmergencyFundStrategy().computeMetricsLegacyInternal(this, group, members, 0, Money.ZERO) as EmergencyFundMetrics
    }



    fun computeCommunitySavingsMetrics(group: Group, members: List<Member>, monthsActive: Int): CommunitySavingsMetrics {
        return DefaultActuarialStrategy().computeMetricsLegacyInternal(this, group, members, monthsActive, Money.ZERO) as CommunitySavingsMetrics
    }

    /**
     * Core math aggregator for strategy implementations.
     */
    fun computeActuarialScalars(
        membersCount: Int,
        balance: Money,
        mortalityRatePct: Double,
        avgClaim: Money,
        safetyLoadingPct: Double,
        adminCostPerMemberMonthly: Money,
        annualDiscountRatePct: Double,
        currentPremiumMonthly: Money,
        claimsPaid: Money,
        totalContributions: Money,
        paymentRatePct: Double,
        totalExpectedContributionsAnnual: Money,
        defaultAnnualExpectedClaim: Money = Money.ZERO
    ): ActuarialMetrics {
        val n = membersCount.coerceAtLeast(1)
        val q = max(0.0, mortalityRatePct) / 100.0

        val calculatedAnnualClaims = avgClaim.max(Money.ZERO) * (n * q)
        val expectedAnnualClaims = if (calculatedAnnualClaims.isPositive) {
            calculatedAnnualClaims
        } else if (defaultAnnualExpectedClaim.isPositive) {
            defaultAnnualExpectedClaim
        } else {
            Money.ZERO
        }
        val apv = ActuarialMath.calculateAPV(expectedAnnualClaims, annualDiscountRatePct, 1.0)

        val purePremiumMonthly = (expectedAnnualClaims / n) / 12.0
        val safetyLoadingMonthly = purePremiumMonthly * (safetyLoadingPct / 100.0)
        val grossPremiumMonthly = purePremiumMonthly + safetyLoadingMonthly + adminCostPerMemberMonthly

        val reserveAdequacyPct = if (expectedAnnualClaims.isPositive) balance.ratio(expectedAnnualClaims) * 100.0 else 1000.0
        val contributionSufficiencyPct = if (grossPremiumMonthly.isPositive) currentPremiumMonthly.ratio(grossPremiumMonthly) * 100.0 else 0.0

        val score = ActuarialMath.calculateCompositeRiskScore(
            reserveAdequacyPct = reserveAdequacyPct,
            contributionSufficiencyPct = contributionSufficiencyPct,
            paymentCompliancePct = paymentRatePct
        )

        val monthlyInflow = (totalExpectedContributionsAnnual / 12.0) * (paymentRatePct / 100.0)
        val monthlyClaimsOutflow = expectedAnnualClaims / 12.0
        val monthlyAdminOutflow = adminCostPerMemberMonthly * n
        val monthlyNetFlow = monthlyInflow - monthlyClaimsOutflow - monthlyAdminOutflow
        val insolvencyMonths = ActuarialMath.calculateInsolvencyMonths(balance, -monthlyNetFlow)

        val marginAmtMonthly = currentPremiumMonthly - purePremiumMonthly - safetyLoadingMonthly
        val breakEvenMembers = if (marginAmtMonthly.isPositive) ceil((adminCostPerMemberMonthly * n).ratio(marginAmtMonthly)).toInt() else Int.MAX_VALUE

        return ActuarialMetrics(
            purePremium = purePremiumMonthly,
            grossPremium = grossPremiumMonthly,
            reserveAdequacyPct = ActuarialMath.round(reserveAdequacyPct),
            solvencyMarginPct = ActuarialMath.round(if (expectedAnnualClaims.isPositive) ((balance + totalContributions) - claimsPaid).ratio(expectedAnnualClaims) * 100.0 else 100.0),
            lossRatioPct = ActuarialMath.round(if (totalContributions.isPositive) claimsPaid.ratio(totalContributions) * 100.0 else 0.0),
            contributionSufficiencyPct = ActuarialMath.round(contributionSufficiencyPct),
            breakEvenMembers = breakEvenMembers,
            actuarialPresentValue = apv,
            fundingRatioPct = ActuarialMath.round(if (apv.isPositive) balance.ratio(apv) * 100.0 else 1000.0),
            paymentRatePct = ActuarialMath.round(paymentRatePct),
            compositeRiskScore = score,
            riskLevel = classifyRiskLevel(score),
            insolvencyMonths = insolvencyMonths,
            expectedAnnualClaims = expectedAnnualClaims
        )
    }

    fun classifyRiskLevel(score: Int): RiskLevel = when {
        score >= 80 -> RiskLevel.LOW
        score >= 55 -> RiskLevel.MODERATE
        score >= 30 -> RiskLevel.HIGH
        else -> RiskLevel.CRITICAL
    }

    fun computeCashFlowProjections(group: Group, memberCount: Int, currentBalance: Money, paymentCompliancePct: Double, months: Int = 12): List<FutureMonthProjection> {
        val n = memberCount.coerceAtLeast(1)
        val compliance = paymentCompliancePct.coerceIn(0.0, 100.0) / 100.0
        val monthlyInflow = group.monthlyContribution * n * compliance

        val monthlyOutflow = when (group.type) {
            GroupType.BURIAL_SOCIETY -> (if (group.goalAmount.isPositive) group.goalAmount else Money(ActuarialConstants.SA_AVG_FUNERAL_COST)) * (n * ActuarialConstants.SA_WORKING_AGE_MORT_PER_1000 / 1000.0 / 12.0) + monthlyInflow * 0.05
            GroupType.ROSCA -> group.monthlyContribution * n
            GroupType.STOKVEL, GroupType.INVESTMENT_CLUB -> monthlyInflow * 0.02
            else -> monthlyInflow * 0.03
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
