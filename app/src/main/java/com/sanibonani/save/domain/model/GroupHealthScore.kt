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

@Serializable
data class HealthScoreComponent(
    val name: String,                // e.g., "Solvency Ratio"
    val score: Int,                  // Component's contribution to total
    val weight: Float,               // e.g., 0.25 = 25% of total score
    val formula: String,             // "Assets / Liabilities"
    @SerialName("current_value")
    val currentValue: Double,        // Actual calculated value
    val benchmark: Double,           // Industry benchmark
    val status: String               // "Good", "Warning", "Critical"
)

enum class RecommendationPriority {
    HIGH,
    MEDIUM,
    LOW
}

@Serializable
data class HealthScoreRecommendation(
    val id: String,
    @SerialName("group_id")
    val groupId: String,
    val title: String,               // e.g., "Increase monthly contribution"
    val description: String,
    val priority: RecommendationPriority,
    val category: String,            // "Contributions", "Retention", "Claims"
    @SerialName("estimated_impact")
    val estimatedImpact: String,     // e.g., "Would move to Green zone"
    @SerialName("created_at")
    val createdAt: String
)

