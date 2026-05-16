package com.sanibonani.save.domain.usecase.voting

import com.sanibonani.save.domain.model.GroupPollWithOptions
import com.sanibonani.save.domain.repository.VotingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class CastGroupPollVoteUseCaseTest {

    private class FakeVotingRepository : VotingRepository {
        override fun observePolls(groupId: String, memberId: String?): Flow<Result<List<GroupPollWithOptions>>> {
            return flowOf(Result.success(emptyList()))
        }

        override suspend fun createPoll(
            groupId: String,
            createdByMemberId: String?,
            title: String,
            description: String?,
            options: List<String>,
            allowMultipleChoice: Boolean,
            endsAt: String?
        ): Result<String> = Result.success("poll_1")

        override suspend fun castVote(groupId: String, pollId: String, optionId: String, memberId: String): Result<Unit> {
            return Result.success(Unit)
        }

        override suspend fun closePoll(pollId: String): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `invoke rejects missing member id`() = runTest {
        val useCase = CastGroupPollVoteUseCase(FakeVotingRepository())

        val result = useCase(
            groupId = "g1",
            pollId = "p1",
            optionId = "o1",
            memberId = ""
        )

        assertTrue(result.isFailure)
    }
}

