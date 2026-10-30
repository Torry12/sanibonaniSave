package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.MemberBehaviorInsight
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * Cross-group member search with the behaviour signal attached to each hit.
 *
 * This is a platform-wide lookup, so callers keep the minimum query length in front of it: a
 * one- or two-character term asks the server to scan every member on the tier.
 */
class SearchMemberBehaviorInsightsUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(query: String): Result<List<MemberBehaviorInsight>> =
        platformRepository.searchMembers(query)
}
