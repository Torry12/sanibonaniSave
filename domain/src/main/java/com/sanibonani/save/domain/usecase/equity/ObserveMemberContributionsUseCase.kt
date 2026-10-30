package com.sanibonani.save.domain.usecase.equity

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * A member's contributions inside one group, re-emitted on every cache or server change.
 */
class ObserveMemberContributionsUseCase @Inject constructor(
    private val contributionRepository: ContributionRepository
) {
    operator fun invoke(memberId: String, groupId: String): Flow<Result<List<Contribution>>> =
        contributionRepository.observeMemberContributions(memberId, groupId)
}
