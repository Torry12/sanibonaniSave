package com.sanibonani.save.features.admin.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.GroupHealthScore
import com.sanibonani.save.domain.usecase.dynamics.CalculateGroupHealthScoreUseCase
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    private var mutationJob: Job? = null

    fun loadHealthScore(groupId: String) {
        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            updateState { it.copy(isLoading = true, error = null) }
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
