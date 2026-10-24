package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.domain.usecase.admin.GetPlatformDistributionsUseCase
import com.sanibonani.save.features.admin.presentation.state.PlatformAnalyticsUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlatformAnalyticsViewModel @Inject constructor(
    private val platformRepo: PlatformRepository,
    private val getPlatformDistributionsUseCase: GetPlatformDistributionsUseCase
) : BaseViewModel<PlatformAnalyticsUiState>(PlatformAnalyticsUiState()) {

    val state: StateFlow<PlatformAnalyticsUiState> = uiState

    fun load() {
        launchJob("load_analytics") {
            updateState { it.copy(isLoading = true, analytics = AsyncResult.Loading) }
            coroutineScope {
                val analyticsTask = async { platformRepo.getPlatformAnalytics() }
                val groupsDeferred = async { platformRepo.getAllGroups().getOrNull() ?: emptyList() }

                val groups = groupsDeferred.await()
                val dist = getPlatformDistributionsUseCase(groups)
                val analyticsRes = analyticsTask.await()

                analyticsRes.onSuccess { analytics ->
                    updateState { it.copy(
                        analytics = AsyncResult.Success(analytics),
                        groups = groups,
                        groupNames = dist.groupNames,
                        groupProvinces = dist.groupProvinces,
                        provinceDistribution = dist.provinceDistribution,
                        marketSegments = dist.marketSegments,
                        totalRevenueShare = analytics.totalPlatformFees,
                        isLoading = false
                    ) }
                }.onFailure { e ->
                    updateState { it.copy(
                        analytics = AsyncResult.Error(e.toUserMessage(), e.javaClass.simpleName),
                        groups = groups,
                        groupNames = dist.groupNames,
                        groupProvinces = dist.groupProvinces,
                        provinceDistribution = dist.provinceDistribution,
                        marketSegments = dist.marketSegments,
                        isLoading = false
                    ) }
                }
            }
        }
    }

    fun syncPlatformMetrics() {
        launchJob("sync_metrics") {
            platformRepo.computeSystemWideMetrics().onSuccess {
                showMessage("System-wide metrics recalculated successfully")
                load()
            }.onFailure { handleError(it) }
        }
    }

    fun fetchGroupMetrics(groupId: String) {
        launchJob("fetch_group_metrics") {
            platformRepo.getGroupMetrics(groupId).onSuccess { metrics ->
                updateState { it.copy(selectedGroupMetrics = metrics) }
            }
        }
    }

    fun deleteGroupFromPlatform(groupId: String) {
        launchJob("delete_group_platform") {
            updateState { it.copy(isLoading = true) }
            platformRepo.deleteGroupFromPlatform(groupId)
                .onSuccess {
                    showMessage("Group deleted from platform successfully")
                    load()
                }
                .onFailure {
                    updateState { it.copy(isLoading = false) }
                    handleError(it)
                }
        }
    }
}
