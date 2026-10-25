package com.sanibonani.save.domain.repository.system

import com.sanibonani.save.domain.model.DomainPillar
import kotlinx.coroutines.flow.StateFlow

sealed class SyncStatus {
    object Idle : SyncStatus()
    data class Progress(val message: String, val progress: Float) : SyncStatus()
    object Completed : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

interface SyncRepository {
    val syncStatus: StateFlow<SyncStatus>
    val outboxCount: kotlinx.coroutines.flow.Flow<Int>
    suspend fun syncAllData(): Result<Unit>
    suspend fun syncGroupData(groupId: String): Result<Unit>
    suspend fun syncRefresh(pillar: DomainPillar, groupId: String): Result<Unit>

    /**
     * Processes all pending operations in the offline outbox.
     */
    suspend fun processOutbox(): Result<Unit>
}
