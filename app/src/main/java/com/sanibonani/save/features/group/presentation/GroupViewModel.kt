package com.sanibonani.save.features.group.presentation

import com.sanibonani.save.domain.config.SaReferenceData
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.domain.usecase.group.FilterGroupsUseCase
import com.sanibonani.save.domain.usecase.group.GetPublicGroupsUseCase
import com.sanibonani.save.domain.usecase.group.ObserveGroupDetailUseCase
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
    private val filterGroups: FilterGroupsUseCase,
    private val getPublicGroups: GetPublicGroupsUseCase,
    private val observeGroupDetail: ObserveGroupDetailUseCase
) : BaseViewModel<GroupUiState>(GroupUiState()) {

    val state: StateFlow<GroupUiState> = uiState

    private fun currentState(): GroupUiState = uiState.value

    fun setActive(active: Boolean) {
        if (active) {
            if (!currentState().isLoading) loadGroups()
        } else {
            // The screen that started these collectors has left; a collector that outlives it keeps
            // re-filtering the list and re-writing the profile nobody is looking at.
            cancelJob(LIST_JOB)
            cancelJob(DETAIL_JOB)
        }
    }

    fun loadGroups() {
        launchSafeJob(LIST_JOB, "Failed to load public groups") {
            updateState { it.copy(isLoading = true, error = null) }
            // The rows on screen came from the cache and stay there whichever way this goes: the refusal
            // rides behind them as its own emission, and the log is what still tells "never attempted" apart.
            AppLogger.i(DISCOVERY_LOG_TAG, "discovery read attempted")
            getPublicGroups().collectLatest { res ->
                res.onSuccess { list ->
                    AppLogger.i(DISCOVERY_LOG_TAG, "discovery read answered with ${list.size} groups")
                    updateGroupsState(list)
                }.onFailure { e ->
                    AppLogger.w(DISCOVERY_LOG_TAG, "discovery read refused: ${e.toUserMessage()}")
                    updateState { it.copy(isLoading = false, error = e.toUserMessage()) }
                    handleError(e)
                }
            }
        }
    }

    private fun updateGroupsState(rawList: List<Group>) {
        updateState { s ->
            val filtered = filterGroups(rawList, s.searchQuery, s.provinceFilter, s.typeFilter)
            s.copy(
                rawGroups = rawList,
                filteredGroups = filtered,
                groupsByProvince = filterGroups.byProvince(filtered),
                isLoading = false,
                // An answered list is a different verdict from a refusal, so it has to retire the
                // refusal recorded by an earlier emission instead of leaving both on screen.
                error = null
            )
        }
    }

    fun loadGroup(id: String) {
        launchSafeJob(DETAIL_JOB, "Failed to load group details") {
            updateState { it.copy(isLoadingDetail = true, detailError = null) }
            AppLogger.i(DISCOVERY_LOG_TAG, "group profile read attempted for $id")
            observeGroupDetail(id).collectLatest { res ->
                res.onSuccess { g ->
                    if (g == null) {
                        // No row is not a loaded profile. Without a verdict here the screen falls
                        // through state.selectedGroup?.let and renders a page with nothing on it.
                        AppLogger.w(DISCOVERY_LOG_TAG, "group profile read found no row for $id")
                        updateState {
                            it.copy(
                                isLoadingDetail = false,
                                selectedGroup = null,
                                detailError = "This group could not be found. It may have been removed."
                            )
                        }
                    } else {
                        updateState {
                            it.copy(selectedGroup = g, isLoadingDetail = false, detailError = null)
                        }
                    }
                }.onFailure { e ->
                    AppLogger.w(
                        DISCOVERY_LOG_TAG,
                        "group profile read refused for $id: ${e.toUserMessage()}"
                    )
                    updateState { it.copy(isLoadingDetail = false, detailError = e.toUserMessage()) }
                    handleError(e)
                }
            }
        }
    }

    fun updateFilter(
        query: String? = null,
        province: String? = null,
        type: GroupType? = null,
        clearType: Boolean = false,
        clearProvince: Boolean = false
    ) {
        updateState { s ->
            val nQuery = query ?: s.searchQuery
            val nProvince = if (clearProvince) null else (province ?: s.provinceFilter)
            val nType = if (clearType) null else (type ?: s.typeFilter)
            val filtered = filterGroups(s.rawGroups, nQuery, nProvince, nType)
            s.copy(
                searchQuery = nQuery,
                provinceFilter = nProvince,
                typeFilter = nType,
                filteredGroups = filtered,
                groupsByProvince = filterGroups.byProvince(filtered)
            )
        }
    }

    private companion object {
        const val LIST_JOB = "load_groups"
        const val DETAIL_JOB = "load_group_detail"

        /**
         * Its own tag, so one grep of logcat — or of `files/diagnostics.log` on a device whose
         * firmware drops app-authored log lines — answers the one question the list cannot.
         */
        const val DISCOVERY_LOG_TAG = "DiscoveryRead"
    }
}

/**
 * The refusal a discovery screen with rows already on it still has to report. With no rows the
 * screen renders the refusal as its whole body, so this stays null and the empty state owns it.
 */
internal fun browseGroupsNotice(error: String?, hasCachedRows: Boolean): String? =
    if (hasCachedRows) error else null

/** The rows the map can place: saved coordinates, or a province it knows a centre for. */
internal fun placeableGroups(groups: List<Group>): List<Group> =
    groups.filter { it.latitude != null || SaReferenceData.PROVINCE_CENTERS.containsKey(it.province) }

/** A province the user chose explicitly keeps that choice; a search only supplies the default. */
internal fun provinceRowExpanded(stored: Boolean?, isSearching: Boolean): Boolean =
    stored ?: isSearching
