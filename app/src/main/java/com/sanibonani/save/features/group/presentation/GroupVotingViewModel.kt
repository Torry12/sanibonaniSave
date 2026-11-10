package com.sanibonani.save.features.group.presentation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.group.CastGroupPollVoteUseCase
import com.sanibonani.save.domain.usecase.group.CloseGroupPollUseCase
import com.sanibonani.save.domain.usecase.group.CreateGroupPollUseCase
import com.sanibonani.save.domain.usecase.group.ObserveGroupPollsUseCase
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class GroupVotingUiState(
    val groupId: String = "",
    val memberId: String? = null,
    val polls: List<GroupPollWithOptions> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class GroupVotingViewModel @Inject constructor(
    private val observeGroupPolls: ObserveGroupPollsUseCase,
    private val createGroupPoll: CreateGroupPollUseCase,
    private val castGroupPollVote: CastGroupPollVoteUseCase,
    private val closeGroupPoll: CloseGroupPollUseCase
) : BaseViewModel<GroupVotingUiState>(GroupVotingUiState()) {

    val state: StateFlow<GroupVotingUiState> = uiState
    private var pollsJob: Job? = null

    fun setActive(active: Boolean) { if (!active) pollsJob?.cancel() }

    fun loadPolls(groupId: String, memberId: String?) {
        updateState { it.copy(groupId = groupId, memberId = memberId) }
        pollsJob?.cancel()
        pollsJob = launchJob("load_polls") {
            observeGroupPolls(groupId, memberId).collectLatest { res ->
                res.onSuccess { list -> updateState { it.copy(polls = list) } }
                    // The screen renders state.error, so a failed poll read says so instead of
                    // showing an empty list.
                    .onFailure { e -> updateState { it.copy(error = e.toUserMessage()) } }
            }
        }
    }

    fun createPoll(title: String, description: String?, options: List<String>, allowMultipleChoice: Boolean = false) {
        val s = uiState.value
        launchJob("create_poll") {
            createGroupPoll(s.groupId, s.memberId, title, description, options, allowMultipleChoice)
                .onSuccess {
                    updateState { it.copy(message = "Poll created", error = null) }
                }
                // A poll the server refuses is an ordinary answer, but until now it was the only
                // answer this call could give: the admin pressed Create and nothing changed.
                .onFailure { error ->
                    updateState { it.copy(message = null, error = error.toUserMessage()) }
                }
        }
    }

    fun castVote(pollId: String, optionId: String) {
        val s = uiState.value
        if (s.memberId == null) return
        launchJob("cast_vote") {
            castGroupPollVote(s.groupId, pollId, optionId, s.memberId)
                .onSuccess {
                    updateState { it.copy(message = "Vote submitted", error = null) }
                }
                // A vote is a record the member believes was counted, so a refusal - closed poll,
                // not a member, a duplicate ballot - has to be visible instead of silently dropped.
                .onFailure { error ->
                    updateState { it.copy(message = null, error = error.toUserMessage()) }
                }
        }
    }

    fun closePoll(pollId: String) {
        launchJob("close_poll") {
            closeGroupPoll(pollId)
                .onSuccess { updateState { it.copy(message = "Poll closed", error = null) } }
                // The database decides who may close a poll, so a refusal is an ordinary answer
                // here, not an anomaly. Without this leg the button appeared to do nothing.
                .onFailure { error ->
                    updateState { it.copy(message = null, error = error.toUserMessage()) }
                }
        }
    }
}
