package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.DatabaseBackup
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * The backups the platform tier currently keeps, newest first as the server returns them.
 */
class GetCloudBackupsUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(): Result<List<DatabaseBackup>> =
        platformRepository.getCloudBackups()
}
