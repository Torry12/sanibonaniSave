package com.sanibonani.save.domain.model

import kotlinx.serialization.*


@Serializable
data class IndustryBenchmark(
    @SerialName("benchmark_type")              val benchmarkType: String = "",
    @SerialName("industry_avg_contribution")   val industryAvgContribution: Money = Money.ZERO,
    @SerialName("industry_avg_balance")        val industryAvgBalance: Money = Money.ZERO,
    @SerialName("industry_payment_rate_pct")   val industryPaymentRatePct: Double = 0.0,
    @SerialName("group_vs_benchmark_pct")      val groupVsBenchmarkPct: Double = 0.0,
    @SerialName("benchmark_notes")             val benchmarkNotes: String = ""
)

@Serializable
data class ActuarialFormulaBreakdown(
    @SerialName("title") val title: String = "",
    @SerialName("formula") val formula: String = "",
    @SerialName("result_text") val resultText: String = "",
    @SerialName("explanation") val explanation: String = "",
    @SerialName("status") val status: String = "OK"
)

@Serializable
data class GroupFinancialInsight(
    @SerialName("group_id")          val groupId: String = "",
    @SerialName("group_type")        val groupType: GroupType = GroupType.OTHER,
    @SerialName("risk_level")        val riskLevel: RiskLevel = RiskLevel.MODERATE,
    @SerialName("status_summary")    val statusSummary: String = "",
    @SerialName("recommendations")   val recommendations: List<String> = emptyList(),
    @SerialName("key_findings")      val keyFindings: List<String> = emptyList(),
    @SerialName("monthly_projections") val monthlyProjections: List<FutureMonthProjection> = emptyList(),
    @SerialName("industry_benchmark") val industryBenchmark: IndustryBenchmark = IndustryBenchmark(),

    // Burial Society specific
    @SerialName("expected_annual_claims_count") val expectedAnnualClaimsCount: Double = 0.0,
    @SerialName("solvency_ratio")              val solvencyRatio: Double = 0.0,
    @SerialName("capital_adequacy_pct")        val capitalAdequacyPct: Double = 0.0,
    @SerialName("reserve_coverage_months")     val reserveCoverageMonths: Double = 0.0,
    @SerialName("benefit_adequacy_pct")        val benefitAdequacyPct: Double = 0.0,
    @SerialName("is_solvent")                  val isSolvent: Boolean = true,
    @SerialName("max_simultaneous_claims_covered") val maxSimultaneousClaimsCovered: Int = 0,
    @SerialName("recommended_monthly_contribution_per_member") val recommendedMonthlyContributionPerMember: Money = Money.ZERO,
    @SerialName("calculation_breakdowns")      val calculationBreakdowns: List<ActuarialFormulaBreakdown> = emptyList(),

    // Investment Club specific
    @SerialName("net_asset_value")            val netAssetValue: Money = Money.ZERO,
    @SerialName("nav_per_unit")                val navPerUnit: Money = Money.ZERO,
    @SerialName("total_return_pct")            val totalReturn: Double = 0.0,
    @SerialName("annualised_return_pct")       val annualisedReturn: Double = 0.0,

    // ROSCA specific
    @SerialName("monthly_pot")                 val monthlyPot: Money = Money.ZERO,
    @SerialName("cycle_length")                val cycleLength: Int = 0,
    @SerialName("current_cycle_month")         val currentCycleMonth: Int = 0,
    @SerialName("current_payout_member")       val currentPayoutMember: String = "",
    @SerialName("next_payout_member")          val nextPayoutMember: String = "",

    // Stokvel specific
    @SerialName("total_projected_fund")        val totalProjectedFund: Money = Money.ZERO,
    @SerialName("projected_payout_per_member") val projectedPayoutPerMember: Money = Money.ZERO,
    @SerialName("pot_milestone_pct")           val potMilestonePct: Double = 0.0
)

