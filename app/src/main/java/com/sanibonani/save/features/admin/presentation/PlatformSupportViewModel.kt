package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.features.admin.presentation.state.PlatformSupportUiState
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import javax.inject.Inject

@HiltViewModel
class PlatformSupportViewModel @Inject constructor(
    private val platformRepo: PlatformRepository,
    private val memberRepo: MemberRepository,
    private val auditLogRepo: AuditLogRepository,
    private val supabaseRepo: SupabaseRepository
) : BaseViewModel<PlatformSupportUiState>(PlatformSupportUiState()) {

    val state: StateFlow<PlatformSupportUiState> = uiState

    fun selectImpersonationGroup(groupId: String) {
        if (groupId.isBlank()) {
            updateState { it.copy(impersonationMembers = emptyList()) }
            return
        }
        launchJob("impersonation_members") {
            updateState { it.copy(isLoadingImpersonationMembers = true) }
            memberRepo.syncGroupMembers(groupId)
            memberRepo.getGroupMembers(groupId).first().onSuccess { members ->
                updateState { it.copy(impersonationMembers = members, isLoadingImpersonationMembers = false) }
            }
        }
    }

    fun searchAllMembers(query: String) {
        if (query.length < 3) return
        launchJob("search_members") {
            platformRepo.searchMembers(query).onSuccess { insights ->
                updateState { it.copy(memberBehaviorInsights = insights) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        updateState { it.copy(searchQuery = query) }
    }

    fun triggerImpersonationAudit(memberId: String, groupId: String) {
        launchJob("impersonation_audit") {
            auditLogRepo.logEvent(
                AuditLog(
                    actorId = supabaseRepo.currentUserId ?: "admin",
                    targetMemberId = memberId,
                    targetGroupId = groupId,
                    action = "IMPERSONATION_REQUESTED",
                    details = buildJsonObject {
                        put("source", "PlatformAdminPortal")
                        put("timestamp", System.currentTimeMillis())
                    }
                )
            )
        }
    }

    fun updateBroadcastMessage(message: String) {
        updateState { it.copy(broadcastMessage = message) }
    }

    fun broadcastMessage(message: String) {
        launchJob("broadcast") {
            updateState { it.copy(isBroadcasting = true, broadcastSuccess = false) }
            platformRepo.broadcastPlatformMessage(message).onSuccess {
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
