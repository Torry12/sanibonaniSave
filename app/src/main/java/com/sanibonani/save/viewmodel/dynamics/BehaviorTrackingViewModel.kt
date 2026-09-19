package com.sanibonani.save.viewmodel.dynamics

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.MemberBehaviorTrack
import com.sanibonani.save.domain.repository.BehaviorTrackingRepository
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BehaviorUiState(
    val track: MemberBehaviorTrack? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class BehaviorTrackingViewModel @Inject constructor(
    private val behaviorRepo: BehaviorTrackingRepository
) : BaseViewModel<BehaviorUiState>(BehaviorUiState()) {

    val state: StateFlow<BehaviorUiState> = uiState

    fun loadTrack(memberId: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            behaviorRepo.getMemberBehavior(memberId).onSuccess { t ->
                updateState { it.copy(track = t, isLoading = false) }
            }
        }
    }
}
