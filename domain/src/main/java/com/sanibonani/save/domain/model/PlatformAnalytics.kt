package com.sanibonani.save.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlatformAnalytics(
    val totalGroups: Int = 0,
    val totalMembers: Int = 0,
    val totalProvinces: Int = 0,
    val totalBalance: Money = Money.ZERO,
    val totalPlatformFees: Money = Money.ZERO,
    val averageRiskScore: Double = 0.0,
    val groupTypeDistribution: Map<String, Int> = emptyMap(),
    val provinceDistribution: Map<String?, Int> = emptyMap()
)

@Serializable
data class PlatformAnalyticsSummary(
    @SerialName("total_groups") val totalGroups: Int,
    @SerialName("total_members") val totalMembers: Int,
    @SerialName("total_balance") val totalBalance: Money,
    @SerialName("total_provinces") val totalProvinces: Int,
    @SerialName("platform_revenue") val platformRevenue: Money,
    @SerialName("average_risk_score") val averageRiskScore: Double
)

@Serializable
data class MemberBehaviorInsight(
    @SerialName("member_id") val memberId: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("member_name") val memberName: String,
    @SerialName("risk_band") val riskBand: String,
    @SerialName("overdue_loans") val overdueLoans: Int,
    @SerialName("behavior_score") val behaviorScore: Double
)
