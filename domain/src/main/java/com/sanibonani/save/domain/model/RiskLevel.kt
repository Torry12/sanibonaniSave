package com.sanibonani.save.domain.model

import kotlinx.serialization.*

@Serializable
enum class RiskLevel(val displayName: String) {
    @SerialName("low") LOW("Low Risk"),
    @SerialName("moderate") MODERATE("Moderate Risk"),
    @SerialName("high") HIGH("High Risk"),
    @SerialName("critical") CRITICAL("Critical Risk")
}
