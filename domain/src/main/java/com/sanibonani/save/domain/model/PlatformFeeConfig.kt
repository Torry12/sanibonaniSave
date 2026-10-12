package com.sanibonani.save.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlatformFeeConfig(
    @SerialName("group_type") val groupType: GroupType,
    @SerialName("rosca_cycle") val roscaCycle: Int = 0,
    @SerialName("registration_fee") val registrationFee: Money = Money.ZERO,
    @SerialName("monthly_fee") val monthlyFee: Money = Money.ZERO,
    @SerialName("shortfall_recovery_fee") val shortfallRecoveryFee: Money = Money.ZERO
)