@Serializable
data class ActuarialMetrics(
    @SerialName("pure_premium")                 val purePremium: Money = Money.ZERO,
    @SerialName("gross_premium")                val grossPremium: Money = Money.ZERO,
    @SerialName("reserve_adequacy_pct")         val reserveAdequacyPct: Double = 0.0,
    @SerialName("solvency_margin_pct")          val solvencyMarginPct: Double = 0.0,
    @SerialName("loss_ratio_pct")               val lossRatioPct: Double = 0.0,
    @SerialName("contribution_sufficiency_pct") val contributionSufficiencyPct: Double = 0.0,
    @SerialName("break_even_members")           val breakEvenMembers: Int = 0,
    @SerialName("actuarial_present_value")      val actuarialPresentValue: Money = Money.ZERO,
    @SerialName("funding_ratio_pct")            val fundingRatioPct: Double = 0.0,
    @SerialName("payment_rate_pct")             val paymentRatePct: Double = 0.0,
    @SerialName("composite_risk_score")         val compositeRiskScore: Int = 0,
    @SerialName("insolvency_months")            val insolvencyMonths: Int = 0,
    @SerialName("expected_annual_claims")       val expectedAnnualClaims: Money = Money.ZERO,
    @SerialName("risk_level")                   val riskLevel: RiskLevel = RiskLevel.MODERATE,
    @SerialName("warnings")                     val warnings: List<String> = emptyList(),
    @SerialName("key_metrics")                  val keyMetrics: List<ActuarialFormulaBreakdown> = emptyList()
)

@Serializable
data class BurialSocietyActuarialMetrics(
    @SerialName("expected_annual_claims_count") val expectedAnnualClaimsCount: Double = 0.0,
    @SerialName("pure_premium")                 val purePremium: Money = Money.ZERO,
    @SerialName("safety_loaded_premium")        val safetyLoadedPremium: Money = Money.ZERO,
    @SerialName("gross_premium")                val grossPremium: Money = Money.ZERO,
    @SerialName("actuarial_present_value")      val actuarialPresentValue: Money = Money.ZERO,
    @SerialName("solvency_ratio")               val solvencyRatio: Double = 0.0,
    @SerialName("capital_adequacy_pct")         val capitalAdequacyPct: Double = 0.0,
    @SerialName("required_reserve_amount")      val requiredReserveAmount: Money = Money.ZERO,
    @SerialName("reserve_coverage_months")      val reserveCoverageMonths: Double = 0.0,
    @SerialName("minimum_capital_amount")       val minimumCapitalAmount: Money = Money.ZERO,
    @SerialName("is_solvent")                   val isSolvent: Boolean = true,
    @SerialName("is_capital_adequate")          val isCapitalAdequate: Boolean = true,
    @SerialName("is_reserve_adequate")          val isReserveAdequate: Boolean = true,
    @SerialName("benefit_adequacy_pct")         val benefitAdequacyPct: Double = 0.0,
    @SerialName("claim_rate_per_thousand")      val claimRatePerThousand: Double = 0.0,
    @SerialName("projected_claims_next_year")   val projectedClaimsNextYear: Money = Money.ZERO,
    @SerialName("years_to_insolvency_at_current_rate") val yearsToInsolvencyAtCurrentRate: Double = -1.0,
    @SerialName("max_simultaneous_claims_covered") val maxSimultaneousClaimsCovered: Int = 0,
    @SerialName("recommended_monthly_contribution_per_member") val recommendedMonthlyContributionPerMember: Money = Money.ZERO,
    @SerialName("user_friendly_summary")        val userFriendlySummary: String = "",
    @SerialName("calculation_breakdowns")       val calculationBreakdowns: List<ActuarialFormulaBreakdown> = emptyList(),
    @SerialName("warnings")                     val warnings: List<String> = emptyList()
)

