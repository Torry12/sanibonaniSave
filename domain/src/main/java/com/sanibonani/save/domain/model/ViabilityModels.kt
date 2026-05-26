package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Parcelize
data class ViabilityPlan(
    val initialContribution: Double,
    val suggestedMonthlyContribution: Double,
    val projectedValue: Double,
    val isViable: Boolean,
    val goalAmount: Double = 0.0,
    val periodMonths: Int = 12,
    val messages: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("required_monthly_to_meet_goal")  val requiredMonthlyToMeetGoal: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("shortfall_amount")                val shortfallAmount: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("break_even_months")               val breakEvenMonths: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("compounded_projected_value")      val compoundedProjectedValue: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("optimistic_projected_value")      val optimisticProjectedValue: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("pessimistic_projected_value")     val pessimisticProjectedValue: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("active_member_ratio")              val activeMemberRatio: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("inflation_adjustment_factor")      val inflationAdjustmentFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projection_retention_factor")      val projectionRetentionFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("claim_readiness_factor")           val claimReadinessFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("mortality_buffer_factor")          val mortalityBufferFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("reserve_adequacy_factor")          val reserveAdequacyFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("market_return_premium_factor")     val marketReturnPremiumFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("volatility_haircut_factor")        val volatilityHaircutFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("collection_efficiency_factor")     val collectionEfficiencyFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("festive_payout_pressure_factor")   val festivePayoutPressureFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("default_risk_factor")              val defaultRiskFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("cycle_slippage_factor")            val cycleSlippageFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("withdrawal_pressure_factor")       val withdrawalPressureFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("inflation_safety_factor")          val inflationSafetyFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("survivor_uncertainty_factor")      val survivorUncertaintyFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("horizon_compounding_factor")       val horizonCompoundingFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("goal_stretch_ratio")               val goalStretchRatio: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("growth_conservatism_factor")       val growthConservatismFactor: Double = 1.0
) : Parcelable
