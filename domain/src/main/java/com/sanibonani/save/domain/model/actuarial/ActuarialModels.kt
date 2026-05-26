package com.sanibonani.save.domain.model.actuarial

import android.os.Parcelable
import com.sanibonani.save.domain.model.group.GroupType
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

@Serializable
enum class RiskLevel(val displayName: String) {
    @SerialName("low")      LOW("Low Risk"),
    @SerialName("moderate") MODERATE("Moderate Risk"),
    @SerialName("high")     HIGH("High Risk"),
    @SerialName("critical") CRITICAL("Critical Risk")
}

@Serializable
@Parcelize
data class MonthlyProjection(
    val month: Int = 0,
    val label: String = "",
    @SerialName("projected_balance") val projectedBalance: Double = 0.0,
    val inflow: Double = 0.0,
    val outflow: Double = 0.0,
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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Parcelize
data class GroupFinancialInsight(
    @SerialName("group_id")          val groupId: String = "",
    @SerialName("group_type")        val groupType: GroupType = GroupType.OTHER,
    @SerialName("risk_level")        val riskLevel: RiskLevel = RiskLevel.MODERATE,
    @SerialName("status_summary")    val statusSummary: String = "",
    val recommendations: List<String> = emptyList(),
    @SerialName("key_findings")      val keyFindings: List<String> = emptyList(),
    @SerialName("monthly_projections") val monthlyProjections: List<MonthlyProjection> = emptyList(),
    @SerialName("industry_benchmark") val industryBenchmark: IndustryBenchmark = IndustryBenchmark(),

    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("expected_annual_claims_count") val expectedAnnualClaimsCount: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("solvency_ratio")              val solvencyRatio: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("capital_adequacy_pct")        val capitalAdequacyPct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("required_reserve_amount")     val requiredReserveAmount: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("reserve_coverage_months")     val reserveCoverageMonths: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("benefit_adequacy_pct")        val benefitAdequacyPct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("is_solvent")                  val isSolvent: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("is_capital_adequate")         val isCapitalAdequate: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("years_to_insolvency")         val yearsToInsolvency: Double = -1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("pure_premium")                val purePremiumInsight: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("gross_premium")               val grossPremiumInsight: Double = 0.0,

    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("nav_per_unit")                val navPerUnit: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("total_return_pct")            val totalReturn: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("annualised_return_pct")       val annualisedReturn: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_cagr_pct")          val projectedCagr: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("capital_at_risk_per_member")  val capitalAtRiskPerMember: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("sharpe_ratio")                val sharpeRatio: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_fv")                val projectedFv: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("requires_return_pct")         val requiresReturnPct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("is_on_track_for_goal")        val isOnTrackForGoal: Boolean = false,

    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("monthly_pot")                 val monthlyPot: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("cycle_length")                val cycleLength: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("current_cycle_month")         val currentCycleMonth: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("pot_completion_pct")          val potCompletionPct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("default_risk_score")          val defaultRiskScore: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("cycle_completion_probability") val cycleCompletionProbability: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("current_payout_member")       val currentPayoutMember: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("next_payout_member")          val nextPayoutMember: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("welfare_gain_early_receiver") val welfareGainEarlyReceiver: Double = 0.0,

    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("total_projected_fund")        val totalProjectedFund: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_payout_per_member") val projectedPayoutPerMember: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("pot_milestone_pct")           val potMilestonePct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("payment_compliance_pct")      val paymentCompliancePct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("benchmark_vs_nasasa_avg")     val benchmarkVsNasasaAvg: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("savings_efficiency_score")    val savingsEfficiencyScore: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("annual_contribution_target")  val annualContributionTarget: Double = 0.0,

    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("coverage_months")             val coverageMonths: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("target_coverage_months")      val targetCoverageMonths: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("coverage_gap")                val coverageGap: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("is_meeting_target")           val isMeetingTarget: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("months_to_emergency_target")  val monthsToEmergencyTarget: Int = 0,

    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("current_share_per_member")    val currentSharePerMember: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_share_at_end")      val projectedShareAtEnd: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("expected_survivors")          val expectedSurvivors: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("mortality_adjusted_yield")    val mortalityAdjustedYield: Double = 0.0,

    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("savings_per_member")          val savingsPerMember: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("annual_dividend_projection")  val annualDividendProjection: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("growth_rate_pct")             val growthRatePct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("goal_progress_pct")           val goalProgressPct: Double = 0.0
) : Parcelable

@OptIn(ExperimentalSerializationApi::class)
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
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("risk_level")               val riskLevel: RiskLevel = RiskLevel.MODERATE,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("cash_flow_risk_score")     val cashFlowRiskScore: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("collection_efficiency_pct") val collectionEfficiencyPct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_balance_m3")     val projectedBalanceM3: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_balance_m6")     val projectedBalanceM6: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_balance_m12")    val projectedBalanceM12: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("type_specific_warnings")   val typeSpecificWarnings: List<String> = emptyList()
) : Parcelable
