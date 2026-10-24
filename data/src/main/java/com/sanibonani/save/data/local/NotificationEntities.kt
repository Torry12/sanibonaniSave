package com.sanibonani.save.data.local

import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "notifications",
    indices = [
        Index("group_id"),
        Index("member_id"),
        Index("trigger_event"),
        Index("created_at"),
    ],
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "member_id") val memberId: String?,
    val message: String,
    val channel: NotifChannel,
    @ColumnInfo(name = "trigger_event") val triggerEvent: NotifEvent,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observeNotifications(groupId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE group_id = :groupId")
    suspend fun getGroupNotifications(groupId: String): List<NotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotifications(notifications: List<NotificationEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotification(notification: NotificationEntity): Long

    @Query("DELETE FROM notifications WHERE group_id = :groupId")
    suspend fun deleteNotificationsByGroupId(groupId: String)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()

    @RoomTransaction
    suspend fun syncNotifications(groupId: String, notifications: List<NotificationEntity>) {
        val current = getGroupNotifications(groupId).map { it.id }.toSet()
        val incoming = notifications.map { it.id }.toSet()
        (current - incoming).forEach { deleteById(it) }
        upsertNotifications(notifications)
    }

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ── Mappers ──────────────────────────────────────────────────────────────────

fun NotificationEntity.toModel() = AppNotification(
    id = id,
    groupId = groupId,
    memberId = memberId,
    message = message,
    channel = channel,
    triggerEvent = triggerEvent,
    createdAt = createdAt
)

fun AppNotification.toEntity() = NotificationEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId ?: "",
    memberId = memberId,
    message = message,
    channel = channel,
    triggerEvent = triggerEvent,
    createdAt = createdAt
)
