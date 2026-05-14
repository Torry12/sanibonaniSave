package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.actuarial.GroupTypeActuarialEngine
import com.sanibonani.save.data.utils.PaymentCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.*

class ActuarialRepositoryImpl @Inject constructor(
    private val groupRepo: GroupRepository,
    private val memberRepo: Provider<MemberRepository>
) : BaseRepository("ActuarialRepository"), ActuarialRepository {

    data class ActuarialScalarInput(
        val membersCount: Int,
        val balance: Double,
        val mortalityRatePct: Double,
        val avgClaim: Double,
        val safetyLoadingPct: Double,
        val adminCostPerMember: Double,
        val annualDiscountRatePct: Double,
        val currentPremium: Double,
        val claimsPaid: Double,
        val totalContributions: Double,
        val paymentRatePct: Double,
        val totalExpectedContributionsAnnual: Double
    )

    // ══════════════════════════════════════════════════════════════════════
    //  Public async API
    // ══════════════════════════════════════════════════════════════════════

    override suspend fun computeMetrics(groupId: String): Result<ActuarialMetrics> =
        withContext(Dispatchers.IO) {
            try {
                val group = groupRepo.getGroupById(groupId).getOrElse {
                    return@withContext Result.failure(it)
                }
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrElse {
                    return@withContext Result.failure(it)
                }
                val metrics = withContext(Dispatchers.Default) { calculateMetrics(group, members) }
                Result.success(metrics)
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
                    // Equity buy-in: 40% of accumulated reserve share
                    val reservesPerMember = group.balance / memberCount
                    val equityContribution = reservesPerMember * 0.40
                    val dynamicFee = baseFee + equityContribution
                    // Cap at 5× base fee to keep accessible for the community
                    roundMoney(min(dynamicFee, baseFee * 5.0))
                }
            }
        }

    override suspend fun calculateViabilityPlan(
        groupId: String,
        goalAmount: Double,
        periodMonths: Int
    ): Result<ViabilityPlan> = withContext(Dispatchers.IO) {
        kotlinx.coroutines.withTimeoutOrNull(10_000) {
            runCatching {
                val group = groupRepo.getGroupById(groupId).getOrThrow()
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrThrow()
                withContext(Dispatchers.Default) { buildViabilityPlan(group, members, goalAmount, periodMonths) }
            }
        } ?: Result.failure(Exception("Calculation timed out. Please try again."))
    }

    override suspend fun fetchGroupInsight(groupId: String): Result<GroupFinancialInsight> =
        withContext(Dispatchers.IO) {
            runCatching {
                val group = groupRepo.getGroupById(groupId).getOrThrow()
                val members = memberRepo.get().getGroupMembers(groupId).first().getOrThrow()
                withContext(Dispatchers.Default) { computeGroupInsight(group, members) }
            }
        }

    // ══════════════════════════════════════════════════════════════════════
    //  Pure synchronous calculation methods
    // ══════════════════════════════════════════════════════════════════════

    override fun calculateMetrics(group: Group, members: List<Member>): ActuarialMetrics {
        val activeList = members.filter {
            it.status == MemberStatus.ACTIVE || it.status == MemberStatus.PROBATION
        }
        val n = max(1, activeList.size)
        val totalExpectedAnnual = activeList.sumOf { calculateMemberContribution(group, it) } * 12
        val monthsActive = estimateMonthsActive(group, members)

        // ── Payment-rate ──────────────────────────────────────────────
        val paymentRatePct = if (totalExpectedAnnual > 0)
            min(100.0, (group.balance / totalExpectedAnnual) * 100.0)
        else 100.0

        // ── Type-specific claim assumptions ──────────────────────────
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
        else GroupTypeActuarialEngine.SA_WORKING_AGE_MORT_PER_1000 / 10.0  // expressed as %

        // ── Core actuarial scalars ────────────────────────────────────
        val coreMetrics = computeActuarialScalars(
            membersCount = n,
            balance = group.balance,
            mortalityRatePct = mortalityRatePct,
            avgClaim = if (n > 0) estimatedAnnualClaims / n else 0.0,
            safetyLoadingPct = GroupTypeActuarialEngine.BURIAL_SAFETY_LOADING_PCT,
            adminCostPerMember = 25.0,
            annualDiscountRatePct = GroupTypeActuarialEngine.SA_REPO_RATE_PCT,
            currentPremium = if (n > 0) totalExpectedAnnual / 12.0 / n
                             else group.monthlyContribution,
            claimsPaid = estimatedAnnualClaims * 0.1,
            totalContributions = group.balance,
            paymentRatePct = paymentRatePct,
            totalExpectedContributionsAnnual = totalExpectedAnnual
        )

        // ── Extended fields: cash-flow projections ────────────────────
        val cashFlowProjections = GroupTypeActuarialEngine.computeCashFlowProjections(
            group = group,
            memberCount = n,
            currentBalance = group.balance,
            paymentCompliancePct = paymentRatePct
        )
        val projM3  = cashFlowProjections.getOrNull(2)?.projectedBalance ?: group.balance
        val projM6  = cashFlowProjections.getOrNull(5)?.projectedBalance ?: group.balance
        val projM12 = cashFlowProjections.lastOrNull()?.projectedBalance ?: group.balance
        val cashFlowRiskScore = cashFlowProjections.count { it.riskFlag } * 100 / cashFlowProjections.size.coerceAtLeast(1)

        // ── Collection efficiency ─────────────────────────────────────
        val collectionEfficiencyPct = if (totalExpectedAnnual > 0.0)
            min(100.0, (group.balance / (totalExpectedAnnual / 12.0 * monthsActive.coerceAtLeast(1))) * 100.0)
        else 100.0

        // ── Type-specific warnings (surface from engine) ──────────────
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

        return coreMetrics.copy(
            riskLevel                = riskLevel,
            cashFlowRiskScore        = cashFlowRiskScore,
            collectionEfficiencyPct  = roundMoney(collectionEfficiencyPct),
            projectedBalanceM3       = projM3,
            projectedBalanceM6       = projM6,
            projectedBalanceM12      = projM12,
            typeSpecificWarnings     = typeWarnings
        )
    }

    override fun computeGroupInsight(group: Group, members: List<Member>): GroupFinancialInsight {
        val activeList = members.filter {
            it.status == MemberStatus.ACTIVE || it.status == MemberStatus.PROBATION
        }
        val n = max(1, activeList.size)
        val monthsActive = estimateMonthsActive(group, members)
        val totalExpectedAnnual = activeList.sumOf { calculateMemberContribution(group, it) } * 12
        val paymentRatePct = if (totalExpectedAnnual > 0.0)
            min(100.0, (group.balance / totalExpectedAnnual) * 100.0)
        else 100.0

        // ── 12-month cash-flow projections ────────────────────────────
        val projections = GroupTypeActuarialEngine.computeCashFlowProjections(
            group = group,
            memberCount = n,
            currentBalance = group.balance,
            paymentCompliancePct = paymentRatePct
        )

        // ── Industry benchmark ────────────────────────────────────────
        val benchmark = GroupTypeActuarialEngine.getIndustryBenchmark(group, n, group.balance)

        // ── Type-specific analytics ───────────────────────────────────
        val keyFindings   = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        // Start with a partially-built insight, then add type fields
        val base = GroupFinancialInsight(
            groupId             = group.id ?: "",
            groupType           = group.type,
            monthlyProjections  = projections,
            industryBenchmark   = benchmark
        )

        return when (group.type) {
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

            else -> {  // COMMUNITY_SAVINGS, OTHER
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
                recommendations += "${m.projectedGoalReachDescription}"
                recommendations += "Deposit pooled savings into a money-market account to earn interest."
                base.copy(
                    riskLevel                = riskLevel,
                    statusSummary            = "Goal: ${m.goalProgressPct}% achieved. ${m.projectedGoalReachDescription}.",
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

    // ══════════════════════════════════════════════════════════════════════
    //  Core actuarial math (generic – used by calculateMetrics)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Computes all scalar actuarial indicators from raw parameters.
     * Pure function – no side effects.
     *
     * Renamed from `computeMetrics` to `computeActuarialScalars` to avoid
     * ambiguity with the public suspend override [computeMetrics(groupId)].
     */
    fun computeActuarialScalars(
        membersCount: Int,
        balance: Double,
        mortalityRatePct: Double,
        avgClaim: Double,
        safetyLoadingPct: Double,
        adminCostPerMember: Double,
        annualDiscountRatePct: Double,
        currentPremium: Double,
        claimsPaid: Double,
        totalContributions: Double,
        paymentRatePct: Double,
        totalExpectedContributionsAnnual: Double
    ): ActuarialMetrics = computeActuarialScalars(
        ActuarialScalarInput(
            membersCount = membersCount,
            balance = balance,
            mortalityRatePct = mortalityRatePct,
            avgClaim = avgClaim,
            safetyLoadingPct = safetyLoadingPct,
            adminCostPerMember = adminCostPerMember,
            annualDiscountRatePct = annualDiscountRatePct,
            currentPremium = currentPremium,
            claimsPaid = claimsPaid,
            totalContributions = totalContributions,
            paymentRatePct = paymentRatePct,
            totalExpectedContributionsAnnual = totalExpectedContributionsAnnual
        )
    )

    fun computeActuarialScalars(input: ActuarialScalarInput): ActuarialMetrics {
        val sanitizedMembers = input.membersCount.coerceAtLeast(1)
        val sanitizedBalance = input.balance.coerceAtLeast(0.0)
        val sanitizedMortalityPct = input.mortalityRatePct.coerceIn(0.0, 100.0)
        val sanitizedAvgClaim = input.avgClaim.coerceAtLeast(0.0)
        val sanitizedSafetyLoadingPct = input.safetyLoadingPct.coerceAtLeast(0.0)
        val sanitizedAdminCost = input.adminCostPerMember.coerceAtLeast(0.0)
        val sanitizedDiscountPct = input.annualDiscountRatePct.coerceAtLeast(0.0)
        val sanitizedCurrentPremium = input.currentPremium.coerceAtLeast(0.0)
        val sanitizedClaimsPaid = input.claimsPaid.coerceAtLeast(0.0)
        val sanitizedTotalContributions = input.totalContributions.coerceAtLeast(0.0)
        val sanitizedPaymentRatePct = input.paymentRatePct.coerceIn(0.0, 100.0)
        val sanitizedExpectedAnnualContrib = input.totalExpectedContributionsAnnual.coerceAtLeast(0.0)

        val q = sanitizedMortalityPct / 100.0
        val expectedAnnualClaims = sanitizedMembers * q * sanitizedAvgClaim

        val i = sanitizedDiscountPct / 100.0
        val v = 1.0 / (1.0 + i)
        val actuarialPresentValue = expectedAnnualClaims * v

        val purePremium = expectedAnnualClaims / sanitizedMembers
        val safetyLoading = purePremium * (sanitizedSafetyLoadingPct / 100.0)
        val grossPremium = purePremium + safetyLoading + sanitizedAdminCost

        val reserveAdequacyPct = if (expectedAnnualClaims > 0)
            (sanitizedBalance / expectedAnnualClaims) * 100.0 else 1_000.0
        val solvencyMarginPct = ((sanitizedBalance + sanitizedTotalContributions - sanitizedClaimsPaid) /
            max(1.0, expectedAnnualClaims)) * 100.0
        val lossRatioPct = if (sanitizedTotalContributions > 0)
            (sanitizedClaimsPaid / sanitizedTotalContributions) * 100.0 else 0.0

        val contributionSufficiencyPct = if (grossPremium > 0)
            (sanitizedCurrentPremium / grossPremium) * 100.0 else 0.0
        val breakEvenDenominator = sanitizedCurrentPremium - purePremium - safetyLoading
        val breakEvenMembers = if (breakEvenDenominator <= 0.0) Int.MAX_VALUE
            else ceil((sanitizedAdminCost * sanitizedMembers) / breakEvenDenominator).toInt()

        val fundingRatioPct = if (actuarialPresentValue > 0)
            (sanitizedBalance / actuarialPresentValue) * 100.0 else 1_000.0

        val score = (
            min(100.0, reserveAdequacyPct * 0.3) +
            min(100.0, contributionSufficiencyPct * 0.4) +
            min(100.0, sanitizedPaymentRatePct * 0.3)
        ).roundToInt().coerceIn(0, 100)

        val monthlyNetFlow = (sanitizedExpectedAnnualContrib / 12.0 * (sanitizedPaymentRatePct / 100.0)) -
            (expectedAnnualClaims / 12.0) -
            (sanitizedMembers * sanitizedAdminCost / 12.0)
        val insolvencyMonths = if (monthlyNetFlow >= 0.0) Int.MAX_VALUE
            else (sanitizedBalance / abs(monthlyNetFlow)).toInt().coerceAtLeast(0)

        return ActuarialMetrics(
            purePremium                 = roundMoney(purePremium),
            grossPremium                = roundMoney(grossPremium),
            reserveAdequacyPct          = roundMoney(reserveAdequacyPct),
            solvencyMarginPct           = roundMoney(solvencyMarginPct),
            lossRatioPct                = roundMoney(lossRatioPct),
            contributionSufficiencyPct  = roundMoney(contributionSufficiencyPct),
            breakEvenMembers            = breakEvenMembers,
            actuarialPresentValue       = roundMoney(actuarialPresentValue),
            fundingRatioPct             = roundMoney(fundingRatioPct),
            paymentRatePct              = roundMoney(sanitizedPaymentRatePct),
            compositeRiskScore          = score,
            insolvencyMonths            = insolvencyMonths,
            expectedAnnualClaims        = roundMoney(expectedAnnualClaims)
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Viability plan builder (private)
    // ══════════════════════════════════════════════════════════════════════

    private fun buildViabilityPlan(
        group: Group,
        members: List<Member>,
        goalAmount: Double,
        periodMonths: Int
    ): ViabilityPlan {
        val activeMembers = members.count {
            it.status == MemberStatus.ACTIVE || it.status == MemberStatus.PROBATION
        }
        val n = max(1, activeMembers)
        val messages = mutableListOf<String>()

        val effectiveGoal   = if (goalAmount > 0) goalAmount else group.goalAmount
        val effectivePeriod = if (periodMonths > 0) periodMonths else max(1, group.periodMonths)
        val baseMonthly = effectiveGoal / (n * effectivePeriod)
        val activeRatio = if (members.isEmpty()) 1.0 else n.toDouble() / members.size.toDouble()
        val inflationAdjustmentFactor = 1.0 +
            ((GroupTypeActuarialEngine.SA_CPI_INFLATION_PCT / 100.0) * (effectivePeriod / 12.0))

        // Persist explicit factors on the plan so UI diagnostics match the actual math path.
        var claimReadinessFactor = 1.0
        var mortalityBufferFactor = 1.0
        var reserveAdequacyFactor = 1.0
        var marketReturnPremiumFactor = 1.0
        var volatilityHaircutFactor = 1.0
        var collectionEfficiencyFactor = 1.0
        var festivePayoutPressureFactor = 1.0
        var defaultRiskFactor = 1.0
        var cycleSlippageFactor = 1.0
        var withdrawalPressureFactor = 1.0
        var inflationSafetyFactor = 1.0
        var survivorUncertaintyFactor = 1.0
        var horizonCompoundingFactor = 1.0
        var goalStretchRatio = 1.0
        var growthConservatismFactor = 1.0

        // ── Type-specific monthly suggestion ─────────────────────────
        val suggestedMonthly = when (group.type) {
            GroupType.BURIAL_SOCIETY -> {
                mortalityBufferFactor = 1.0 +
                    (GroupTypeActuarialEngine.SA_WORKING_AGE_MORT_PER_1000 / 1000.0)
                val reserveCoverageMonths = if (n > 0 && group.monthlyContribution > 0.0) {
                    (group.balance / (group.monthlyContribution * n)).coerceAtLeast(0.0)
                } else {
                    0.0
                }
                reserveAdequacyFactor = if (reserveCoverageMonths >= 3.0) 1.0 else 1.15
                claimReadinessFactor = 1.25
                val adjusted = baseMonthly * claimReadinessFactor * mortalityBufferFactor * reserveAdequacyFactor
                messages += "Burial Society factors: claim_readiness=${roundMoney(claimReadinessFactor)}, mortality_buffer=${roundMoney(mortalityBufferFactor)}, reserve_adequacy=${roundMoney(reserveAdequacyFactor)}."
                adjusted
            }
            GroupType.INVESTMENT_CLUB -> {
                val rate = GroupTypeActuarialEngine.SA_REPO_RATE_PCT / 100.0 / 12.0
                val denom = (Math.pow(1.0 + rate, effectivePeriod.toDouble()) - 1.0) / rate
                marketReturnPremiumFactor = 0.93
                volatilityHaircutFactor = if (effectivePeriod < 18) 1.10 else 1.04
                val adjusted = (effectiveGoal / (n * denom)) * marketReturnPremiumFactor * volatilityHaircutFactor
                messages += "Investment Club factors: annual_rate=${GroupTypeActuarialEngine.SA_REPO_RATE_PCT}%, return_premium=${roundMoney(marketReturnPremiumFactor)}, volatility_haircut=${roundMoney(volatilityHaircutFactor)}."
                adjusted
            }
            GroupType.STOKVEL -> {
                collectionEfficiencyFactor = if (group.allowPartialPayment) 0.98 else 1.0
                festivePayoutPressureFactor = if (effectivePeriod >= 11) 1.03 else 1.0
                val adjusted = baseMonthly * festivePayoutPressureFactor / collectionEfficiencyFactor
                messages += "Stokvel factors: collection_efficiency=${roundMoney(collectionEfficiencyFactor)}, payout_pressure=${roundMoney(festivePayoutPressureFactor)}."
                adjusted
            }
            GroupType.ROSCA -> {
                val baseDefault = GroupTypeActuarialEngine.ROSCA_DEFAULT_RISK_BASE_PCT / 100.0
                val participationPenalty = (1.0 - activeRatio).coerceAtLeast(0.0) * 0.30
                val cycleLengthPenalty = (n - 12).coerceAtLeast(0) * 0.003
                defaultRiskFactor = 1.0 + baseDefault + participationPenalty + cycleLengthPenalty
                cycleSlippageFactor = when {
                    activeRatio >= 0.95 -> 1.0
                    activeRatio >= 0.85 -> 1.03
                    else -> 1.07
                }
                val adjusted = baseMonthly * defaultRiskFactor * cycleSlippageFactor
                messages += "ROSCA factors: default_risk=${roundMoney(defaultRiskFactor)} (base=${roundMoney(baseDefault)}, participation_penalty=${roundMoney(participationPenalty)}, cycle_length_penalty=${roundMoney(cycleLengthPenalty)}), cycle_slippage=${roundMoney(cycleSlippageFactor)}, active_ratio=${roundMoney(activeRatio * 100.0)}%."
                messages += "ROSCA: Monthly pot = R${money(adjusted * n)} (${n} members)."
                adjusted
            }
            GroupType.EMERGENCY_FUND -> {
                val target = group.monthlyContribution * n * GroupTypeActuarialEngine.EMERGENCY_TARGET_MONTHS
                withdrawalPressureFactor = if (group.autoSuspendAfter <= 1) 1.10 else 1.03
                inflationSafetyFactor = inflationAdjustmentFactor
                val emergMonthly = (target / (n * effectivePeriod)) * withdrawalPressureFactor * inflationSafetyFactor
                messages += "Emergency Fund factors: withdrawal_pressure=${roundMoney(withdrawalPressureFactor)}, inflation_safety=${roundMoney(inflationSafetyFactor)}."
                messages += "Emergency Fund: Target is ${GroupTypeActuarialEngine.EMERGENCY_TARGET_MONTHS.toInt()} months of pooled expenses."
                emergMonthly
            }
            GroupType.TONTINE -> {
                survivorUncertaintyFactor = if (n < 10) 1.08 else 1.03
                horizonCompoundingFactor = if (effectivePeriod >= 24) 0.97 else 1.0
                val adjusted = baseMonthly * survivorUncertaintyFactor * horizonCompoundingFactor
                messages += "Tontine factors: survivor_uncertainty=${roundMoney(survivorUncertaintyFactor)}, horizon_compounding=${roundMoney(horizonCompoundingFactor)}."
                messages += "Tontine: Contributions accumulate for ${effectivePeriod/12} years; survivor benefit grows as membership thins."
                adjusted
            }
            else -> {
                goalStretchRatio = if (group.balance > 0.0) {
                    (effectiveGoal / group.balance).coerceAtLeast(1.0)
                } else {
                    1.2
                }
                growthConservatismFactor = if (goalStretchRatio > 4.0) 1.10 else 1.03
                val adjusted = baseMonthly * growthConservatismFactor
                messages += "Savings Group factors: goal_stretch_ratio=${roundMoney(goalStretchRatio)}, conservatism=${roundMoney(growthConservatismFactor)}."
                messages += "Savings Group: Linear projection over $effectivePeriod months."
                adjusted
            }
        }

        val initialContribution = when (group.type) {
            GroupType.BURIAL_SOCIETY -> suggestedMonthly * 2  // 2-month upfront reserve
            else -> suggestedMonthly
        }

        // ── Projected value (base linear & compound) ──────────────────
        val projectionRetentionFactor = when (group.type) {
            GroupType.ROSCA -> (0.99 - ((1.0 - activeRatio).coerceAtLeast(0.0) * 0.08)).coerceIn(0.90, 0.99)
            GroupType.BURIAL_SOCIETY -> 0.99
            GroupType.EMERGENCY_FUND -> 0.99
            GroupType.STOKVEL -> if (group.allowPartialPayment) 0.98 else 1.0
            else -> 1.0
        }
        val projectedValue = n * suggestedMonthly * effectivePeriod * projectionRetentionFactor
        val rate = GroupTypeActuarialEngine.SA_REPO_RATE_PCT / 100.0 / 12.0
        val compoundedBase = if (rate > 0.0)
            n * suggestedMonthly * ((Math.pow(1.0 + rate, effectivePeriod.toDouble()) - 1.0) / rate)
        else projectedValue
        val compoundedProjectedValue = compoundedBase * projectionRetentionFactor

        // Scenario analysis: ±15% contribution compliance
        val optimistic   = n * (suggestedMonthly * 1.15) * effectivePeriod * projectionRetentionFactor
        val pessimistic  = n * (suggestedMonthly * 0.85) * effectivePeriod * projectionRetentionFactor

        // ── Shortfall & break-even ────────────────────────────────────
        val shortfall = (effectiveGoal - projectedValue).coerceAtLeast(0.0)
        val requiredMonthly = if (n > 0 && effectivePeriod > 0)
            effectiveGoal / (n * effectivePeriod) else suggestedMonthly
        val breakEvenMonths = if (n > 0 && suggestedMonthly > 0.0)
            ceil(group.balance / (suggestedMonthly * n)).toInt()
        else effectivePeriod

        if (n < 5)
            messages += "Warning: Low member count ($n) increases individual burden. Recruiting more members reduces monthly cost."

        return ViabilityPlan(
            initialContribution            = roundMoney(initialContribution),
            suggestedMonthlyContribution   = roundMoney(suggestedMonthly),
            projectedValue                 = roundMoney(projectedValue),
            isViable                       = compoundedProjectedValue >= effectiveGoal,
            goalAmount                     = effectiveGoal,
            periodMonths                   = effectivePeriod,
            messages                       = messages,
            requiredMonthlyToMeetGoal      = roundMoney(requiredMonthly),
            shortfallAmount                = roundMoney(shortfall),
            breakEvenMonths                = breakEvenMonths,
            compoundedProjectedValue       = roundMoney(compoundedProjectedValue),
            optimisticProjectedValue       = roundMoney(optimistic),
            pessimisticProjectedValue      = roundMoney(pessimistic),
            activeMemberRatio              = roundMoney(activeRatio),
            inflationAdjustmentFactor      = roundMoney(inflationAdjustmentFactor),
            projectionRetentionFactor      = roundMoney(projectionRetentionFactor),
            claimReadinessFactor           = roundMoney(claimReadinessFactor),
            mortalityBufferFactor          = roundMoney(mortalityBufferFactor),
            reserveAdequacyFactor          = roundMoney(reserveAdequacyFactor),
            marketReturnPremiumFactor      = roundMoney(marketReturnPremiumFactor),
            volatilityHaircutFactor        = roundMoney(volatilityHaircutFactor),
            collectionEfficiencyFactor     = roundMoney(collectionEfficiencyFactor),
            festivePayoutPressureFactor    = roundMoney(festivePayoutPressureFactor),
            defaultRiskFactor              = roundMoney(defaultRiskFactor),
            cycleSlippageFactor            = roundMoney(cycleSlippageFactor),
            withdrawalPressureFactor       = roundMoney(withdrawalPressureFactor),
            inflationSafetyFactor          = roundMoney(inflationSafetyFactor),
            survivorUncertaintyFactor      = roundMoney(survivorUncertaintyFactor),
            horizonCompoundingFactor       = roundMoney(horizonCompoundingFactor),
            goalStretchRatio               = roundMoney(goalStretchRatio),
            growthConservatismFactor       = roundMoney(growthConservatismFactor)
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════

    /** Estimate months since group formation from createdAt or earliest member joinedAt. */
    private fun estimateMonthsActive(group: Group, members: List<Member>): Int {
        val dateStr = group.createdAt
            ?: members.mapNotNull { it.joinedAt }.minOrNull()
        return try {
            val created = LocalDate.parse(dateStr?.substringBefore("T") ?: "")
            ChronoUnit.MONTHS.between(created, LocalDate.now()).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            12  // default 1 year if no date available
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
}

// ── Module-level helpers ───────────────────────────────────────────────────────
private fun roundMoney(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0

private fun money(value: Double): String = String.format(Locale.US, "%.2f", roundMoney(value))

