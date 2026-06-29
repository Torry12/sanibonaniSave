package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.data.utils.toUserMessage
import com.sanibonani.save.domain.model.GroupPollWithOptions
import com.sanibonani.save.domain.repository.VotingRepository
import com.sanibonani.save.domain.usecase.voting.CastGroupPollVoteUseCase
import com.sanibonani.save.domain.usecase.voting.CreateGroupPollUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class GroupVotingViewModel @Inject constructor(
    private val votingRepository: VotingRepository,
    private val createGroupPollUseCase: CreateGroupPollUseCase,
    private val castGroupPollVoteUseCase: CastGroupPollVoteUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(GroupVotingUiState())
    val state: StateFlow<GroupVotingUiState> = _state.asStateFlow()

    private var pollsJob: Job? = null
    private val isActive = MutableStateFlow(false)

    fun setActive(active: Boolean) {
        isActive.value = active
        if (!active) {
            pollsJob?.cancel()
            pollsJob = null
        }
    }

    fun loadPolls(groupId: String, memberId: String?) {
        pollsJob?.cancel()
        pollsJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, groupId = groupId, memberId = memberId) }
            votingRepository.observePolls(groupId, memberId).collect { result ->
                result.onSuccess { polls ->
                    _state.update { it.copy(isLoading = false, polls = polls) }
                }.onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
            }
        }
    }

    fun createPoll(title: String, description: String?, options: List<String>, allowMultipleChoice: Boolean = false) {
        val current = _state.value
        val groupId = current.groupId
        if (groupId.isBlank()) {
            _state.update { it.copy(error = "Group context missing. Please re-open this screen.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            createGroupPollUseCase(
                groupId = groupId,
                createdByMemberId = current.memberId,
                title = title,
                description = description,
                options = options,
                allowMultipleChoice = allowMultipleChoice
            ).onSuccess {
                _state.update { it.copy(isSaving = false, message = "Poll created successfully.") }
                loadPolls(groupId, current.memberId)
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
            }
        }
    }

    fun castVote(pollId: String, optionId: String) {
        val current = _state.value
        if (current.groupId.isBlank() || current.memberId.isNullOrBlank()) {
            _state.update { it.copy(error = "Member context missing. Please re-open this screen.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            castGroupPollVoteUseCase(
                groupId = current.groupId,
                pollId = pollId,
                optionId = optionId,
                memberId = current.memberId
            ).onSuccess {
                _state.update { it.copy(isSaving = false, message = "Vote submitted.") }
                loadPolls(current.groupId, current.memberId)
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
            }
        }
    }

    fun closePoll(pollId: String) {
        val current = _state.value
        if (current.groupId.isBlank()) {
            _state.update { it.copy(error = "Group context missing. Please re-open this screen.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            votingRepository.closePoll(pollId)
                .onSuccess {
                    _state.update { it.copy(isSaving = false, message = "Poll closed.") }
                    loadPolls(current.groupId, current.memberId)
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
                }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    override fun onCleared() {
        super.onCleared()
        pollsJob?.cancel()
        _state.update { GroupVotingUiState() }
    }
}

data class GroupVotingUiState(
    val groupId: String = "",
    val memberId: String? = null,
    val polls: List<GroupPollWithOptions> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

