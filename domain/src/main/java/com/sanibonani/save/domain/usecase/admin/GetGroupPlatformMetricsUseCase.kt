package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.ActuarialMetrics
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * One group's actuarial metrics, as the platform sees them rather than as the group's own cached
 * copy. Used by the analytics drill-down.
 */
class GetGroupPlatformMetricsUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(groupId: String): Result<ActuarialMetrics> =
        platformRepository.getGroupMetrics(groupId)
}
