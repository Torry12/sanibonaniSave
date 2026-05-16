package com.sanibonani.save.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlatformConfig(
    @SerialName("monthly_member_fee") val monthlyMemberFee: Double = 10.0,
    @SerialName("registration_fee") val registrationFee: Double = 700.0,
    @SerialName("payout_fee") val payoutFee: Double = 5.0,
    @SerialName("whatsapp_fee") val whatsappFee: Double = 0.50,
    @SerialName("late_fee_percent") val lateFeePercent: Double = 10.0,
    @SerialName("auto_suspension_days") val autoSuspensionDays: Int = 30
)
