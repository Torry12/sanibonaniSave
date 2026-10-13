package com.sanibonani.save.viewmodel.state.admin

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase

data class AdminOverviewState(
    val group: Group? = null,
    val metrics: ActuarialMetrics = ActuarialMetrics(),
    val businessInsight: GetGroupBusinessInsightsUseCase.GroupBusinessInsight = GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Empty,
    val feeStatus: AdminFeeState = AdminFeeState.DUE,
    val healthScore: GroupHealthScore? = null,
    val viabilityPlan: ViabilityPlan? = null,
    val isCalculatingViability: Boolean = false
)
