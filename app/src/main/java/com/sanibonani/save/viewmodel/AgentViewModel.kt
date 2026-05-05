package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.AgentTask
import com.sanibonani.save.domain.model.AgentResult
import com.sanibonani.save.domain.usecase.SubmitAgentTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val submitAgentTask: SubmitAgentTaskUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    fun submitTask(task: AgentTask) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            submitAgentTask(task).collect { result ->
                result.onSuccess { agentResult ->
                    val isTerminal = agentResult.status.equals("completed", ignoreCase = true) ||
                        agentResult.status.equals("failed", ignoreCase = true)
                    _state.update { it.copy(result = agentResult, isLoading = !isTerminal, error = null) }
                }.onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                }
            }
        }
    }
}

data class AgentUiState(
    val result: AgentResult? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

