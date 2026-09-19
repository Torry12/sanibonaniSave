package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

@Serializable
@Parcelize
data class MonthlyProjection(
    @SerialName("month") val month: Int = 0,
    @SerialName("label") val label: String = "",
    @SerialName("projected_balance") val projectedBalance: Double = 0.0,
    @SerialName("inflow") val inflow: Double = 0.0,
    @SerialName("outflow") val outflow: Double = 0.0,
    @SerialName("net_flow")   val netFlow: Double = 0.0,
    @SerialName("risk_flag")  val riskFlag: Boolean = false
) : Parcelable

@Serializable
@Parcelize
data class IndustryBenchmark(
    @SerialName("benchmark_type")              val benchmarkType: String = "",
    @SerialName("industry_avg_contribution")   val industryAvgContribution: Double = 0.0,
    @SerialName("industry_avg_balance")        val industryAvgBalance: Double = 0.0,
    @SerialName("industry_payment_rate_pct")   val industryPaymentRatePct: Double = 0.0,
    @SerialName("group_vs_benchmark_pct")      val groupVsBenchmarkPct: Double = 0.0,
    @SerialName("benchmark_notes")             val benchmarkNotes: String = ""
) : Parcelable

@Serializable
@Parcelize
data class GroupFinancialInsight(
    @SerialName("group_id")          val groupId: String = "",
    @SerialName("group_type")        val groupType: GroupType = GroupType.OTHER,
    @SerialName("risk_level")        val riskLevel: RiskLevel = RiskLevel.MODERATE,
    @SerialName("status_summary")    val statusSummary: String = "",
    @SerialName("recommendations")   val recommendations: List<String> = emptyList(),
    @SerialName("key_findings")      val keyFindings: List<String> = emptyList(),
    @SerialName("monthly_projections") val monthlyProjections: List<MonthlyProjection> = emptyList(),
    @SerialName("industry_benchmark") val industryBenchmark: IndustryBenchmark = IndustryBenchmark()
) : Parcelable

@Serializable
@Parcelize
data class ActuarialMetrics(
    @SerialName("pure_premium")                 val purePremium: Double = 0.0,
    @SerialName("gross_premium")                val grossPremium: Double = 0.0,
    @SerialName("reserve_adequacy_pct")         val reserveAdequacyPct: Double = 0.0,
    @SerialName("solvency_margin_pct")          val solvencyMarginPct: Double = 0.0,
    @SerialName("loss_ratio_pct")               val lossRatioPct: Double = 0.0,
    @SerialName("contribution_sufficiency_pct") val contributionSufficiencyPct: Double = 0.0,
    @SerialName("break_even_members")           val breakEvenMembers: Int = 0,
    @SerialName("actuarial_present_value")      val actuarialPresentValue: Double = 0.0,
    @SerialName("funding_ratio_pct")            val fundingRatioPct: Double = 0.0,
    @SerialName("payment_rate_pct")             val paymentRatePct: Double = 0.0,
    @SerialName("composite_risk_score")         val compositeRiskScore: Int = 0,
    @SerialName("insolvency_months")            val insolvencyMonths: Int = 0,
    @SerialName("expected_annual_claims")       val expectedAnnualClaims: Double = 0.0,
    @SerialName("risk_level")                   val riskLevel: RiskLevel = RiskLevel.MODERATE,
    @SerialName("warnings")                     val warnings: List<String> = emptyList()
) : Parcelable

@Serializable
@Parcelize
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
) : Parcelable

@Serializable
@Parcelize
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
) : Parcelable

@Serializable
@Parcelize
data class EmergencyFundMetrics(
    @SerialName("coverage_months") val coverageMonths: Double = 0.0,
    @SerialName("target_coverage_months") val targetCoverageMonths: Double = 0.0,
    @SerialName("coverage_gap") val coverageGap: Money = Money.ZERO,
    @SerialName("replenishment_rate_pct") val replenishmentRatePct: Double = 0.0,
    @SerialName("is_meeting_target") val isMeetingTarget: Boolean = false,
    @SerialName("months_to_target") val monthsToTarget: Int = 0,
    @SerialName("warnings")         val warnings: List<String> = emptyList()
) : Parcelable

@Serializable
@Parcelize
data class TontineMetrics(
    @SerialName("total_fund_value") val totalFundValue: Money = Money.ZERO,
    @SerialName("current_share_per_member") val currentSharePerMember: Money = Money.ZERO,
    @SerialName("projected_share_at_end") val projectedShareAtEnd: Money = Money.ZERO,
    @SerialName("expected_survivors") val expectedSurvivors: Int = 0,
    @SerialName("mortality_adjusted_yield_pct") val mortalityAdjustedYieldPct: Double = 0.0,
    @SerialName("time_to_projected_payout_months") val timeToProjectedPayoutMonths: Int = 0,
    @SerialName("warnings")                        val warnings: List<String> = emptyList()
) : Parcelable

@Serializable
@Parcelize
data class CommunitySavingsMetrics(
    @SerialName("savings_per_member") val savingsPerMember: Money = Money.ZERO,
    @SerialName("annual_dividend_projection") val annualDividendProjection: Money = Money.ZERO,
    @SerialName("growth_rate_pct") val growthRatePct: Double = 0.0,
    @SerialName("goal_progress_pct") val goalProgressPct: Double = 0.0,
    @SerialName("projected_goal_reach_description") val projectedGoalReachDescription: String = "",
    @SerialName("warnings")                         val warnings: List<String> = emptyList()
) : Parcelable

@Serializable
@Parcelize
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
) : Parcelable
