package com.sanibonani.save.features.group.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.domain.usecase.group.GetPublicGroupsUseCase
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class GroupUiState(
    val rawGroups: List<Group> = emptyList(),
    val filteredGroups: List<Group> = emptyList(),
    val groupsByProvince: List<Pair<String, List<Group>>> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val provinceFilter: String? = null,
    val typeFilter: GroupType? = null,
    val selectedGroup: Group? = null,
    val isLoadingDetail: Boolean = false,
    val detailError: String? = null
)

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val groupRepo: GroupRepository,
    private val getPublicGroupsUseCase: GetPublicGroupsUseCase
) : BaseViewModel<GroupUiState>(GroupUiState()) {

    val state: StateFlow<GroupUiState> = uiState

    init {
        loadGroups()
    }

    fun setActive(active: Boolean) {
        if (active && !state.value.isLoading) {
            loadGroups()
        }
    }

    fun loadGroups() {
        launchSafeJob("load_groups", "Failed to load public groups") {
            updateState { it.copy(isLoading = true, error = null) }
            getPublicGroupsUseCase().collectLatest { res ->
                res.onSuccess { list ->
                    updateGroupsState(list)
                }.onFailure { e ->
                    updateState { it.copy(isLoading = false, error = e.toUserMessage()) }
                    handleError(e)
                }
            }
        }
    }

    private fun updateGroupsState(rawList: List<Group>) {
        updateState { s ->
            val filtered = rawList.filter { g ->
                (s.searchQuery.isBlank() || g.name.contains(s.searchQuery, ignoreCase = true)) &&
                (s.provinceFilter == null || g.province == s.provinceFilter) &&
                (s.typeFilter == null || g.type == s.typeFilter)
            }
            val grouped = filtered.groupBy { it.province ?: "Other" }.toList().sortedBy { it.first }
            s.copy(rawGroups = rawList, filteredGroups = filtered, groupsByProvince = grouped, isLoading = false)
        }
    }

    fun loadGroup(id: String) {
        launchSafeJob("load_group_detail_$id", "Failed to load group details") {
            updateState { it.copy(isLoadingDetail = true, detailError = null) }
            groupRepo.observeGroup(id).collectLatest { res ->
                res.onSuccess { g ->
                    updateState { it.copy(selectedGroup = g, isLoadingDetail = false) }
                }.onFailure { e ->
                    updateState { it.copy(isLoadingDetail = false, detailError = e.toUserMessage()) }
                    handleError(e)
                }
            }
        }
    }

    fun updateFilter(query: String? = null, province: String? = null, type: GroupType? = null, clearType: Boolean = false, clearProvince: Boolean = false) {
        updateState { s ->
            val nQuery = query ?: s.searchQuery
            val nProvince = if (clearProvince) null else (province ?: s.provinceFilter)
            val nType = if (clearType) null else (type ?: s.typeFilter)
            val filtered = s.rawGroups.filter { g ->
                (nQuery.isBlank() || g.name.contains(nQuery, ignoreCase = true)) &&
                (nProvince == null || g.province == nProvince) &&
                (nType == null || g.type == nType)
            }
            s.copy(searchQuery = nQuery, provinceFilter = nProvince, typeFilter = nType, filteredGroups = filtered, groupsByProvince = filtered.groupBy { it.province ?: "Other" }.toList())
        }
    }
}
