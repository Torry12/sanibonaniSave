package com.sanibonani.save.domain.usecase.system

import com.sanibonani.save.domain.repository.system.SyncRepository
import javax.inject.Inject

/**
 * Runs one full synchronisation pass across every pillar.
 */
class SyncAllDataUseCase @Inject constructor(
    private val syncRepository: SyncRepository
) {
    suspend operator fun invoke(): Result<Unit> = syncRepository.syncAllData()
}
