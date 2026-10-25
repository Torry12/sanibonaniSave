package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.repository.admin.PlatformConfigRepository
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * Re-reads the published platform settings and republishes them to the in-memory config that
 * billing reads. A failed read leaves the last known config in place.
 *
 * Kept out of [PlatformConfigRepository] because the read path runs through the actuarial
 * dependency graph, which itself consumes the fee config.
 */
class RefreshPlatformConfigUseCase @Inject constructor(
    private val platformRepository: PlatformRepository,
    private val platformConfigRepository: PlatformConfigRepository
) {
    suspend operator fun invoke(): Result<PlatformConfig> =
        platformRepository.getPlatformSettings().onSuccess { platformConfigRepository.hydrate(it) }
}
