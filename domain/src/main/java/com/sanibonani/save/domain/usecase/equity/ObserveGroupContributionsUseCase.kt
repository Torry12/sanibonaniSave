package com.sanibonani.save.domain.usecase.equity

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Every contribution recorded in one group, re-emitted on each change.
 */
class ObserveGroupContributionsUseCase @Inject constructor(
    private val contributionRepository: ContributionRepository
) {
    operator fun invoke(groupId: String): Flow<Result<List<Contribution>>> =
        contributionRepository.observeGroupContributions(groupId)
}
