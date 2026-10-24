package com.sanibonani.save.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.sanibonani.save.domain.model.AuditLog
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Entity(
    tableName = "audit_logs",
    indices = [
        Index("actor_id"),
        Index("target_member_id"),
        Index("target_group_id"),
        Index("action")
    ]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "actor_id") val actorId: String,
    @ColumnInfo(name = "target_member_id") val targetMemberId: String?,
    @ColumnInfo(name = "target_group_id") val targetGroupId: String?,
    val action: String,
    @ColumnInfo(name = "related_id") val relatedId: String? = null,
    val details: String?, // JSON string
    @ColumnInfo(name = "created_at") val createdAt: String?
)

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs WHERE target_group_id = :groupId ORDER BY created_at DESC")
    fun observeLogsForGroup(groupId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE target_member_id = :memberId ORDER BY created_at DESC")
    fun observeLogsForMember(memberId: String): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLogs(logs: List<AuditLogEntity>)

    @Query("DELETE FROM audit_logs WHERE target_group_id = :groupId")
    suspend fun clearLogsForGroup(groupId: String)

    @Transaction
    suspend fun syncLogsForGroup(groupId: String, logs: List<AuditLogEntity>) {
        clearLogsForGroup(groupId)
        upsertLogs(logs)
    }

    @Query("DELETE FROM audit_logs")
    suspend fun clearAll()
}

@Entity(tableName = "audit_reports")
@Serializable
data class AuditReportEntity(
    @PrimaryKey val id: String, // format: "type_targetId" (e.g., "member_123", "platform_global")
    val type: String, // "member", "group", "platform"
    @SerialName("target_id") val targetId: String,
    @SerialName("content_json") val contentJson: String,
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_reports WHERE id = :id")
    fun observeReport(id: String): Flow<AuditReportEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReport(report: AuditReportEntity)

    @Query("DELETE FROM audit_reports WHERE id = :id")
    suspend fun deleteReport(id: String)

    @Query("DELETE FROM audit_reports")
    suspend fun clearAll()
}

// ── Mappers ──────────────────────────────────────────────────────────────────

fun AuditLogEntity.toModel() = AuditLog(
    id = id,
    actorId = actorId,
    targetMemberId = targetMemberId,
    targetGroupId = targetGroupId,
    action = action,
    relatedId = relatedId,
    details = details?.let { Json.parseToJsonElement(it) },
    createdAt = createdAt
)

fun AuditLog.toEntity() = AuditLogEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    actorId = actorId,
    targetMemberId = targetMemberId,
    targetGroupId = targetGroupId,
    action = action,
    relatedId = relatedId,
    details = details?.let { Json.encodeToString(it) },
    createdAt = createdAt
)
