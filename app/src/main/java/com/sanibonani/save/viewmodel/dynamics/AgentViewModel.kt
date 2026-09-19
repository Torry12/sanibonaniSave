package com.sanibonani.save.viewmodel.dynamics

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.AgentTask
import com.sanibonani.save.domain.repository.AgentRepository
import com.sanibonani.save.domain.usecase.SubmitAgentTaskUseCase
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentUiState(
    val tasks: List<AgentTask> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agentRepo: AgentRepository,
    private val submitTaskUseCase: SubmitAgentTaskUseCase
) : BaseViewModel<AgentUiState>(AgentUiState()) {

    val state: StateFlow<AgentUiState> = uiState

    fun loadTasks() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            agentRepo.observeTasks().collect { res ->
                res.onSuccess { list -> updateState { it.copy(tasks = list, isLoading = false) } }
            }
        }
    }
}
