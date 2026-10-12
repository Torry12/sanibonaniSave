package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

import com.sanibonani.save.domain.repository.member.MemberRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull

data class BehaviorTrackingUiState(
    val member: Member? = null,
    val track: MemberBehaviorTrack? = null,
    val recentFraudEvents: List<FraudDetectionEvent> = emptyList(),
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BehaviorTrackingViewModel @Inject constructor(
    private val behaviorRepo: BehaviorTrackingRepository,
    private val memberRepo: MemberRepository
) : BaseViewModel<BehaviorTrackingUiState>(BehaviorTrackingUiState()) {

    val state: StateFlow<BehaviorTrackingUiState> = uiState

    fun loadMemberData(memberId: String) {
        launchJob("load_member_data") {
            updateState { it.copy(isLoading = true, error = null) }

            coroutineScope {
                val trackDeferred = async<MemberBehaviorTrack?> { behaviorRepo.getMemberBehavior(memberId).getOrNull() }
                val memberDeferred = async<Member?> { memberRepo.getMemberById(memberId).getOrNull() }
                val eventsDeferred = async<List<FraudDetectionEvent>> { behaviorRepo.observeFraudEventsByMember(memberId).firstOrNull()?.getOrNull() ?: emptyList() }

                val track = trackDeferred.await()
                val member = memberDeferred.await()
                val events = eventsDeferred.await()

                updateState { it.copy(
                    track = track,
                    member = member,
                    recentFraudEvents = events,
                    isLoading = false
                ) }
            }
        }
    }

    fun exportDataset(groupId: String) {
        launchJob("export_dataset") {
            updateState { it.copy(isExporting = true) }
            behaviorRepo.generateFinetuningDataset(groupId)
                .onSuccess { dataset ->
                    updateState { it.copy(isExporting = false) }
                    showMessage("Dataset generated with ${dataset.size} examples")
                }
                .onFailure { e ->
                    updateState { it.copy(isExporting = false) }
                    handleError(e)
                }
        }
    }

    fun flagMember(memberId: String, reason: String) {
        launchJob("flag_member") {
            behaviorRepo.flagMemberForReview(memberId, reason).onSuccess {
                loadMemberData(memberId)
            }.onFailure { handleError(it) }
        }
    }

    fun unflagMember(memberId: String) {
        launchJob("unflag_member") {
            behaviorRepo.unflagMember(memberId).onSuccess {
                loadMemberData(memberId)
            }.onFailure { handleError(it) }
        }
    }

    fun suspendMember(memberId: String, reason: String) {
        launchJob("suspend_member") {
            behaviorRepo.suspendMember(memberId, reason).onSuccess {
                loadMemberData(memberId)
            }.onFailure { handleError(it) }
        }
    }

    fun unsuspendMember(memberId: String) {
        launchJob("unsuspend_member") {
            behaviorRepo.unsuspendMember(memberId).onSuccess {
                loadMemberData(memberId)
            }.onFailure { handleError(it) }
        }
    }
}
