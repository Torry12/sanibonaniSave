package com.sanibonani.save.viewmodel.dynamics

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupUiState(
    val publicGroups: List<Group> = emptyList(),
    val filteredGroups: List<Group> = emptyList(),
    val selectedGroup: Group? = null,
    val isLoading: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val error: String? = null,
    val detailError: String? = null,
    val searchQuery: String = "",
    val provinceFilter: String? = null,
    val typeFilter: GroupType? = null
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

    fun loadGroups() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            getPublicGroupsUseCase().collect { res ->
                res.onSuccess { list ->
                    updateState { it.copy(publicGroups = list, filteredGroups = applyFilters(list, it.searchQuery, it.provinceFilter, it.typeFilter), isLoading = false) }
                }
            }
        }
    }

    fun loadGroup(id: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoadingDetail = true, selectedGroup = null) }
            groupRepo.getGroupById(id).onSuccess { g ->
                updateState { it.copy(selectedGroup = g, isLoadingDetail = false) }
            }
        }
    }

    fun updateFilter(query: String? = null, province: String? = null, type: GroupType? = null, clearProvince: Boolean = false, clearType: Boolean = false) {
        updateState { s ->
            val nQuery = query ?: s.searchQuery
            val nProvince = if (clearProvince) null else (province ?: s.provinceFilter)
            val nType = if (clearType) null else (type ?: s.typeFilter)
            s.copy(searchQuery = nQuery, provinceFilter = nProvince, typeFilter = nType, filteredGroups = applyFilters(s.publicGroups, nQuery, nProvince, nType))
        }
    }

    private fun applyFilters(list: List<Group>, q: String, p: String?, t: GroupType?): List<Group> {
        return list.filter { g ->
            (q.isBlank() || g.name.contains(q, ignoreCase = true)) && (p == null || g.province == p) && (t == null || g.type == t)
        }
    }

    fun setActive(active: Boolean) { if (active) loadGroups() }
}
