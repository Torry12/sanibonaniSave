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
    val track: MemberBehaviorTrack? = null,
    val isLoading: Boolean = false
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
            updateState { it.copy(isLoading = true) }
            // A null track is the answer "this member has no history recorded"; a refused read is
            // not that answer, and blanking the panel would present it as one. Flagging and
            // suspending both refresh through here straight after writing, so the last proven
            // track stays and the refusal is reported instead.
            getMemberBehaviorTrack(memberId)
                .onSuccess { track -> updateState { it.copy(track = track, isLoading = false) } }
                .onFailure { e ->
                    updateState { it.copy(isLoading = false) }
                    handleError(e, "Unable to refresh this member's behaviour track. The panel still shows the last result.")
                }
        }
    }

    fun exportDataset(groupId: String) {
        launchJob("export_dataset") {
            generateBehaviorFinetuningDataset(groupId)
                .onSuccess { dataset ->
                    showMessage("Dataset generated with ${dataset.size} examples")
                }
                .onFailure { e ->
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
