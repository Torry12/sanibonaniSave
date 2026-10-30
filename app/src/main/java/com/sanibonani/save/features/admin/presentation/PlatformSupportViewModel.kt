package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.usecase.admin.BroadcastPlatformMessageUseCase
import com.sanibonani.save.domain.usecase.admin.RecordImpersonationAuditUseCase
import com.sanibonani.save.domain.usecase.admin.SearchMemberBehaviorInsightsUseCase
import com.sanibonani.save.domain.usecase.member.ObserveGroupMembersUseCase
import com.sanibonani.save.domain.usecase.member.SyncGroupMembersUseCase
import com.sanibonani.save.features.admin.presentation.state.PlatformSupportUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

@HiltViewModel
class PlatformSupportViewModel @Inject constructor(
    private val syncGroupMembers: SyncGroupMembersUseCase,
    private val observeGroupMembers: ObserveGroupMembersUseCase,
    private val searchMemberInsights: SearchMemberBehaviorInsightsUseCase,
    private val recordImpersonationAudit: RecordImpersonationAuditUseCase,
    private val broadcastPlatformMessage: BroadcastPlatformMessageUseCase
) : BaseViewModel<PlatformSupportUiState>(PlatformSupportUiState()) {

    val state: StateFlow<PlatformSupportUiState> = uiState

    fun selectImpersonationGroup(groupId: String) {
        if (groupId.isBlank()) {
            updateState { it.copy(impersonationMembers = emptyList()) }
            return
        }
        launchJob("impersonation_members") {
            updateState { it.copy(isLoadingImpersonationMembers = true) }
            syncGroupMembers(groupId)
            observeGroupMembers(groupId).first().onSuccess { members ->
                updateState { it.copy(impersonationMembers = members, isLoadingImpersonationMembers = false) }
            }.onFailure {
                updateState { it.copy(isLoadingImpersonationMembers = false) }
                handleError(it)
            }
        }
    }

    fun searchAllMembers(query: String) {
        if (query.length < 3) return
        launchJob("search_members") {
            searchMemberInsights(query).onSuccess { insights ->
                updateState { it.copy(memberBehaviorInsights = insights) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        updateState { it.copy(searchQuery = query) }
    }

    fun triggerImpersonationAudit(memberId: String, groupId: String) {
        launchJob("impersonation_audit") {
            recordImpersonationAudit(memberId, groupId).onFailure { handleError(it) }
        }
    }

    fun updateBroadcastMessage(message: String) {
        updateState { it.copy(broadcastMessage = message) }
    }

    fun broadcastMessage(message: String) {
        launchJob("broadcast") {
            updateState { it.copy(isBroadcasting = true, broadcastSuccess = false) }
            broadcastPlatformMessage(message).onSuccess {
                updateState { it.copy(isBroadcasting = false, broadcastSuccess = true, broadcastMessage = "") }
                showMessage("Broadcast sent successfully")
            }.onFailure {
                updateState { s -> s.copy(isBroadcasting = false) }
                handleError(it)
            }
        }
    }

    fun resetLocalData() {
        showMessage("Local data reset is currently in development.")
    }
}
