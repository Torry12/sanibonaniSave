package com.sanibonani.save.domain.usecase.actuarial

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.rosca.sortRoscaParticipants

/**
 * Industry-standard actuarial calculation engine for South African savings groups.
 *
 * References:
 *  - FSCA Friendly Societies Act 25 of 1956 & FSB Prudential Standards
 *  - NASASA Guidelines for Stokvels (2024)
 *  - Besley, Coate & Loury ROSCA welfare model (1993)
 *  - JSE / NASAA Investment Club standards
 *  - SA Reserve Bank (SARB) macro-economic data (2024/2025)
 *  - StatsSA Funeral Cost & Household Expenditure Surveys
 */
object GroupTypeActuarialEngine {

    // ── SA Macro-economic Constants ────────────────────────────────────────
    /** SARB repo rate (2025) */
    const val SA_REPO_RATE_PCT = 8.25
    /** SA prime lending rate = repo + 3.5 */
    const val SA_PRIME_RATE_PCT = 11.75
    /** SA CPI inflation target centre */
    const val SA_CPI_INFLATION_PCT = 5.5
    /** SA 10-year government bond yield (risk-free proxy) */
    const val SA_RISK_FREE_RATE_PCT = 9.5
    /** JSE equity risk premium over risk-free */
    const val SA_EQUITY_RISK_PREMIUM_PCT = 6.5

    // ── Burial Society Standards (FSCA / FSB Prudential) ──────────────────
    /** Deaths per 1,000 working-age SA adults (ages 25-55), StatsSA */
    const val SA_WORKING_AGE_MORT_PER_1000 = 8.2
    /** Average SA funeral cost (StatsSA 2024) */
    const val SA_AVG_FUNERAL_COST = 30_000.0
    /** FSB minimum solvency ratio */
    const val BURIAL_MIN_SOLVENCY_RATIO = 1.30
    /** Minimum reserve: 3 months of expected annual claims */
    const val BURIAL_MIN_RESERVE_MONTHS = 3.0
    /** FSB capital adequacy requirement: 15% of annual expected claims */
    const val BURIAL_CAPITAL_ADEQUACY_PCT = 15.0
    /** Standard actuarial safety loading */
    const val BURIAL_SAFETY_LOADING_PCT = 35.0

    // ── Stokvel / ROSCA Standards (NASASA 2024) ───────────────────────────
    /** SA average stokvel membership size */
    const val STOKVEL_AVG_MEMBERS = 38.0
    /** SA average monthly contribution per stokvel member */
    const val STOKVEL_AVG_MONTHLY_CONTRIB = 150.0
    /** ROSCA base early-exit / default risk per cycle */
    const val ROSCA_DEFAULT_RISK_BASE_PCT = 2.5

    // ── Emergency Fund (SA Financial Planning) ────────────────────────────
    /** Target coverage: 6 months of pooled expenses */
    const val EMERGENCY_TARGET_MONTHS = 6.0

    // ── Community Savings / Other ─────────────────────────────────────────
    /** Conservative SA money-market equivalent annual rate */
    const val COMMUNITY_SAVINGS_RATE_PCT = 6.0

    // ── Tontine ───────────────────────────────────────────────────────────
    /** Discount rate used for tontine NPV calculations */
    const val TONTINE_DISCOUNT_RATE_PCT = 10.0

    // ══════════════════════════════════════════════════════════════════════
    //  BURIAL SOCIETY  –  FSCA / FSB Prudential Standard
    // ══════════════════════════════════════════════════════════════════════

    data class BurialSocietyMetrics(
        /** E(N) = n × q */
        val expectedAnnualClaimsCount: Double,
        /** Net premium (before safety loading) */
        val purePremium: Double,
        /** P × (1 + θ) */
        val safetyLoadedPremium: Double,
        /** P × (1 + θ) + admin cost */
        val grossPremium: Double,
        /** APV = E(S) × discount factor */
        val actuarialPresentValue: Double,
        /** Total assets / (liabilities + provisions);  FSB minimum: 1.30 */
        val solvencyRatio: Double,
        /** Free capital as % of risk-weighted assets;  FSB minimum: 15% */
        val capitalAdequacyPct: Double,
        /** 3-month claims reserve requirement */
        val requiredReserveAmount: Double,
        /** How many months the current balance covers expected claims */
        val reserveCoverageMonths: Double,
        /** Minimum required capital = 15% × annual expected claims */
        val minimumCapitalAmount: Double,
        val isSolvent: Boolean,
        val isCapitalAdequate: Boolean,
        val isReserveAdequate: Boolean,
        /** (Actual benefit / market funeral cost) × 100 */
        val benefitAdequacyPct: Double,
        /** Normalised per-1,000 claim rate */
        val claimRatePerThousand: Double,
        val projectedClaimsNextYear: Double,
        /** -1.0 if indefinitely solvent */
        val yearsToInsolvencyAtCurrentRate: Double,
        val warnings: List<String>
    )

