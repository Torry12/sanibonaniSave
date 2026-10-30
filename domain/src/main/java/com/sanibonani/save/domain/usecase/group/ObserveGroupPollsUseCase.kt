package com.sanibonani.save.domain.usecase.group

import com.sanibonani.save.domain.model.GroupPollWithOptions
import com.sanibonani.save.domain.repository.group.VotingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * A group's polls with each member's own ballot already attached.
 *
 * [memberId] is optional because the same screen serves a member answering a poll and an admin
 * watching one: with no member the option counts are the only thing to show.
 */
class ObserveGroupPollsUseCase @Inject constructor(
    private val votingRepository: VotingRepository
) {
    operator fun invoke(groupId: String, memberId: String? = null): Flow<Result<List<GroupPollWithOptions>>> =
        votingRepository.observePolls(groupId, memberId)
}
