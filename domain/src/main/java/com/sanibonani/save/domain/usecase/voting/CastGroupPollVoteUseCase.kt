package com.sanibonani.save.domain.usecase.voting

import com.sanibonani.save.domain.repository.VotingRepository
import javax.inject.Inject

class CastGroupPollVoteUseCase @Inject constructor(
    private val votingRepository: VotingRepository
) {
    suspend operator fun invoke(
        groupId: String,
        pollId: String,
        optionId: String,
        memberId: String
    ): Result<Unit> {
        if (memberId.isBlank()) {
            return Result.failure(IllegalArgumentException("Member context is required before voting."))
        }
        return votingRepository.castVote(
            groupId = groupId,
            pollId = pollId,
            optionId = optionId,
            memberId = memberId
        )
    }
}