@Serializable
data class RoscaActuarialMetrics(
    @SerialName("monthly_pot") val monthlyPot: Money = Money.ZERO,
    @SerialName("cycle_length") val cycleLength: Int = 0,
    @SerialName("current_cycle_month") val currentCycleMonth: Int = 0,
    @SerialName("pot_completion_pct") val potCompletionPct: Double = 0.0,
    @SerialName("welfare_gain_early_receiver") val welfareGainEarlyReceiver: Money = Money.ZERO,
    @SerialName("welfare_gain_late_receiver") val welfareGainLateReceiver: Money = Money.ZERO,
    @SerialName("default_risk_score") val defaultRiskScore: Int = 0,
    @SerialName("expected_defaults_per_cycle") val expectedDefaultsPerCycle: Double = 0.0,
    @SerialName("cycle_completion_probability") val cycleCompletionProbability: Double = 0.0,
    @SerialName("current_payout_member") val currentPayoutMember: String = "",
    @SerialName("next_payout_member") val nextPayoutMember: String = "",
    @SerialName("warnings")           val warnings: List<String> = emptyList()
)

@Serializable
data class StokvelActuarialMetrics(
    @SerialName("total_projected_fund") val totalProjectedFund: Money = Money.ZERO,
    @SerialName("projected_payout_per_member") val projectedPayoutPerMember: Money = Money.ZERO,
    @SerialName("pot_milestone_pct") val potMilestonePct: Double = 0.0,
    @SerialName("payment_compliance_pct") val paymentCompliancePct: Double = 0.0,
    @SerialName("benchmark_vs_nasasa_avg") val benchmarkVsNasasaAvg: Double = 0.0,
    @SerialName("savings_efficiency_score") val savingsEfficiencyScore: Int = 0,
    @SerialName("target_payout_month") val targetPayoutMonth: Int = 12,
    @SerialName("months_to_target") val monthsToTarget: Int = 0,
    @SerialName("annual_contribution_target") val annualContributionTarget: Money = Money.ZERO,
    @SerialName("warnings")                   val warnings: List<String> = emptyList()
)

@Serializable
data class EmergencyFundMetrics(
    @SerialName("coverage_months") val coverageMonths: Double = 0.0,
    @SerialName("target_coverage_months") val targetCoverageMonths: Double = 0.0,
    @SerialName("coverage_gap") val coverageGap: Money = Money.ZERO,
    @SerialName("replenishment_rate_pct") val replenishmentRatePct: Double = 0.0,
    @SerialName("is_meeting_target") val isMeetingTarget: Boolean = false,
    @SerialName("months_to_target") val monthsToTarget: Int = 0,
    @SerialName("warnings")         val warnings: List<String> = emptyList()
)



@Serializable
data class CommunitySavingsMetrics(
    @SerialName("savings_per_member") val savingsPerMember: Money = Money.ZERO,
    @SerialName("annual_dividend_projection") val annualDividendProjection: Money = Money.ZERO,
    @SerialName("growth_rate_pct") val growthRatePct: Double = 0.0,
    @SerialName("goal_progress_pct") val goalProgressPct: Double = 0.0,
    @SerialName("projected_goal_reach_description") val projectedGoalReachDescription: String = "",
    @SerialName("warnings")                         val warnings: List<String> = emptyList()
)

@Serializable
data class InvestmentClubMetrics(
    @SerialName("net_asset_value") val netAssetValue: Money = Money.ZERO,
    @SerialName("nav_per_unit") val navPerUnit: Money = Money.ZERO,
    @SerialName("total_return_pct") val totalReturnPct: Double = 0.0,
    @SerialName("annualised_return_pct") val annualisedReturnPct: Double = 0.0,
    @SerialName("projected_cagr_pct") val projectedCagrPct: Double = 0.0,
    @SerialName("capital_at_risk_per_member") val capitalAtRiskPerMember: Money = Money.ZERO,
    @SerialName("sharpe_ratio") val sharpeRatio: Double = 0.0,
    @SerialName("projected_fv_at_cagr") val projectedFvAtCagr: Money = Money.ZERO,
    @SerialName("requires_return_pct") val requiresReturnPct: Double = 0.0,
    @SerialName("is_on_track_for_goal") val isOnTrackForGoal: Boolean = false,
    @SerialName("warnings")             val warnings: List<String> = emptyList()
)
