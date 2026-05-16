package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.GroupPollWithOptions
import kotlinx.coroutines.flow.Flow

interface VotingRepository {
    fun observePolls(groupId: String, memberId: String? = null): Flow<Result<List<GroupPollWithOptions>>>

    suspend fun createPoll(
        groupId: String,
        createdByMemberId: String?,
        title: String,
        description: String?,
        options: List<String>,
        allowMultipleChoice: Boolean = false,
        endsAt: String? = null
    ): Result<String>

    suspend fun castVote(
        groupId: String,
        pollId: String,
        optionId: String,
        memberId: String
    ): Result<Unit>

    suspend fun closePoll(pollId: String): Result<Unit>
}

