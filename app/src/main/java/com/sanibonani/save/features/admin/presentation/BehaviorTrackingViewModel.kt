package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.admin.FlagMemberForReviewUseCase
import com.sanibonani.save.domain.usecase.admin.GenerateBehaviorFinetuningDatasetUseCase
import com.sanibonani.save.domain.usecase.admin.GetMemberBehaviorTrackUseCase
import com.sanibonani.save.domain.usecase.admin.ObserveFraudEventsByMemberUseCase
import com.sanibonani.save.domain.usecase.admin.UnflagMemberUseCase
import com.sanibonani.save.domain.usecase.member.GetMemberByIdUseCase
import com.sanibonani.save.domain.usecase.member.SuspendMemberUseCase
import com.sanibonani.save.domain.usecase.member.UnsuspendMemberUseCase
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

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
    private val getMemberBehaviorTrack: GetMemberBehaviorTrackUseCase,
    private val getMemberById: GetMemberByIdUseCase,
    private val observeFraudEventsByMember: ObserveFraudEventsByMemberUseCase,
    private val generateBehaviorFinetuningDataset: GenerateBehaviorFinetuningDatasetUseCase,
    private val flagMemberForReview: FlagMemberForReviewUseCase,
    private val unflagMemberUseCase: UnflagMemberUseCase,
    private val suspendMemberUseCase: SuspendMemberUseCase,
    private val unsuspendMemberUseCase: UnsuspendMemberUseCase
) : BaseViewModel<BehaviorTrackingUiState>(BehaviorTrackingUiState()) {

    val state: StateFlow<BehaviorTrackingUiState> = uiState

    fun loadMemberData(memberId: String) {
        launchJob("load_member_data") {
            updateState { it.copy(isLoading = true, error = null) }

            coroutineScope {
                val trackDeferred = async<MemberBehaviorTrack?> { getMemberBehaviorTrack(memberId).getOrNull() }
                val memberDeferred = async<Member?> { getMemberById(memberId).getOrNull() }
                val eventsDeferred = async<List<FraudDetectionEvent>> { observeFraudEventsByMember(memberId).firstOrNull()?.getOrNull() ?: emptyList() }

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
            generateBehaviorFinetuningDataset(groupId)
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
            flagMemberForReview(memberId, reason).onSuccess {
                loadMemberData(memberId)
            }.onFailure { handleError(it) }
        }
    }

    fun unflagMember(memberId: String) {
        launchJob("unflag_member") {
            unflagMemberUseCase(memberId).onSuccess {
                loadMemberData(memberId)
            }.onFailure { handleError(it) }
        }
    }

    fun suspendMember(memberId: String, reason: String) {
        launchJob("suspend_member") {
            suspendMemberUseCase(memberId, reason).onSuccess {
                loadMemberData(memberId)
            }.onFailure { handleError(it) }
        }
    }

    fun unsuspendMember(memberId: String) {
        launchJob("unsuspend_member") {
            unsuspendMemberUseCase(memberId).onSuccess {
                loadMemberData(memberId)
            }.onFailure { handleError(it) }
        }
    }
}
