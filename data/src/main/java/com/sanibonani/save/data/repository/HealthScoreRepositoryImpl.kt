package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.GroupHealthScoreEntity
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.GroupHealthScore
import com.sanibonani.save.domain.model.RiskZone
import com.sanibonani.save.domain.repository.HealthScoreRepository
import com.sanibonani.save.domain.utils.OperationKeys
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

class HealthScoreRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase
) : BaseRepository("HealthScoreRepository"), HealthScoreRepository {

    private val json = Json { ignoreUnknownKeys = true }

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

    override suspend fun saveHealthScore(score: GroupHealthScore): Result<Unit> = runCatching {
        retryWithExponentialBackoff {
            supabase.postgrest["group_health_scores"].upsert(score) {
                onConflict = "group_id"
            }
            db.groupHealthScoreDao().upsert(score.toEntity())
        }
        Unit
    }

    override suspend fun getHealthScore(groupId: String): Result<GroupHealthScore> = runCatching {
        val local = db.groupHealthScoreDao().getByGroupId(groupId)
        if (local != null && !isExpired(local.expiresAt)) {
            return@runCatching local.toModel()
        }

        val remote = supabase.postgrest["group_health_scores"].select {
            filter { eq("group_id", groupId) }
        }.decodeSingle<GroupHealthScore>()

        db.groupHealthScoreDao().upsert(remote.toEntity())
        remote
    }.recoverCatching {
        db.groupHealthScoreDao().getByGroupId(groupId)?.toModel()
            ?: throw it
    }

    override suspend fun invalidateCache(groupId: String): Result<Unit> = runCatching {
        db.groupHealthScoreDao().deleteByGroupId(groupId)
    }

    private fun GroupHealthScoreEntity.toModel(): GroupHealthScore {
        val components = json.decodeFromString(
            MapSerializer(String.serializer(), Int.serializer()),
            componentsJson
        )
        val recommendations = json.decodeFromString(
            ListSerializer(String.serializer()),
            recommendationsJson
        )
        return GroupHealthScore(
            groupId = groupId,
            overallScore = overallScore,
            zone = runCatching { RiskZone.valueOf(zone) }.getOrDefault(RiskZone.YELLOW),
            components = components,
            recommendations = recommendations,
            generatedAt = generatedAt,
            expiresAt = expiresAt
        )
    }

    private fun GroupHealthScore.toEntity(): GroupHealthScoreEntity {
        val id = OperationKeys.stableUuid("group_health_score", groupId)
        return GroupHealthScoreEntity(
            id = id,
            groupId = groupId,
            overallScore = overallScore,
            zone = zone.name,
            componentsJson = json.encodeToString(
                MapSerializer(String.serializer(), Int.serializer()),
                components
            ),
            recommendationsJson = json.encodeToString(
                ListSerializer(String.serializer()),
                recommendations
            ),
            generatedAt = generatedAt,
            expiresAt = expiresAt
        )
    }

    private fun isExpired(expiresAt: String?): Boolean {
        if (expiresAt.isNullOrBlank()) return true
        val expiry = runCatching { java.time.Instant.parse(expiresAt) }.getOrNull() ?: return true
        return expiry.isBefore(java.time.Instant.now())
    }

}
