package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.PlatformAnalytics
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * The platform-wide counters the landing dashboard shows.
 */
class GetPlatformAnalyticsUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(): Result<PlatformAnalytics> =
        platformRepository.getPlatformAnalytics()
}
