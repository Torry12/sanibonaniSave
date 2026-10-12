package com.sanibonani.save.features.admin.presentation.state

import com.sanibonani.save.domain.model.*

data class PlatformAnalyticsUiState(
    val analytics: AsyncResult<PlatformAnalytics> = AsyncResult.Uninitialized,
    val groups: List<Group> = emptyList(),
    val groupNames: Map<String, String> = emptyMap(),
    val groupProvinces: Map<String, String> = emptyMap(),
    val loanMemberNames: Map<String, String> = emptyMap(),
    val provinceDistribution: List<Pair<String, Int>> = emptyList(),
    val marketSegments: List<Pair<String, Int>> = emptyList(),
    val totalRevenueShare: Money = Money.ZERO,
    val selectedGroupMetrics: ActuarialMetrics? = null,
    val selectedGroupHealthScore: GroupHealthScore? = null,
    val isLoadingHealthScore: Boolean = false,
    val isLoading: Boolean = false
)
