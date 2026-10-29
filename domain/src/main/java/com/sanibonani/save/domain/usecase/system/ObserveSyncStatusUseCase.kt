package com.sanibonani.save.domain.usecase.system

import com.sanibonani.save.domain.model.SyncStatus
import com.sanibonani.save.domain.repository.system.SyncRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The single sync-status stream the whole app shares, so a banner and the bootloader never disagree
 * about what the current pass is doing.
 */
class ObserveSyncStatusUseCase @Inject constructor(
    private val syncRepository: SyncRepository
) {
    operator fun invoke(): StateFlow<SyncStatus> = syncRepository.syncStatus
}
