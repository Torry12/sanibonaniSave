package com.sanibonani.save.features.admin.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.dynamics.BehaviorTrackingRepository
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BehaviorTrackingUiState(
    val track: MemberBehaviorTrack? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BehaviorTrackingViewModel @Inject constructor(
    private val behaviorRepo: BehaviorTrackingRepository
) : BaseViewModel<BehaviorTrackingUiState>(BehaviorTrackingUiState()) {

    val state: StateFlow<BehaviorTrackingUiState> = uiState

    fun loadTrack(memberId: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            behaviorRepo.getMemberBehavior(memberId).onSuccess { t ->
                updateState { it.copy(track = t, isLoading = false) }
            }.onFailure { e ->
                updateState { it.copy(isLoading = false) }
                handleError(e)
            }
        }
    }

    fun flagMember(memberId: String, reason: String) {
        viewModelScope.launch {
            behaviorRepo.flagMemberForReview(memberId, reason).onSuccess {
                loadTrack(memberId)
            }.onFailure { handleError(it) }
        }
    }

    fun unflagMember(memberId: String) {
        viewModelScope.launch {
            behaviorRepo.unflagMember(memberId).onSuccess {
                loadTrack(memberId)
            }.onFailure { handleError(it) }
        }
    }

    fun suspendMember(memberId: String, reason: String) {
        viewModelScope.launch {
            behaviorRepo.suspendMember(memberId, reason).onSuccess {
                loadTrack(memberId)
            }.onFailure { handleError(it) }
        }
    }

    fun unsuspendMember(memberId: String) {
        viewModelScope.launch {
            behaviorRepo.unsuspendMember(memberId).onSuccess {
                loadTrack(memberId)
            }.onFailure { handleError(it) }
        }
    }
}
