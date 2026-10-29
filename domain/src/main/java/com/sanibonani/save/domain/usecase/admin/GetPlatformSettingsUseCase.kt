package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * Reads the published platform settings for display.
 *
 * Read-only, unlike [RefreshPlatformConfigUseCase], which also republishes what it reads into the
 * in-memory config billing uses. A screen showing bank details must not silently move the fee
 * numbers another screen is about to charge against.
 */
class GetPlatformSettingsUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(): Result<PlatformConfig> =
        platformRepository.getPlatformSettings()
}
