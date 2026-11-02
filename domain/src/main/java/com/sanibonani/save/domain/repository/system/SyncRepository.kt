package com.sanibonani.save.domain.repository.system

import com.sanibonani.save.domain.model.DomainPillar
import com.sanibonani.save.domain.model.SyncStatus
import kotlinx.coroutines.flow.StateFlow

interface SyncRepository {
    val syncStatus: StateFlow<SyncStatus>
    val outboxCount: kotlinx.coroutines.flow.Flow<Int>

    /**
     * Per group, the relations whose last pull did not land.
     *
     * Boot is best-effort — an offline device is meant to open on the Room cache — so a completed
     * sync does not mean every book arrived. A group missing from this map was covered by a sync
     * that reported nothing outstanding; a relation is only dropped when a later sync read it.
     * Anything that judges the money in a group has to ask this before calling what it sees verified.
     */
    val staleGroups: StateFlow<Map<String, Set<String>>>

    suspend fun syncAllData(): Result<Unit>
    suspend fun syncGroupData(groupId: String): Result<Unit>
    suspend fun syncRefresh(pillar: DomainPillar, groupId: String): Result<Unit>

    /**
     * Processes all pending operations in the offline outbox.
     */
    suspend fun processOutbox(): Result<Unit>
}
