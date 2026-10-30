package com.sanibonani.save.domain.repository.system

import com.sanibonani.save.domain.model.DomainPillar
import com.sanibonani.save.domain.model.SyncStatus
import kotlinx.coroutines.flow.StateFlow

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
