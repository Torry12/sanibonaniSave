package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * Asks the platform tier to take a backup now, outside its scheduled one.
 */
class TriggerCloudBackupUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(): Result<Unit> = platformRepository.triggerCloudBackup()
}
