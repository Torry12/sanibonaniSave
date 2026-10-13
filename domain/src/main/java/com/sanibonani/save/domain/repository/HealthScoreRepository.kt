package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.GroupHealthScore
import kotlinx.coroutines.flow.Flow

interface HealthScoreRepository {
    fun observeGroupHealthScore(groupId: String): Flow<Result<GroupHealthScore>>
    suspend fun saveHealthScore(score: GroupHealthScore): Result<Unit>
    suspend fun getHealthScore(groupId: String): Result<GroupHealthScore>
    suspend fun invalidateCache(groupId: String): Result<Unit>
}
