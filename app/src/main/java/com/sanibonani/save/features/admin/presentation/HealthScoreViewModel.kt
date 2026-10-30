package com.sanibonani.save.features.admin.presentation

import com.sanibonani.save.domain.model.GroupHealthScore
import com.sanibonani.save.domain.usecase.actuarial.CalculateGroupHealthScoreUseCase
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class HealthScoreUiState(
    val score: GroupHealthScore? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HealthScoreViewModel @Inject constructor(
    private val calculateGroupHealthScore: CalculateGroupHealthScoreUseCase
) : BaseViewModel<HealthScoreUiState>(HealthScoreUiState()) {

    val state: StateFlow<HealthScoreUiState> = uiState
    private var mutationJob: Job? = null

    fun loadHealthScore(groupId: String) {
        mutationJob?.cancel()
        mutationJob = launchJob("load_health_score") {
            updateState { it.copy(isLoading = true, error = null) }
            calculateGroupHealthScore(groupId)
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
