package com.sanibonani.save.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlatformAnalytics(
    val totalGroups: Int = 0,
    val totalMembers: Int = 0,
    val totalProvinces: Int = 0,
    val totalBalance: Double = 0.0,
    val totalPlatformFees: Double = 0.0,
    val averageRiskScore: Double = 0.0,
    val groupTypeDistribution: Map<String, Int> = emptyMap(),
    val provinceDistribution: Map<String?, Int> = emptyMap()
)

@Serializable
data class PlatformSummaryStats(
    @SerialName("total_groups") val totalGroups: Int,
    @SerialName("total_members") val totalMembers: Int,
    @SerialName("total_balance") val totalBalance: Double,
    @SerialName("total_provinces") val totalProvinces: Int,
    @SerialName("platform_revenue") val platformRevenue: Double,
    @SerialName("average_risk_score") val averageRiskScore: Double
)
