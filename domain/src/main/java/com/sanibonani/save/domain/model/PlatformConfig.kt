package com.sanibonani.save.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlatformConfig(
    @SerialName("monthly_member_fee") val monthlyMemberFee: Double = 0.0,
    @SerialName("registration_fee") val registrationFee: Double = 700.0
)

