package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.PlatformFeeConfig
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * The per-tier fee rows published on the platform.
 */
class GetGranularFeeConfigsUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(): Result<List<PlatformFeeConfig>> =
        platformRepository.getGranularFeeConfigs()
}
