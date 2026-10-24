package com.sanibonani.save.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class RiskZone {
    RED,     // < 40 points: Critical risk
    YELLOW,  // 40-70 points: Moderate risk
    GREEN    // > 70 points: Healthy
}

@Serializable
data class GroupHealthScore(
    @SerialName("group_id")
    val groupId: String,
    @SerialName("overall_score")
    val overallScore: Int,                          // 0-100
    @SerialName("zone")
    val zone: RiskZone,
    @SerialName("components")
    val components: Map<String, Int> = emptyMap(), // Component name → score
    @SerialName("recommendations")
    val recommendations: List<String> = emptyList(),
    @SerialName("generated_at")
    val generatedAt: String,
    @SerialName("expires_at")
    val expiresAt: String? = null                  // Cache expiration
)
