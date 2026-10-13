package com.sanibonani.save.data.repository

import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.ActuarialRepository
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.usecase.actuarial.GroupTypeActuarialEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.abs

class ActuarialRepositoryImpl @Inject constructor(
    private val groupRepo: GroupRepository,
    private val memberRepo: Provider<MemberRepository>
) : BaseRepository("ActuarialRepository"), ActuarialRepository {

    override suspend fun computeMetrics(groupId: String): Result<ActuarialMetrics> =
        withContext(Dispatchers.IO) {
            try {
                val group = groupRepo.getGroupById(groupId).getOrElse {
                    return@withContext Result.failure(it)
                }
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrElse {
                    return@withContext Result.failure(it)
                }
                withContext(Dispatchers.Default) { calculateMetrics(group, members) }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun calculateDynamicJoiningFee(groupId: String): Result<Double> =
        withContext(Dispatchers.IO) {
            runCatching {
                val group = groupRepo.getGroupById(groupId).getOrThrow()
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrThrow()
                withContext(Dispatchers.Default) {
                    val memberCount = max(1, members.count { it.status != MemberStatus.SUSPENDED })
                    val baseFee = group.joiningFee
                    val reservesPerMember = group.balance / memberCount
                    val equityContribution = reservesPerMember * 0.40
                    val dynamicFee = baseFee + equityContribution
                    roundMoney(min(dynamicFee, baseFee * 5.0))
                }
            }
        }

    override suspend fun calculateViabilityPlan(
        groupId: String,
        goalAmount: Double,
        periodMonths: Int
    ): Result<ViabilityPlan> = withContext(Dispatchers.IO) {
        kotlinx.coroutines.withTimeoutOrNull(10.seconds) {
            runCatching {
                val group = groupRepo.getGroupById(groupId).getOrThrow()
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrThrow()
                withContext(Dispatchers.Default) {
                    GroupTypeActuarialEngine.calculateViabilityPlan(group, members, goalAmount, periodMonths)
                }
            }
        } ?: Result.failure(Exception("Calculation timed out. Please try again."))
    }

    override suspend fun fetchGroupInsight(groupId: String): Result<GroupFinancialInsight> =
        withContext(Dispatchers.IO) {
            runCatching {
                val group = groupRepo.getGroupById(groupId).getOrThrow()
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrThrow()
                withContext(Dispatchers.Default) { computeGroupInsight(group, members).getOrThrow() }
            }
        }

    override fun calculateMetrics(group: Group, members: List<Member>): Result<ActuarialMetrics> = runCatching {
        val stats = getGroupStats(group, members)
        val activeList = stats.activeMembers
        val n = stats.activeMemberCount
        val totalExpectedAnnual = stats.totalExpectedAnnualContributions
        val monthsActive = stats.monthsActive

        val estimatedAnnualClaims = when (group.type) {
            GroupType.BURIAL_SOCIETY -> {
                val q = GroupTypeActuarialEngine.SA_WORKING_AGE_MORT_PER_1000 / 1000.0
                val benefit = if (group.goalAmount > 0.0) group.goalAmount
                else GroupTypeActuarialEngine.SA_AVG_FUNERAL_COST
                n * q * benefit
            }
            GroupType.STOKVEL -> n * 5_000.0
            GroupType.ROSCA   -> 0.0
            else              -> n * 10_000.0
        }

        val mortalityRatePct = if (group.type == GroupType.ROSCA) 0.0
        else GroupTypeActuarialEngine.SA_WORKING_AGE_MORT_PER_1000 / 10.0

        val coreMetrics = GroupTypeActuarialEngine.computeActuarialScalars(
            membersCount = n,
            balance = group.balance,
            mortalityRatePct = mortalityRatePct,
            avgClaim = if (n > 0) estimatedAnnualClaims / n else 0.0,
            safetyLoadingPct = GroupTypeActuarialEngine.BURIAL_SAFETY_LOADING_PCT,
            adminCostPerMember = GroupTypeActuarialEngine.ADMIN_COST_PER_MEMBER,
            annualDiscountRatePct = GroupTypeActuarialEngine.SA_REPO_RATE_PCT,
            currentPremium = if (n > 0) totalExpectedAnnual / 12.0 / n
                             else group.monthlyContribution,
            claimsPaid = estimatedAnnualClaims * 0.1,
            totalContributions = group.balance,
            paymentRatePct = stats.paymentRatePct,
            totalExpectedContributionsAnnual = totalExpectedAnnual
        )

        val cashFlowProjections = GroupTypeActuarialEngine.computeCashFlowProjections(
            group = group,
            memberCount = n,
            currentBalance = group.balance,
            paymentCompliancePct = stats.paymentRatePct
        )
        val projM3  = cashFlowProjections.getOrNull(2)?.projectedBalance ?: group.balance
        val projM6  = cashFlowProjections.getOrNull(5)?.projectedBalance ?: group.balance
        val projM12 = cashFlowProjections.lastOrNull()?.projectedBalance ?: group.balance
        val cashFlowRiskScore = cashFlowProjections.count { it.riskFlag } * 100 / cashFlowProjections.size.coerceAtLeast(1)

        val collectionEfficiencyPct = if (totalExpectedAnnual > 0.0)
            min(100.0, (group.balance / (totalExpectedAnnual / 12.0 * monthsActive.coerceAtLeast(1))) * 100.0)
        else 100.0

        val typeWarnings: List<String> = when (group.type) {
            GroupType.BURIAL_SOCIETY -> GroupTypeActuarialEngine.computeBurialSocietyMetrics(
                group, n, group.balance, totalExpectedAnnual
            ).warnings
            GroupType.STOKVEL -> GroupTypeActuarialEngine.computeStokvelMetrics(
                group, n, group.balance, totalExpectedAnnual, monthsActive
            ).warnings
            GroupType.ROSCA -> GroupTypeActuarialEngine.computeRoscaMetrics(
                group, activeList, monthsActive
            ).warnings
            GroupType.INVESTMENT_CLUB -> GroupTypeActuarialEngine.computeInvestmentMetrics(
                group, n, group.balance,
                activeList.sumOf { it.totalPaid ?: 0.0 }, monthsActive
            ).warnings
            GroupType.EMERGENCY_FUND -> GroupTypeActuarialEngine.computeEmergencyFundMetrics(
                group, n, group.balance
            ).warnings
            GroupType.TONTINE -> GroupTypeActuarialEngine.computeTontineMetrics(
                group, n, group.balance, monthsActive
            ).warnings
            else -> GroupTypeActuarialEngine.computeCommunitySavingsMetrics(
                group, n, group.balance, monthsActive
            ).warnings
        }

        val riskLevel = GroupTypeActuarialEngine.classifyRiskLevel(coreMetrics.compositeRiskScore)

        coreMetrics.copy(
            riskLevel                = riskLevel,
            cashFlowRiskScore        = cashFlowRiskScore,
            collectionEfficiencyPct  = roundMoney(collectionEfficiencyPct),
            projectedBalanceM3       = projM3,
            projectedBalanceM6       = projM6,
            projectedBalanceM12      = projM12,
            typeSpecificWarnings     = typeWarnings
        )
    }

    override fun computeGroupInsight(group: Group, members: List<Member>): Result<GroupFinancialInsight> = runCatching {
        val stats = getGroupStats(group, members)
        val activeList = stats.activeMembers
        val n = stats.activeMemberCount
        val monthsActive = stats.monthsActive
        val totalExpectedAnnual = stats.totalExpectedAnnualContributions
        val paymentRatePct = stats.paymentRatePct

        val projections = GroupTypeActuarialEngine.computeCashFlowProjections(
            group = group,
            memberCount = n,
            currentBalance = group.balance,
            paymentCompliancePct = paymentRatePct
        )

        val benchmark = GroupTypeActuarialEngine.getIndustryBenchmark(group, n, group.balance)

        val keyFindings   = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        val base = GroupFinancialInsight(
            groupId             = group.id ?: "",
            groupType           = group.type,
            monthlyProjections  = projections,
            industryBenchmark   = benchmark
        )

        when (group.type) {
            GroupType.BURIAL_SOCIETY -> {
                val m = GroupTypeActuarialEngine.computeBurialSocietyMetrics(
                    group, n, group.balance, totalExpectedAnnual
                )
                val score = buildBurialRiskScore(m)
                val riskLevel = GroupTypeActuarialEngine.classifyRiskLevel(score)
                keyFindings += "Solvency ratio: ${m.solvencyRatio} (FSB min: ${GroupTypeActuarialEngine.BURIAL_MIN_SOLVENCY_RATIO})"
                keyFindings += "Reserve coverage: ${m.reserveCoverageMonths} months"
                keyFindings += "Benefit adequacy: ${m.benefitAdequacyPct}% of market funeral cost"
                keyFindings += "Expected claims next year: R${money(m.projectedClaimsNextYear)}"
                if (!m.isSolvent) recommendations += "Increase contributions by at least ${r((m.grossPremium - group.monthlyContribution).coerceAtLeast(0.0))} to restore solvency."
                if (!m.isReserveAdequate) recommendations += "Build reserve to R${money(m.requiredReserveAmount)} (3-month claims buffer)."
                if (!m.isCapitalAdequate) recommendations += "Raise capital to R${money(m.minimumCapitalAmount)} to meet FSB 15% adequacy threshold."
                if (m.benefitAdequacyPct < 80.0) recommendations += "Consider increasing death benefit closer to R${money(GroupTypeActuarialEngine.SA_AVG_FUNERAL_COST)} (market rate)."
                else recommendations += "Contributions and benefits are well balanced — maintain current trajectory."
                base.copy(
                    riskLevel                = riskLevel,
                    statusSummary            = buildBurialStatus(m, riskLevel),
                    recommendations          = recommendations,
                    keyFindings              = keyFindings,
                    expectedAnnualClaimsCount = m.expectedAnnualClaimsCount,
                    solvencyRatio            = m.solvencyRatio,
                    capitalAdequacyPct       = m.capitalAdequacyPct,
                    requiredReserveAmount    = m.requiredReserveAmount,
                    reserveCoverageMonths    = m.reserveCoverageMonths,
                    benefitAdequacyPct       = m.benefitAdequacyPct,
                    isSolvent                = m.isSolvent,
                    isCapitalAdequate        = m.isCapitalAdequate,
                    yearsToInsolvency        = m.yearsToInsolvencyAtCurrentRate,
                    purePremiumInsight       = m.purePremium,
                    grossPremiumInsight      = m.grossPremium
                )
            }

            GroupType.INVESTMENT_CLUB -> {
                val totalContributed = activeList.sumOf { it.totalPaid ?: 0.0 }
                val m = GroupTypeActuarialEngine.computeInvestmentMetrics(
                    group, n, group.balance, totalContributed, monthsActive
                )
                val riskLevel = if (m.isOnTrackForGoal && m.sharpeRatio > 0.5) RiskLevel.LOW
                    else if (m.isOnTrackForGoal) RiskLevel.MODERATE
                    else if (m.annualisedReturnPct < 0.0) RiskLevel.HIGH
                    else RiskLevel.MODERATE
                keyFindings += "NAV per unit: R${money(m.navPerUnit)} (inception: R1.00)"
                keyFindings += "CAGR since inception: ${m.annualisedReturnPct}%"
                keyFindings += "Projected CAGR (forward): ${m.projectedCagrPct}%"
                keyFindings += "Sharpe ratio: ${m.sharpeRatio} (>1.0 = excellent)"
                keyFindings += "Capital at risk per member: R${money(m.capitalAtRiskPerMember)}"
                if (m.annualisedReturnPct < GroupTypeActuarialEngine.SA_CPI_INFLATION_PCT)
                    recommendations += "Real return is below inflation. Diversify into higher-yield instruments."
                if (m.sharpeRatio < 0.5) recommendations += "Improve risk-adjusted return. Target Sharpe > 1.0."
                recommendations += "Projected FV at ${m.projectedCagrPct}% CAGR: R${money(m.projectedFvAtCagr)}."
                base.copy(
                    riskLevel               = riskLevel,
                    statusSummary           = "Portfolio is ${if (m.isOnTrackForGoal) "on track ✅" else "off track ⚠️"} for goal. CAGR: ${m.annualisedReturnPct}% p.a.",
                    recommendations         = recommendations,
                    keyFindings             = keyFindings,
                    navPerUnit              = m.navPerUnit,
                    totalReturn             = m.totalReturnPct,
                    annualisedReturn        = m.annualisedReturnPct,
                    projectedCagr           = m.projectedCagrPct,
                    capitalAtRiskPerMember  = m.capitalAtRiskPerMember,
                    sharpeRatio             = m.sharpeRatio,
                    projectedFv             = m.projectedFvAtCagr,
                    requiresReturnPct       = m.requiresReturnPct,
                    isOnTrackForGoal        = m.isOnTrackForGoal
                )
            }

            GroupType.ROSCA -> {
                val m = GroupTypeActuarialEngine.computeRoscaMetrics(group, activeList, monthsActive)
                val riskLevel = when {
                    m.defaultRiskScore < 20 && m.cycleCompletionProbability >= 90.0 -> RiskLevel.LOW
                    m.defaultRiskScore < 50 -> RiskLevel.MODERATE
                    else -> RiskLevel.HIGH
                }
                keyFindings += "Monthly pot: R${money(m.monthlyPot)} (${m.cycleLength}-month cycle)"
                keyFindings += "Cycle position: month ${m.currentCycleMonth} of ${m.cycleLength}"
                keyFindings += "Pot completion this cycle: ${m.potCompletionPct}%"
                keyFindings += "Cycle completion probability: ${m.cycleCompletionProbability}%"
                keyFindings += "This month's payout: ${m.currentPayoutMember}"
                keyFindings += "Next payout: ${m.nextPayoutMember}"
                recommendations += "Ensure all members have signed a written agreement before each cycle."
                if (m.defaultRiskScore > 30) recommendations += "Consider requiring a surety deposit to reduce exit risk."
                if (n < 6) recommendations += "Recruiting 2-3 more members will reduce individual cycle wait time and default risk."
                base.copy(
                    riskLevel                  = riskLevel,
                    statusSummary              = "Cycle ${m.currentCycleMonth}/${m.cycleLength}. This month: ${m.currentPayoutMember}. Next: ${m.nextPayoutMember}.",
                    recommendations            = recommendations,
                    keyFindings                = keyFindings,
                    monthlyPot                 = m.monthlyPot,
                    cycleLength                = m.cycleLength,
                    currentCycleMonth          = m.currentCycleMonth,
                    potCompletionPct           = m.potCompletionPct,
                    defaultRiskScore           = m.defaultRiskScore,
                    cycleCompletionProbability = m.cycleCompletionProbability,
                    currentPayoutMember        = m.currentPayoutMember,
                    nextPayoutMember           = m.nextPayoutMember,
                    welfareGainEarlyReceiver   = m.welfareGainEarlyReceiver
                )
            }

            GroupType.STOKVEL -> {
                val m = GroupTypeActuarialEngine.computeStokvelMetrics(
                    group, n, group.balance, totalExpectedAnnual, monthsActive
                )
                val riskLevel = when {
                    m.savingsEfficiencyScore >= 80 -> RiskLevel.LOW
                    m.savingsEfficiencyScore >= 55 -> RiskLevel.MODERATE
                    else -> RiskLevel.HIGH
                }
                keyFindings += "Pot milestone: ${m.potMilestonePct}% toward annual target"
                keyFindings += "Projected payout per member: R${money(m.projectedPayoutPerMember)}"
                keyFindings += "Payment compliance: ${m.paymentCompliancePct}%"
                keyFindings += "vs NASASA average: ${if (m.benchmarkVsNasasaAvg >= 0.0) "+" else ""}${m.benchmarkVsNasasaAvg}%"
                if (m.paymentCompliancePct < 90.0)
                    recommendations += "Chase outstanding contributions. Send reminders 5 days before due date."
                recommendations += "December payout projection: R${money(m.projectedPayoutPerMember)} per member."
                if (m.benchmarkVsNasasaAvg < 0.0)
                    recommendations += "Payout is below SA average. Consider increasing monthly contribution."
                base.copy(
                    riskLevel                = riskLevel,
                    statusSummary            = "Savings at ${m.potMilestonePct}% of target. Payout in ${m.monthsToTarget} months.",
                    recommendations          = recommendations,
                    keyFindings              = keyFindings,
                    totalProjectedFund       = m.totalProjectedFund,
                    projectedPayoutPerMember = m.projectedPayoutPerMember,
                    potMilestonePct          = m.potMilestonePct,
                    paymentCompliancePct     = m.paymentCompliancePct,
                    benchmarkVsNasasaAvg     = m.benchmarkVsNasasaAvg,
                    savingsEfficiencyScore   = m.savingsEfficiencyScore,
                    annualContributionTarget = m.annualContributionTarget
                )
            }

            GroupType.EMERGENCY_FUND -> {
                val m = GroupTypeActuarialEngine.computeEmergencyFundMetrics(group, n, group.balance)
                val riskLevel = when {
                    m.isMeetingTarget -> RiskLevel.LOW
                    m.coverageMonths >= 3.0 -> RiskLevel.MODERATE
                    else -> RiskLevel.HIGH
                }
                keyFindings += "Coverage: ${m.coverageMonths} months (target: ${m.targetCoverageMonths.toInt()} months)"
                keyFindings += "Gap to target: R${money(m.coverageGap)}"
                keyFindings += "Replenishment rate: ${m.replenishmentRatePct}%/month toward target"
                recommendations += if (m.isMeetingTarget) "✅ Emergency fund meets the 6-month coverage standard."
                    else "Build fund to R${money(m.coverageGap)} more to reach ${m.targetCoverageMonths.toInt()}-month target (~${m.monthsToTarget} months)."
                base.copy(
                    riskLevel               = riskLevel,
                    statusSummary           = "${if (m.isMeetingTarget) "✅" else "⚠️"} Coverage: ${m.coverageMonths}/${m.targetCoverageMonths.toInt()} months (${if (m.isMeetingTarget) "Target met" else "Building"}).",
                    recommendations         = recommendations,
                    keyFindings             = keyFindings,
                    coverageMonths          = m.coverageMonths,
                    targetCoverageMonths    = m.targetCoverageMonths,
                    coverageGap             = m.coverageGap,
                    isMeetingTarget         = m.isMeetingTarget,
                    monthsToEmergencyTarget = m.monthsToTarget
                )
            }

            GroupType.TONTINE -> {
                val m = GroupTypeActuarialEngine.computeTontineMetrics(group, n, group.balance, monthsActive)
                val riskLevel = if (m.mortalityAdjustedYieldPct >= 10.0) RiskLevel.LOW
                    else if (m.mortalityAdjustedYieldPct >= 0.0) RiskLevel.MODERATE
                    else RiskLevel.HIGH
                keyFindings += "Current share per member: R${money(m.currentSharePerMember)}"
                keyFindings += "Projected share at end: R${money(m.projectedShareAtEnd)}"
                keyFindings += "Expected survivors: ${m.expectedSurvivors} of $n"
                keyFindings += "Mortality-adjusted yield: ${m.mortalityAdjustedYieldPct}%"
                recommendations += "Maintain regular contributions to maximise survivor-benefit accumulation."
                if (n < 10) recommendations += "Recruit more members to reduce individual survivor-benefit variance."
                base.copy(
                    riskLevel                = riskLevel,
                    statusSummary            = "Projected per-survivor payout: R${money(m.projectedShareAtEnd)} in ${m.timeToProjectedPayoutMonths} months.",
                    recommendations          = recommendations,
                    keyFindings              = keyFindings,
                    currentSharePerMember    = m.currentSharePerMember,
                    projectedShareAtEnd      = m.projectedShareAtEnd,
                    expectedSurvivors        = m.expectedSurvivors,
                    mortalityAdjustedYield   = m.mortalityAdjustedYieldPct
                )
            }

            GroupType.COMMUNITY_SAVINGS -> {
                val m = GroupTypeActuarialEngine.computeCommunitySavingsMetrics(
                    group, n, group.balance, monthsActive
                )
                val riskLevel = when {
                    m.goalProgressPct >= 80.0 -> RiskLevel.LOW
                    m.goalProgressPct >= 40.0 -> RiskLevel.MODERATE
                    else -> RiskLevel.HIGH
                }
                keyFindings += "Goal progress: ${m.goalProgressPct}%"
                keyFindings += "Savings per member: R${money(m.savingsPerMember)}"
                keyFindings += "Annual dividend projection (money-market): R${money(m.annualDividendProjection)}"
                recommendations += m.projectedGoalReachDescription
                recommendations += "Channel pooled community savings into a low-risk money-market account to preserve liquidity and earn yield."
                base.copy(
                    riskLevel                = riskLevel,
                    statusSummary            = "Community goal is ${m.goalProgressPct}% funded. ${m.projectedGoalReachDescription}.",
                    recommendations          = recommendations,
                    keyFindings              = keyFindings,
                    savingsPerMember         = m.savingsPerMember,
                    annualDividendProjection = m.annualDividendProjection,
                    growthRatePct            = m.growthRatePct,
                    goalProgressPct          = m.goalProgressPct
                )
            }

            else -> {
                val m = GroupTypeActuarialEngine.computeCommunitySavingsMetrics(
                    group, n, group.balance, monthsActive
                )
                val riskLevel = when {
                    m.goalProgressPct >= 75.0 -> RiskLevel.LOW
                    m.goalProgressPct >= 35.0 -> RiskLevel.MODERATE
                    else -> RiskLevel.HIGH
                }
                keyFindings += "Custom goal progress: ${m.goalProgressPct}%"
                keyFindings += "Savings per member: R${money(m.savingsPerMember)}"
                keyFindings += "Annual dividend projection (money-market): R${money(m.annualDividendProjection)}"
                recommendations += m.projectedGoalReachDescription
                recommendations += "Review this custom group's purpose and tune contributions, fees, and payout rules to match the actual savings objective."
                recommendations += "Keep surplus funds in a low-risk interest-bearing account until the group adopts a more specific operating model."
                base.copy(
                    riskLevel                = riskLevel,
                    statusSummary            = "Custom savings group is ${m.goalProgressPct}% toward its configured target. ${m.projectedGoalReachDescription}.",
                    recommendations          = recommendations,
                    keyFindings              = keyFindings,
                    savingsPerMember         = m.savingsPerMember,
                    annualDividendProjection = m.annualDividendProjection,
                    growthRatePct            = m.growthRatePct,
                    goalProgressPct          = m.goalProgressPct
                )
            }
        }
    }

    override fun calculateMemberContribution(group: Group, member: Member): Double =
        PaymentCalculator.calculateMonthlyContribution(group, member)

    private fun getGroupStats(group: Group, members: List<Member>): GroupTypeActuarialEngine.GroupStats {
        val activeList = members.filter {
            it.status == MemberStatus.ACTIVE || it.status == MemberStatus.PROBATION
        }
        val n = max(1, activeList.size)
        val totalExpectedAnnual = activeList.sumOf { calculateMemberContribution(group, it) } * 12
        val monthsActive = estimateMonthsActive(group, members)
        val paymentRatePct = if (totalExpectedAnnual > 0)
            min(100.0, (group.balance / totalExpectedAnnual) * 100.0)
        else 100.0

        return GroupTypeActuarialEngine.GroupStats(
            activeMemberCount = n,
            monthsActive = monthsActive,
            totalExpectedAnnualContributions = totalExpectedAnnual,
            paymentRatePct = paymentRatePct,
            activeMembers = activeList
        )
    }

    private fun estimateMonthsActive(group: Group, members: List<Member>): Int {
        val dateStr = group.createdAt
            ?: members.mapNotNull { it.joinedAt }.minOrNull()
        return try {
            val created = LocalDate.parse(dateStr?.substringBefore("T") ?: "")
            ChronoUnit.MONTHS.between(created, LocalDate.now()).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            12
        }
    }

    private fun buildBurialRiskScore(m: GroupTypeActuarialEngine.BurialSocietyMetrics): Int {
        var score = 100
        if (!m.isSolvent)          score -= 40
        if (!m.isCapitalAdequate)  score -= 25
        if (!m.isReserveAdequate)  score -= 20
        if (m.benefitAdequacyPct < 60.0) score -= 15
        return score.coerceIn(0, 100)
    }

    private fun buildBurialStatus(
        m: GroupTypeActuarialEngine.BurialSocietyMetrics,
        riskLevel: RiskLevel
    ): String {
        val solvencyLabel = if (m.isSolvent) "✅ Solvent" else "❌ Below solvency"
        val reserveLabel  = if (m.isReserveAdequate) "✅ Reserve adequate" else "⚠️ Reserve low"
        return "$solvencyLabel | $reserveLabel | Ratio: ${m.solvencyRatio} | ${riskLevel.displayName}"
    }

    private fun r(v: Double): Double = roundMoney(v)

    private fun roundMoney(value: Double): Double = round(value * 100.0) / 100.0

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", roundMoney(value))
}
