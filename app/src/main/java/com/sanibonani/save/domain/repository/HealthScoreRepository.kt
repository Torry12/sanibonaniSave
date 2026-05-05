package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.GroupHealthScore
import kotlinx.coroutines.flow.Flow

interface HealthScoreRepository {
    /**
     * Observe group health score with automatic sync from remote.
     * Falls back to cached local data if offline or remote unavailable.
     */
    fun observeGroupHealthScore(groupId: String): Flow<Result<GroupHealthScore>>

    /**
     * Save health score to both local DB and remote.
     */
    suspend fun saveHealthScore(score: GroupHealthScore): Result<Unit>

    /**
     * Get cached or fresh health score.
     * Returns cached if not expired, fetches fresh otherwise.
     */
    suspend fun getHealthScore(groupId: String): Result<GroupHealthScore>

    /**
     * Invalidate cache to force refresh.
     */
    suspend fun invalidateCache(groupId: String): Result<Unit>
}

