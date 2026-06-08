package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.GroupHealthScore
import com.sanibonani.save.domain.usecase.CalculateGroupHealthScoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
) : ViewModel() {

    private val _state = MutableStateFlow(HealthScoreUiState())
    val state: StateFlow<HealthScoreUiState> = _state.asStateFlow()

    private val isActive = MutableStateFlow(false)

    fun setActive(active: Boolean) {
        isActive.value = active
    }

    fun loadHealthScore(groupId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            calculateGroupHealthScoreUseCase(groupId)
                .onSuccess { score ->
                    _state.update { it.copy(score = score, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _state.update { HealthScoreUiState() }
    }
}
