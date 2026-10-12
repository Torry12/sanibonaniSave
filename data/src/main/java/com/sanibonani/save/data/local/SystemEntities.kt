package com.sanibonani.save.data.local

import androidx.room.*
import com.sanibonani.save.domain.model.PendingOperation
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val payload: String,
    @ColumnInfo(name = "group_id") val groupId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null
)

@Dao
interface PendingOperationDao {
    @Query("SELECT * FROM pending_operations ORDER BY created_at ASC")
    fun observeAll(): Flow<List<PendingOperationEntity>>

    @Query("SELECT * FROM pending_operations ORDER BY created_at ASC")
    suspend fun getAll(): List<PendingOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(operation: PendingOperationEntity)

    @Delete
    suspend fun delete(operation: PendingOperationEntity)

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM pending_operations")
    fun observeCount(): Flow<Int>
}

// ── Mappers ──────────────────────────────────────────────────────────────────

fun PendingOperationEntity.toModel() = PendingOperation(
    id = id,
    type = type,
    payload = payload,
    groupId = groupId,
    createdAt = createdAt,
    retryCount = retryCount,
    lastError = lastError
)

fun PendingOperation.toEntity() = PendingOperationEntity(
    id = id,
    type = type,
    payload = payload,
    groupId = groupId,
    createdAt = createdAt,
    retryCount = retryCount,
    lastError = lastError
)
