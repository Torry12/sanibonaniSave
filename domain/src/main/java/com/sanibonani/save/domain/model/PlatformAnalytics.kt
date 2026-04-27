package com.sanibonani.save.domain.model

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