    fun computeBurialSocietyMetrics(
        group: Group,
        memberCount: Int,
        balance: Double,
        totalExpectedAnnualContributions: Double
    ): BurialSocietyMetrics {
        val n = memberCount.coerceAtLeast(1)
        val q = SA_WORKING_AGE_MORT_PER_1000 / 1000.0
        val benefitAmount = if (group.goalAmount > 0) group.goalAmount else SA_AVG_FUNERAL_COST

        // ── Claims estimation (Poisson: E[N] = n·q) ───────────────────
        val expectedAnnualClaimsCount = n * q
        val expectedAnnualClaimsValue = expectedAnnualClaimsCount * benefitAmount

        // ── Premium calculations (net-premium basis) ──────────────────
        val i = SA_REPO_RATE_PCT / 100.0
        val d = i / (1.0 + i)                                     // annual discount rate
        val apv = expectedAnnualClaimsValue * (1.0 - d)           // APV (1-year Makeham)

        val purePremium = if (n > 0) expectedAnnualClaimsValue / n else 0.0
        val safetyLoading = purePremium * (BURIAL_SAFETY_LOADING_PCT / 100.0)
        val adminCost = group.monthlyContribution * 0.05           // 5% admin loading
        val safetyLoadedPremium = purePremium + safetyLoading
        val grossPremium = safetyLoadedPremium + adminCost

        // ── Solvency ratio ────────────────────────────────────────────
        val periodYears = group.periodMonths.coerceAtLeast(12).toDouble() / 12.0
        val expectedFutureClaimsNpv = apv * periodYears
        val solvencyRatio = if (expectedFutureClaimsNpv > 0.0)
            (balance + totalExpectedAnnualContributions) / expectedFutureClaimsNpv
        else
            BURIAL_MIN_SOLVENCY_RATIO * 2.0

        // ── Capital adequacy ──────────────────────────────────────────
        val minimumCapitalAmount = expectedAnnualClaimsValue * (BURIAL_CAPITAL_ADEQUACY_PCT / 100.0)
        val capitalAdequacyPct = if (expectedAnnualClaimsValue > 0.0)
            (balance / expectedAnnualClaimsValue) * 100.0
        else 100.0

        // ── Reserve coverage ──────────────────────────────────────────
        val monthlyExpectedClaims = expectedAnnualClaimsValue / 12.0
        val requiredReserveAmount = monthlyExpectedClaims * BURIAL_MIN_RESERVE_MONTHS
        val reserveCoverageMonths = if (monthlyExpectedClaims > 0.0)
            balance / monthlyExpectedClaims
        else
            BURIAL_MIN_RESERVE_MONTHS * 4.0

        val benefitAdequacyPct = (benefitAmount / SA_AVG_FUNERAL_COST) * 100.0
        val claimRatePerThousand = if (n > 0) expectedAnnualClaimsCount / n * 1000.0 else 0.0

        val isCapitalAdequate = balance >= minimumCapitalAmount
        val isReserveAdequate = reserveCoverageMonths >= BURIAL_MIN_RESERVE_MONTHS
        val isSolvent = solvencyRatio >= BURIAL_MIN_SOLVENCY_RATIO

        // ── Insolvency runway projection ──────────────────────────────
        val adminCostTotal = adminCost * n * 12.0
        val annualNetFlow = totalExpectedAnnualContributions - expectedAnnualClaimsValue - adminCostTotal
        val yearsToInsolvency = if (annualNetFlow >= 0.0) -1.0
        else (-balance / annualNetFlow).coerceAtLeast(0.0)

        val warnings = buildList {
            if (!isSolvent)
                add("⚠️ Below FSB minimum solvency ratio (${r(solvencyRatio)} < $BURIAL_MIN_SOLVENCY_RATIO).")
            if (!isCapitalAdequate)
                add("⚠️ Capital adequacy below 15% regulatory minimum. Required: R${r(minimumCapitalAmount)}.")
            if (!isReserveAdequate)
                add("⚠️ Reserve covers ${r(reserveCoverageMonths)} months (min: ${BURIAL_MIN_RESERVE_MONTHS} months).")
            if (benefitAdequacyPct < 60.0)
                add("⚠️ Benefit R${r(benefitAmount)} is below 60% of average SA funeral cost (R${SA_AVG_FUNERAL_COST.toInt()}).")
            if (yearsToInsolvency in 0.0..3.0)
                add("⚠️ Projected insolvency in ${r(yearsToInsolvency)} years at current rate. Increase contributions.")
        }

        return BurialSocietyMetrics(
            expectedAnnualClaimsCount = r(expectedAnnualClaimsCount),
            purePremium               = r(purePremium),
            safetyLoadedPremium       = r(safetyLoadedPremium),
            grossPremium              = r(grossPremium),
            actuarialPresentValue     = r(apv),
            solvencyRatio             = r(solvencyRatio),
            capitalAdequacyPct        = r(capitalAdequacyPct),
            requiredReserveAmount     = r(requiredReserveAmount),
            reserveCoverageMonths     = r(reserveCoverageMonths),
            minimumCapitalAmount      = r(minimumCapitalAmount),
            isSolvent                 = isSolvent,
            isCapitalAdequate         = isCapitalAdequate,
            isReserveAdequate         = isReserveAdequate,
            benefitAdequacyPct        = r(benefitAdequacyPct),
            claimRatePerThousand      = r(claimRatePerThousand),
            projectedClaimsNextYear   = r(expectedAnnualClaimsValue),
            yearsToInsolvencyAtCurrentRate = yearsToInsolvency,
            warnings                  = warnings
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INVESTMENT CLUB  –  JSE / NASAA standard
    // ══════════════════════════════════════════════════════════════════════

    data class InvestmentClubMetrics(
        /** Total portfolio NAV (current balance) */
        val netAssetValue: Double,
        /** NAV / total units; each R1 contributed = 1 unit */
        val navPerUnit: Double,
        /** (NAV - cost) / cost × 100  (unrealised + realised) */
        val totalReturnPct: Double,
        /** CAGR since inception (%) */
        val annualisedReturnPct: Double,
        /** Forward-looking CAGR blend (historical weight + market premium) (%) */
        val projectedCagrPct: Double,
        /** 95% VaR proxy: 30% of per-member NAV */
        val capitalAtRiskPerMember: Double,
        /** (return - RFR) / estimated vol (simplified) */
        val sharpeRatio: Double,
        /** FV at projectedCagrPct over remaining period */
        val projectedFvAtCagr: Double,
        /** Required annual return to reach goal (%) */
        val requiresReturnPct: Double,
        val isOnTrackForGoal: Boolean,
        val warnings: List<String>
    )

    fun computeInvestmentMetrics(
        group: Group,
        memberCount: Int,
        balance: Double,
        totalContributed: Double,
        monthsActive: Int
    ): InvestmentClubMetrics {
        val n = memberCount.coerceAtLeast(1)
        val periodYears = (monthsActive.coerceAtLeast(1)).toDouble() / 12.0

        // ── Historical return ─────────────────────────────────────────
        val totalReturnPct = if (totalContributed > 0.0)
            ((balance - totalContributed) / totalContributed) * 100.0
        else 0.0

        // ── CAGR: ((FV/PV)^(1/t)) – 1 ────────────────────────────────
        val annualisedReturn = if (periodYears > 0.0 && totalContributed > 0.0 && balance > 0.0)
            (balance / totalContributed).pow(1.0 / periodYears) - 1.0
        else 0.0  // expressed as decimal

        // ── Forward CAGR: blend historical with market premium ────────
        val historicalConfidence = min(1.0, periodYears / 3.0)
        val marketCagr = (SA_RISK_FREE_RATE_PCT + SA_EQUITY_RISK_PREMIUM_PCT) / 100.0
        val projectedCagr = annualisedReturn * historicalConfidence + marketCagr * (1.0 - historicalConfidence)

        // ── NAV per unit ──────────────────────────────────────────────
        val navPerUnit = if (totalContributed > 0.0) balance / totalContributed else 1.0

        // ── Capital at risk (simplified VaR 95%: max 30% drawdown) ────
        val capitalAtRiskPerMember = (balance / n) * 0.30

        // ── Sharpe ratio (simplified, σ assumed 15% diversified SA fund)
        val rfr = SA_RISK_FREE_RATE_PCT / 100.0
        val estimatedVol = 0.15
        val sharpeRatio = (annualisedReturn - rfr) / estimatedVol

        // ── Projected FV over remaining period ────────────────────────
        val remainingMonths = (group.periodMonths - monthsActive).coerceAtLeast(0)
        val r = projectedCagr / 12.0
        val pmt = group.monthlyContribution * n
        val projectedFv = if (remainingMonths > 0 && r > 0.0)
            balance * (1.0 + r).pow(remainingMonths.toDouble()) +
                pmt * ((1.0 + r).pow(remainingMonths.toDouble()) - 1.0) / r
        else
            balance + pmt * remainingMonths

        // ── Required return to hit goal ────────────────────────────────
        val goalAmount = if (group.goalAmount > 0.0) group.goalAmount else balance * 1.5
        val requiresReturnPct = if (remainingMonths > 0 && balance > 0.0)
            ((goalAmount / balance).pow(12.0 / remainingMonths.toDouble()) - 1.0) * 100.0
        else 0.0

        val warnings = buildList {
            if (annualisedReturn < SA_CPI_INFLATION_PCT / 100.0 && periodYears > 1.0)
                add("📉 Real return (${r(annualisedReturn * 100)}%) is below SA inflation (${SA_CPI_INFLATION_PCT}%). Improve diversification.")
            if (sharpeRatio < 0.0 && periodYears > 1.0)
                add("📉 Negative Sharpe ratio — risk-adjusted return is below the risk-free rate.")
            if (capitalAtRiskPerMember > group.monthlyContribution * 3)
                add("⚠️ Capital at risk per member (R${r(capitalAtRiskPerMember)}) exceeds 3 months of contributions.")
            if (projectedFv < goalAmount)
                add("📊 Current trajectory may fall short of goal R${r(goalAmount)}. Required return: ${r(requiresReturnPct)}% p.a.")
        }

        return InvestmentClubMetrics(
            netAssetValue           = r(balance),
            navPerUnit              = r(navPerUnit),
            totalReturnPct          = r(totalReturnPct),
            annualisedReturnPct     = r(annualisedReturn * 100.0),
            projectedCagrPct        = r(projectedCagr * 100.0),
            capitalAtRiskPerMember  = r(capitalAtRiskPerMember),
            sharpeRatio             = r(sharpeRatio),
            projectedFvAtCagr       = r(projectedFv),
            requiresReturnPct       = r(requiresReturnPct),
            isOnTrackForGoal        = projectedFv >= goalAmount,
            warnings                = warnings
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ROSCA  –  Besley-Coate-Loury (1993)
    // ══════════════════════════════════════════════════════════════════════

    data class RoscaActuarialMetrics(
        /** n × monthly_contribution */
        val monthlyPot: Double,
        /** Number of members = months per full cycle */
        val cycleLength: Int,
        /** Which month in the current cycle (1-based) */
        val currentCycleMonth: Int,
        /** Paid-out positions / cycle_length × 100 */
        val potCompletionPct: Double,
        /** BCL PV benefit to the member who receives the pot first (position 1) */
        val welfareGainEarlyReceiver: Double,
        /** BCL PV benefit to the last recipient (position n) — usually 0 when r > 0 */
        val welfareGainLateReceiver: Double,
        /** 0-100 composite default risk score */
        val defaultRiskScore: Int,
        /** E[defaults per cycle] = n × p_default */
        val expectedDefaultsPerCycle: Double,
        /** (1 - p_default)^n × 100 */
        val cycleCompletionProbability: Double,
        /** Member receiving the pot THIS month */
        val currentPayoutMember: String,
        /** Member who will receive the pot NEXT month (wraps to cycle start) */
        val nextPayoutMember: String,
        val warnings: List<String>
    )

    fun computeRoscaMetrics(
        group: Group,
        members: List<Member>,
        monthsActive: Int
    ): RoscaActuarialMetrics {
        val participants = members.filter { it.status != MemberStatus.SUSPENDED }
        val n = participants.size.coerceAtLeast(1)
        val r = SA_PRIME_RATE_PCT / 100.0 / 12.0          // monthly discount rate
        val monthlyPot = group.monthlyContribution * n

        // ── Cycle tracking ────────────────────────────────────────────
        val cycleLength = n
        val currentCycleMonth = if (monthsActive <= 0) 1 else ((monthsActive - 1) % n) + 1

        // Positions paid out within current cycle (people who already received pot)
        val paidOutInCycle = (currentCycleMonth - 1).coerceIn(0, n)
        val potCompletionPct = if (n > 0) paidOutInCycle.toDouble() / n * 100.0 else 0.0

        // ── Besley-Coate-Loury (1993) welfare gain ───────────────────
        // PV of saving alone: annuity of c payments for n months at rate r
        //   PV_alone = c × (1 - (1+r)^-n) / r   [or c×n when r≈0]
        // PV of saving alone: annuity PV = c × (1 - (1+r)^-n) / r
        val pvSavingAlone = if (r > 0.0) {
            group.monthlyContribution * (1.0 - (1.0 + r).pow(-n.toDouble())) / r
        } else {
            group.monthlyContribution * n
        }

        // Early receiver (position 1): gets full pot at end of month 1
        //   PV_early = monthlyPot / (1+r)
        //   welfare_gain_early = PV_early - PV_alone   (positive: lump sum beats annuity)
        val pvEarly = monthlyPot / (1.0 + r)
        val welfareGainEarly = (pvEarly - pvSavingAlone).coerceAtLeast(0.0)

        // Late receiver (position n): gets pot at end of month n
        //   PV_late = monthlyPot / (1+r)^n
        //   welfare_gain_late = PV_late - PV_alone   (usually negative → clamped to 0)
        val pvLate = monthlyPot / (1.0 + r).pow(n.toDouble())
        val welfareGainLate = (pvLate - pvSavingAlone).coerceAtLeast(0.0)

        // ── Default risk (moral-hazard model) ─────────────────────────
        val baseDefault = ROSCA_DEFAULT_RISK_BASE_PCT / 100.0
        // Risk rises with fraction already paid out (received-and-might-exit)
        val paidFraction = potCompletionPct / 100.0
        val distressedMembers = members.count {
            it.status == MemberStatus.PENDING_PAYMENT || it.status == MemberStatus.SUSPENDED
        }
        val delinquencyRatio = if (members.isNotEmpty()) distressedMembers.toDouble() / members.size else 0.0
        val pDefault = (baseDefault + (paidFraction * 0.04) + (delinquencyRatio * 0.08)).coerceIn(0.0, 0.35)
        val defaultRiskScore = (pDefault * 1000.0).toInt().coerceIn(0, 100)
        val expectedDefaultsPerCycle = n * pDefault
        val cycleCompletionProbability = (1.0 - pDefault).pow(n.toDouble()) * 100.0

        // ── Current & next payout members ────────────────────────────
        val sortedMembers = sortRoscaParticipants(group, participants)
        // currentCycleMonth is 1-based → index of CURRENT recipient = currentCycleMonth - 1
        val currentPayoutMember = sortedMembers.getOrNull(currentCycleMonth - 1)?.fullName ?: "Unknown"
        // Next recipient wraps around using modulo (handles last-month-of-cycle correctly)
        val nextPayoutMember = sortedMembers.getOrNull(currentCycleMonth % n)?.fullName ?: "Unknown"

        val warnings = buildList {
            if (n < 4)
                add("⚠️ Very small ROSCA ($n members). High cycle-completion volatility.")
            if (defaultRiskScore > 50)
                add("⚠️ Elevated default risk score ($defaultRiskScore/100). Earlier receivers may drop out.")
            if (cycleCompletionProbability < 80.0)
                add("⚠️ Estimated cycle-completion probability: ${r(cycleCompletionProbability)}%.")
            if (n > 20)
                add("💡 Large ROSCA ($n members = ${n}-month cycle). Consider splitting into sub-groups.")
            if (participants.size != members.size)
                add("⚠️ ${members.size - participants.size} suspended member(s) excluded from the payout queue.")
        }

        return RoscaActuarialMetrics(
            monthlyPot                 = r(monthlyPot),
            cycleLength                = cycleLength,
            currentCycleMonth          = currentCycleMonth,
            potCompletionPct           = r(potCompletionPct),
            welfareGainEarlyReceiver   = r(welfareGainEarly),
            welfareGainLateReceiver    = r(welfareGainLate),
            defaultRiskScore           = defaultRiskScore,
            expectedDefaultsPerCycle   = r(expectedDefaultsPerCycle),
            cycleCompletionProbability = r(cycleCompletionProbability),
            currentPayoutMember        = currentPayoutMember,
            nextPayoutMember           = nextPayoutMember,
            warnings                   = warnings
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STOKVEL  –  NASASA standard (2024)
    // ══════════════════════════════════════════════════════════════════════

    data class StokvelActuarialMetrics(
        /** Balance + future expected contributions until year-end */
        val totalProjectedFund: Double,
        /** totalProjectedFund / memberCount */
        val projectedPayoutPerMember: Double,
        /** Progress toward annual target (%) */
        val potMilestonePct: Double,
        /** % of expected contributions actually collected */
        val paymentCompliancePct: Double,
        /** Group payout vs NASASA SA average (% premium/discount) */
        val benchmarkVsNasasaAvg: Double,
        /** 0-100 composite savings quality score */
        val savingsEfficiencyScore: Int,
        /** Month of the year of the traditional payout (December = 12) */
        val targetPayoutMonth: Int,
        val monthsToTarget: Int,
        val annualContributionTarget: Double,
        val warnings: List<String>
    )

    fun computeStokvelMetrics(
        group: Group,
        memberCount: Int,
        balance: Double,
        totalExpectedAnnualContributions: Double,
        monthsActive: Int
    ): StokvelActuarialMetrics {
        val n = memberCount.coerceAtLeast(1)
        val monthInYear = (monthsActive % 12).let { if (it == 0) 12 else it }
        val monthsToTarget = (12 - monthInYear).let { if (it == 0) 12 else it }

        val futureContribs = group.monthlyContribution * n * monthsToTarget
        val totalProjectedFund = balance + futureContribs
        val projectedPayoutPerMember = totalProjectedFund / n

        val annualContributionTarget = group.monthlyContribution * n * 12.0
        val potMilestonePct = if (annualContributionTarget > 0.0)
            min(100.0, balance / annualContributionTarget * 100.0)
        else 0.0

        // Compliance: balance vs expected cumulative (using annualised contribution for precision)
        val monthlyExpected = totalExpectedAnnualContributions / 12.0
        val expectedByNow = monthlyExpected * monthInYear.coerceAtLeast(1)
        val paymentCompliancePct = if (expectedByNow > 0.0)
            min(100.0, balance / expectedByNow * 100.0)
        else 100.0

        // NASASA benchmark: avg member payout = R150 × 12 = R1,800 p.a.
        val nasasaAvgAnnualPayout = STOKVEL_AVG_MONTHLY_CONTRIB * 12.0
        val benchmarkVsNasasa = (projectedPayoutPerMember / nasasaAvgAnnualPayout - 1.0) * 100.0

        val savingsScore = (min(100.0, potMilestonePct) * 0.4 + min(100.0, paymentCompliancePct) * 0.6)
            .toInt().coerceIn(0, 100)

        val warnings = buildList {
            if (paymentCompliancePct < 80.0)
                add("⚠️ Payment compliance at ${r(paymentCompliancePct)}%. Target: 95%+.")
            if (potMilestonePct < 30.0 && monthsActive > 6)
                add("⚠️ Fund is only ${r(potMilestonePct)}% toward annual target after $monthsActive months.")
            if (group.monthlyContribution < STOKVEL_AVG_MONTHLY_CONTRIB)
                add("💡 Monthly contribution R${group.monthlyContribution.toInt()} is below the NASASA average (R${STOKVEL_AVG_MONTHLY_CONTRIB.toInt()}). Consider increasing.")
            if (n > 50)
                add("💡 Large stokvel ($n members). NASASA recommends sub-groups or a committee structure.")
        }

        return StokvelActuarialMetrics(
            totalProjectedFund         = r(totalProjectedFund),
            projectedPayoutPerMember   = r(projectedPayoutPerMember),
            potMilestonePct            = r(potMilestonePct),
            paymentCompliancePct       = r(paymentCompliancePct),
            benchmarkVsNasasaAvg       = r(benchmarkVsNasasa),
            savingsEfficiencyScore     = savingsScore,
            targetPayoutMonth          = 12,
            monthsToTarget             = monthsToTarget,
            annualContributionTarget   = r(annualContributionTarget),
            warnings                   = warnings
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EMERGENCY FUND  –  SA Financial Planning standard
    // ══════════════════════════════════════════════════════════════════════

    data class EmergencyFundMetrics(
        /** balance / estimated monthly pooled expenses */
        val coverageMonths: Double,
        /** Standard: 6 months */
        val targetCoverageMonths: Double,
        /** Rand gap to fill target */
        val coverageGap: Double,
        /** Monthly inflow / target balance × 100 */
        val replenishmentRatePct: Double,
        val isMeetingTarget: Boolean,
        val monthsToTarget: Int,
        val warnings: List<String>
    )

    fun computeEmergencyFundMetrics(
        group: Group,
        memberCount: Int,
        balance: Double
    ): EmergencyFundMetrics {
        val n = memberCount.coerceAtLeast(1)
        // Pooled monthly expense proxy: 1.5× contribution (conservative)
        val estimatedMonthlyExpenses = group.monthlyContribution * n * 1.5

        val coverageMonths = if (estimatedMonthlyExpenses > 0.0) balance / estimatedMonthlyExpenses else 0.0
        val targetBalance = estimatedMonthlyExpenses * EMERGENCY_TARGET_MONTHS
        val coverageGap = (targetBalance - balance).coerceAtLeast(0.0)

        val monthlyContribTotal = group.monthlyContribution * n
        val monthsToTarget = if (monthlyContribTotal > 0.0 && coverageGap > 0.0)
            ceil(coverageGap / monthlyContribTotal).toInt()
        else 0

        val replenishmentRatePct = if (targetBalance > 0.0)
            monthlyContribTotal / targetBalance * 100.0
        else 0.0

        val warnings = buildList {
            if (coverageMonths < 3.0)
                add("⚠️ Emergency fund covers only ${r(coverageMonths)} months (min recommended: 3 months).")
            if (coverageMonths < EMERGENCY_TARGET_MONTHS)
                add("📊 R${r(coverageGap)} still needed to reach ${EMERGENCY_TARGET_MONTHS.toInt()}-month coverage target (~$monthsToTarget more months).")
        }

        return EmergencyFundMetrics(
            coverageMonths       = r(coverageMonths),
            targetCoverageMonths = EMERGENCY_TARGET_MONTHS,
            coverageGap          = r(coverageGap),
            replenishmentRatePct = r(replenishmentRatePct),
            isMeetingTarget      = coverageMonths >= EMERGENCY_TARGET_MONTHS,
            monthsToTarget       = monthsToTarget,
            warnings             = warnings
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TONTINE  –  mortality-adjusted survivor-benefit model
    // ══════════════════════════════════════════════════════════════════════

    data class TontineMetrics(
        /** Total projected fund at end of period */
        val totalFundValue: Double,
        /** Current balance / living members */
        val currentSharePerMember: Double,
        /** projectedFund / expectedSurvivors */
        val projectedShareAtEnd: Double,
        /** n × (1 – q)^(remaining years × 12) */
        val expectedSurvivors: Int,
        /** (projectedShare / totalContributionsPerMember – 1) × 100 */
        val mortalityAdjustedYieldPct: Double,
        val timeToProjectedPayoutMonths: Int,
        val warnings: List<String>
    )

    fun computeTontineMetrics(
        group: Group,
        memberCount: Int,
        balance: Double,
        monthsActive: Int
    ): TontineMetrics {
        val n = memberCount.coerceAtLeast(1)
        val remainingMonths = (group.periodMonths - monthsActive).coerceAtLeast(0)
        val yearsRemaining = remainingMonths.toDouble() / 12.0
        val q = SA_WORKING_AGE_MORT_PER_1000 / 1000.0

        val expectedSurvivors = max(1, (n * (1.0 - q).pow(yearsRemaining * 12.0)).toInt())

        val r = TONTINE_DISCOUNT_RATE_PCT / 100.0 / 12.0
        val pmt = group.monthlyContribution * n
        val projectedFund = if (r > 0.0 && remainingMonths > 0)
            balance * (1.0 + r).pow(remainingMonths.toDouble()) +
                pmt * ((1.0 + r).pow(remainingMonths.toDouble()) - 1.0) / r
        else
            balance + pmt * remainingMonths

        val currentSharePerMember = balance / n
        val projectedShareAtEnd = projectedFund / expectedSurvivors
        val totalContribPerMember = group.monthlyContribution * group.periodMonths.coerceAtLeast(1)
        val mortalityAdjustedYield = if (totalContribPerMember > 0.0)
            (projectedShareAtEnd / totalContribPerMember - 1.0) * 100.0
        else 0.0

        val warnings = buildList {
            if (n < 10)
                add("⚠️ Tontine with fewer than 10 members has high survivor-benefit variance.")
            if (mortalityAdjustedYield < 0.0)
                add("⚠️ Projected per-member payout is below total contributions — consider increasing contributions.")
        }

        return TontineMetrics(
            totalFundValue                = r(projectedFund),
            currentSharePerMember         = r(currentSharePerMember),
            projectedShareAtEnd           = r(projectedShareAtEnd),
            expectedSurvivors             = expectedSurvivors,
            mortalityAdjustedYieldPct     = r(mortalityAdjustedYield),
            timeToProjectedPayoutMonths   = remainingMonths,
            warnings                      = warnings
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COMMUNITY SAVINGS / OTHER  –  money-market growth model
    // ══════════════════════════════════════════════════════════════════════

    data class CommunitySavingsMetrics(
        val savingsPerMember: Double,
        /** balance × community savings rate */
        val annualDividendProjection: Double,
        /** (balance – expected) / expected × 100 */
        val growthRatePct: Double,
        val goalProgressPct: Double,
        val projectedGoalReachDescription: String,
        val warnings: List<String>
    )

    fun computeCommunitySavingsMetrics(
        group: Group,
        memberCount: Int,
        balance: Double,
        monthsActive: Int
    ): CommunitySavingsMetrics {
        val n = memberCount.coerceAtLeast(1)
        val savingsPerMember = balance / n
        val annualDividend = balance * (COMMUNITY_SAVINGS_RATE_PCT / 100.0)

        val expectedByNow = group.monthlyContribution * n * monthsActive.coerceAtLeast(1)
        val growthRatePct = if (expectedByNow > 0.0)
            (balance - expectedByNow) / expectedByNow * 100.0
        else 0.0

        val goalAmount = if (group.goalAmount > 0.0) group.goalAmount else group.monthlyContribution * n * 12.0
        val goalProgressPct = if (goalAmount > 0.0) min(100.0, balance / goalAmount * 100.0) else 100.0

        val monthlyContribTotal = group.monthlyContribution * n
        val projectedGoalDesc = if (balance >= goalAmount) {
            "Goal reached ✅"
        } else if (monthlyContribTotal > 0.0) {
            val needed = ceil((goalAmount - balance) / monthlyContribTotal).toInt()
            "~$needed more months"
        } else {
            "Increase contributions to reach goal"
        }

        val warnings = buildList {
            if (growthRatePct < -10.0)
                add("⚠️ Fund is below expected savings trajectory by ${r(abs(growthRatePct))}%.")
            if (goalProgressPct < 20.0 && monthsActive > 3)
                add("⚠️ Fund at ${r(goalProgressPct)}% of goal after $monthsActive months. Review contribution schedule.")
        }

        return CommunitySavingsMetrics(
            savingsPerMember              = r(savingsPerMember),
            annualDividendProjection      = r(annualDividend),
            growthRatePct                 = r(growthRatePct),
            goalProgressPct               = r(goalProgressPct),
            projectedGoalReachDescription = projectedGoalDesc,
            warnings                      = warnings
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SHARED: 12-month cash-flow projection (all group types)
    // ══════════════════════════════════════════════════════════════════════

    fun computeCashFlowProjections(
        group: Group,
        memberCount: Int,
        currentBalance: Double,
        paymentCompliancePct: Double,
        months: Int = 12
    ): List<MonthlyProjection> {
        val n = memberCount.coerceAtLeast(1)
        val compliance = paymentCompliancePct.coerceIn(0.0, 100.0) / 100.0
        val monthlyInflow = group.monthlyContribution * n * compliance

        // Outflow model is group-type specific
        val monthlyOutflow = when (group.type) {
            GroupType.BURIAL_SOCIETY -> {
                val q = SA_WORKING_AGE_MORT_PER_1000 / 1000.0
                val benefit = if (group.goalAmount > 0.0) group.goalAmount else SA_AVG_FUNERAL_COST
                n * q * benefit / 12.0 + monthlyInflow * 0.05  // claims + 5% admin
            }
            GroupType.ROSCA        -> group.monthlyContribution * n  // full pot per month
            GroupType.STOKVEL      -> monthlyInflow * 0.03
            GroupType.INVESTMENT_CLUB -> monthlyInflow * 0.02        // management-fee proxy
            GroupType.TONTINE      -> 0.0                            // no regular outflow
            else                   -> monthlyInflow * 0.03
        }

        var runningBalance = currentBalance
        return (1..months).map { m ->
            val balanceStart = runningBalance
            val netFlow = monthlyInflow - monthlyOutflow
            val balanceEnd = (balanceStart + netFlow).coerceAtLeast(0.0)
            val riskFlag = balanceEnd < monthlyOutflow * 3  // < 3 months of outflows

            MonthlyProjection(
                month             = m,
                label             = "Month $m",
                projectedBalance  = r(balanceEnd),
                inflow            = r(monthlyInflow),
                outflow           = r(monthlyOutflow),
                netFlow           = r(netFlow),
                riskFlag          = riskFlag
            ).also { runningBalance = balanceEnd }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RISK CLASSIFICATION
    // ══════════════════════════════════════════════════════════════════════

    fun classifyRiskLevel(compositeScore: Int): RiskLevel = when {
        compositeScore >= 80 -> RiskLevel.LOW
        compositeScore >= 55 -> RiskLevel.MODERATE
        compositeScore >= 30 -> RiskLevel.HIGH
        else                 -> RiskLevel.CRITICAL
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INDUSTRY BENCHMARKS  –  per group type
    // ══════════════════════════════════════════════════════════════════════

    fun getIndustryBenchmark(group: Group, memberCount: Int, balance: Double): IndustryBenchmark {
        val n = memberCount.coerceAtLeast(1)
        return when (group.type) {
            GroupType.BURIAL_SOCIETY -> IndustryBenchmark(
                benchmarkType            = "FSCA Friendly Society Standard",
                industryAvgContribution  = 250.0,
                industryAvgBalance       = n * 1_500.0,
                industryPaymentRatePct   = 93.0,
                groupVsBenchmarkPct      = (balance / n - 1_500.0) / 1_500.0 * 100.0,
                benchmarkNotes           = "FSB solvency min: 1.30. Reserve: 3 months claims. Capital adequacy: 15%."
            )
            GroupType.STOKVEL -> IndustryBenchmark(
                benchmarkType            = "NASASA Stokvel Standard (2024)",
                industryAvgContribution  = STOKVEL_AVG_MONTHLY_CONTRIB,
                industryAvgBalance       = STOKVEL_AVG_MEMBERS * STOKVEL_AVG_MONTHLY_CONTRIB * 6.0,
                industryPaymentRatePct   = 95.0,
                groupVsBenchmarkPct      = (group.monthlyContribution / STOKVEL_AVG_MONTHLY_CONTRIB - 1.0) * 100.0,
                benchmarkNotes           = "NASASA 2024: 11.5M members, 820K stokvels. Avg: 38 members @ R150/month."
            )
            GroupType.INVESTMENT_CLUB -> IndustryBenchmark(
                benchmarkType            = "JSE Investment Club / NASAA Standard",
                industryAvgContribution  = 500.0,
                industryAvgBalance       = n * 5_000.0,
                industryPaymentRatePct   = 90.0,
                groupVsBenchmarkPct      = (balance / n - 5_000.0) / 5_000.0 * 100.0,
                benchmarkNotes           = "Target CAGR: 10-14% p.a. (JSE All-Share). Beat inflation + 5%."
            )
            GroupType.ROSCA -> IndustryBenchmark(
                benchmarkType            = "ROSCA Best Practice (NASASA / Academic)",
                industryAvgContribution  = 300.0,
                industryAvgBalance       = n * group.monthlyContribution * 0.5,
                industryPaymentRatePct   = 92.0,
                groupVsBenchmarkPct      = 0.0,
                benchmarkNotes           = "Ideal: 6-12 members. Cycle completion rate target: 90%+."
            )
            GroupType.EMERGENCY_FUND -> IndustryBenchmark(
                benchmarkType            = "SA Financial Planning Standard",
                industryAvgContribution  = 200.0,
                industryAvgBalance       = n * group.monthlyContribution * EMERGENCY_TARGET_MONTHS,
                industryPaymentRatePct   = 90.0,
                groupVsBenchmarkPct      = run {
                    val target = EMERGENCY_TARGET_MONTHS * group.monthlyContribution * n
                    if (target > 0.0) (balance / target - 1.0) * 100.0 else 0.0
                },
                benchmarkNotes           = "Standard: 3-6 months of pooled group expenses. Target: 6 months."
            )
            GroupType.TONTINE -> IndustryBenchmark(
                benchmarkType            = "Tontine Actuarial Standard",
                industryAvgContribution  = 300.0,
                industryAvgBalance       = n * 3_000.0,
                industryPaymentRatePct   = 95.0,
                groupVsBenchmarkPct      = (balance / n - 3_000.0) / 3_000.0 * 100.0,
                benchmarkNotes           = "Minimum 10 members recommended. Survivor benefit grows over time."
            )
            else -> IndustryBenchmark(
                benchmarkType            = "General SA Savings Group Standard",
                industryAvgContribution  = 200.0,
                industryAvgBalance       = n * 2_000.0,
                industryPaymentRatePct   = 90.0,
                groupVsBenchmarkPct      = (balance / n - 2_000.0) / 2_000.0.coerceAtLeast(1.0) * 100.0,
                benchmarkNotes           = "SA National Treasury small savings group benchmark."
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /** Round to 2 decimal places (monetary precision). */
    private fun r(v: Double): Double = (kotlin.math.round(v * 100.0) / 100.0)

    /** Kotlin `Double.pow` via java.lang.Math. */
    private fun Double.pow(exp: Double): Double = Math.pow(this, exp)

    private fun max(a: Int, b: Int): Int = kotlin.math.max(a, b)
    private fun min(a: Double, b: Double): Double = kotlin.math.min(a, b)
    private fun abs(v: Double): Double = kotlin.math.abs(v)
    private fun ceil(v: Double): Double = kotlin.math.ceil(v)
}

