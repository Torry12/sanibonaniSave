package com.sanibonani.save.features.group.presentation

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.dynamics.VotingRepository
import com.sanibonani.save.domain.usecase.voting.CastGroupPollVoteUseCase
import com.sanibonani.save.domain.usecase.voting.CreateGroupPollUseCase
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupVotingUiState(
    val groupId: String = "",
    val memberId: String? = null,
    val polls: List<GroupPollWithOptions> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class GroupVotingViewModel @Inject constructor(
    private val votingRepository: VotingRepository,
    private val createGroupPollUseCase: CreateGroupPollUseCase,
    private val castGroupPollVoteUseCase: CastGroupPollVoteUseCase
) : BaseViewModel<GroupVotingUiState>(GroupVotingUiState()) {

    val state: StateFlow<GroupVotingUiState> = uiState
    private var pollsJob: Job? = null

    fun setActive(active: Boolean) { if (!active) pollsJob?.cancel() }

    fun loadPolls(groupId: String, memberId: String?) {
        updateState { it.copy(groupId = groupId, memberId = memberId) }
        pollsJob?.cancel()
        pollsJob = viewModelScope.launch {
            votingRepository.observePolls(groupId, memberId).collectLatest { res ->
                res.onSuccess { list -> updateState { it.copy(polls = list, isLoading = false) } }
                    .onFailure { updateState { it.copy(isLoading = false) } }
            }
        }
    }

    fun createPoll(title: String, description: String?, options: List<String>, allowMultipleChoice: Boolean = false) {
        val s = uiState.value
        viewModelScope.launch {
            updateState { it.copy(isSaving = true) }
            createGroupPollUseCase(s.groupId, s.memberId, title, description, options, allowMultipleChoice).onSuccess {
                updateState { it.copy(isSaving = false, message = "Poll created") }
            }
        }
    }

    fun castVote(pollId: String, optionId: String) {
        val s = uiState.value
        if (s.memberId == null) return
        viewModelScope.launch {
            castGroupPollVoteUseCase(s.groupId, pollId, optionId, s.memberId).onSuccess {
                updateState { it.copy(message = "Vote submitted") }
            }
        }
    }

    fun closePoll(pollId: String) {
        viewModelScope.launch {
            votingRepository.closePoll(pollId).onSuccess {
                updateState { it.copy(message = "Poll closed") }
            }
        }
    }
}
