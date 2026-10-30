package com.sanibonani.save.domain.usecase.group

import com.sanibonani.save.domain.repository.group.VotingRepository
import javax.inject.Inject

/**
 * Stops a poll from taking further votes.
 *
 * Entitlement is decided by the database, so a refusal here is an ordinary answer rather than an
 * anomaly - callers surface it instead of treating it as a no-op.
 */
class CloseGroupPollUseCase @Inject constructor(
    private val votingRepository: VotingRepository
) {
    suspend operator fun invoke(pollId: String): Result<Unit> = votingRepository.closePoll(pollId)
}
