package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * Removes a stored backup by name. Irreversible, so it stays behind an explicit admin action.
 */
class DeleteCloudBackupUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(name: String): Result<Unit> = platformRepository.deleteCloudBackup(name)
}
