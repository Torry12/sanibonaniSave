package com.sanibonani.save.viewmodel.dynamics

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.GroupHealthScore
import com.sanibonani.save.domain.usecase.CalculateGroupHealthScoreUseCase
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HealthScoreUiState(
    val score: GroupHealthScore? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HealthScoreViewModel @Inject constructor(
    private val calculateGroupHealthScoreUseCase: CalculateGroupHealthScoreUseCase
) : BaseViewModel<HealthScoreUiState>(HealthScoreUiState()) {

    val state: StateFlow<HealthScoreUiState> = uiState
    private val isActive = MutableStateFlow(false)
    private var mutationJob: Job? = null

    fun setActive(active: Boolean) {
        isActive.value = active
        if (!active) {
            mutationJob?.cancel()
            mutationJob = null
        }
    }

    fun loadHealthScore(groupId: String) {
        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
            withLoading {
                calculateGroupHealthScoreUseCase(groupId)
                    .onSuccess { score ->
                        updateState { it.copy(score = score, isLoading = false) }
                    }
                    .onFailure { e ->
                        updateState { it.copy(isLoading = false) }
                        handleError(e)
                    }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        updateState { HealthScoreUiState() }
    }
}
