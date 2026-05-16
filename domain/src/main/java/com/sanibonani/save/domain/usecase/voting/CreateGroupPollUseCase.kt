package com.sanibonani.save.domain.usecase.voting

import com.sanibonani.save.domain.repository.VotingRepository
import javax.inject.Inject

class CreateGroupPollUseCase @Inject constructor(
    private val votingRepository: VotingRepository
) {
    suspend operator fun invoke(
        groupId: String,
        createdByMemberId: String?,
        title: String,
        description: String?,
        options: List<String>,
        allowMultipleChoice: Boolean = false,
        endsAt: String? = null
    ): Result<String> {
        if (title.isBlank()) {
            return Result.failure(IllegalArgumentException("Poll title is required."))
        }
        if (options.map { it.trim() }.count { it.isNotBlank() } < 2) {
            return Result.failure(IllegalArgumentException("Please provide at least two options."))
        }
        return votingRepository.createPoll(
            groupId = groupId,
            createdByMemberId = createdByMemberId,
            title = title,
            description = description,
            options = options,
            allowMultipleChoice = allowMultipleChoice,
            endsAt = endsAt
        )
    }
}

