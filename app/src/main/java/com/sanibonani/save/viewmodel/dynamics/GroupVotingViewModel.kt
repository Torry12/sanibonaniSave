package com.sanibonani.save.viewmodel.dynamics

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.VotingRepository
import com.sanibonani.save.domain.usecase.voting.CastGroupPollVoteUseCase
import com.sanibonani.save.domain.usecase.voting.CreateGroupPollUseCase
import com.sanibonani.save.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VotingUiState(
    val polls: List<GroupPoll> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GroupVotingViewModel @Inject constructor(
    private val votingRepo: VotingRepository,
    private val createPollUseCase: CreateGroupPollUseCase,
    private val castVoteUseCase: CastGroupPollVoteUseCase
) : BaseViewModel<VotingUiState>(VotingUiState()) {

    val state: StateFlow<VotingUiState> = uiState

    fun loadPolls(groupId: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            votingRepo.observePolls(groupId).collect { res ->
                res.onSuccess { list -> updateState { it.copy(polls = list, isLoading = false) } }
            }
        }
    }

    fun castVote(pollId: String, optionId: String, memberId: String) {
        viewModelScope.launch {
            castVoteUseCase(pollId, optionId, memberId).onSuccess { /* refresh */ }
        }
    }
}
