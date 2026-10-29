package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.repository.admin.PlatformConfigRepository
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * Publishes new platform-wide fees and republishes them to the in-memory config billing reads,
 * so the admin's own session stops quoting the old numbers.
 */
class UpdateGlobalFeesUseCase @Inject constructor(
    private val platformRepository: PlatformRepository,
    private val platformConfigRepository: PlatformConfigRepository
) {
    suspend operator fun invoke(config: PlatformConfig): Result<Unit> =
        platformRepository.updateGlobalFees(config).onSuccess { platformConfigRepository.hydrate(config) }
}
