package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.repository.BaseRepository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.model.GroupHealthScore
import com.sanibonani.save.domain.repository.admin.HealthScoreRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import javax.inject.Inject

class HealthScoreRepositoryImpl @Inject constructor(
    private val supabaseProvider: javax.inject.Provider<SupabaseClient>,
    private val dbProvider: javax.inject.Provider<SanibonaniDatabase>
) : BaseRepository("HealthScoreRepository"), HealthScoreRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    override fun observeGroupHealthScore(groupId: String): Flow<Result<GroupHealthScore>> = observeAndSyncItem(
        dbFlow = db.groupHealthScoreDao().observeByGroupId(groupId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["group_health_scores"].select {
                filter { eq("group_id", groupId) }
            }.decodeSingle<GroupHealthScore>()
        },
        cacheSync = { entity -> db.groupHealthScoreDao().upsert(entity) }
    ).map { result: Result<GroupHealthScore?> ->
        val value = result.getOrNull()
        if (value != null) {
            Result.success(value)
        } else {
            Result.failure(result.exceptionOrNull() ?: IllegalStateException("Health score not found"))
        }
    }

    override suspend fun saveHealthScore(score: GroupHealthScore): Result<Unit> = retryWithExponentialBackoff {
        supabase.postgrest["group_health_scores"].upsert(score) {
            onConflict = "group_id"
        }
        db.groupHealthScoreDao().upsert(score.toEntity())
    }
}

