package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.PlatformFeeConfig
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * Writes one tier's fee row.
 */
class UpdateGranularFeeConfigUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(config: PlatformFeeConfig): Result<Unit> =
        platformRepository.updateGranularFeeConfig(config)
}
