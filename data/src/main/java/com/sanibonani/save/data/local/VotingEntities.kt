package com.sanibonani.save.data.local

import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "group_polls",
    indices = [
        Index("group_id"),
        Index("status"),
    ],
)
data class GroupPollEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "created_by_member_id") val createdByMemberId: String?,
    val title: String,
    val description: String?,
    val type: PollType,
    val status: PollStatus,
    @ColumnInfo(name = "effect_status") val effectStatus: EffectStatus,
    @ColumnInfo(name = "effect_data") val effectData: String?,
    @ColumnInfo(name = "allow_multiple_choice") val allowMultipleChoice: Boolean,
    @ColumnInfo(name = "starts_at") val startsAt: String?,
    @ColumnInfo(name = "ends_at") val endsAt: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?
)

@Entity(
    tableName = "group_poll_options",
    indices = [Index("poll_id")],
)
data class GroupPollOptionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "poll_id") val pollId: String,
    val label: String,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: String?,
)

@Entity(
    tableName = "group_poll_votes",
    indices = [
        Index("poll_id"),
        Index("member_id"),
        Index(value = ["poll_id", "member_id"], unique = true),
    ],
)
data class GroupPollVoteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "poll_id") val pollId: String,
    @ColumnInfo(name = "option_id") val optionId: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?
)

data class GroupPollWithDetailsEntity(
    @Embedded val poll: GroupPollEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "poll_id"
    )
    val options: List<GroupPollOptionEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "poll_id"
    )
    val votes: List<GroupPollVoteEntity>
)

@Dao
interface GroupVotingDao {
    @RoomTransaction
    @Query("SELECT * FROM group_polls WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observePollsWithDetails(groupId: String): Flow<List<GroupPollWithDetailsEntity>>

    @Query("SELECT * FROM group_polls WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observePollsByGroup(groupId: String): Flow<List<GroupPollEntity>>

    @Query("SELECT * FROM group_polls WHERE group_id = :groupId")
    suspend fun getGroupPolls(groupId: String): List<GroupPollEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPolls(polls: List<GroupPollEntity>)

    @Query("DELETE FROM group_polls WHERE group_id = :groupId")
    suspend fun deletePollsByGroupId(groupId: String)

    @RoomTransaction
    suspend fun syncPolls(groupId: String, polls: List<GroupPollEntity>) {
        val current = getGroupPolls(groupId).map { it.id }.toSet()
        val incoming = polls.map { it.id }.toSet()
        (current - incoming).forEach { deletePoll(it) }
        upsertPolls(polls)
    }

    @Query("DELETE FROM group_polls WHERE id = :id")
    suspend fun deletePoll(id: String)

    @Query("SELECT * FROM group_poll_options WHERE poll_id = :pollId ORDER BY position ASC")
    fun observeOptionsByPoll(pollId: String): Flow<List<GroupPollOptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOptions(options: List<GroupPollOptionEntity>)

    @Query("DELETE FROM group_poll_options WHERE poll_id IN (SELECT id FROM group_polls WHERE group_id = :groupId)")
    suspend fun deleteOptionsByGroupId(groupId: String)

    @Query("SELECT * FROM group_poll_votes WHERE poll_id = :pollId")
    fun observeVotesByPoll(pollId: String): Flow<List<GroupPollVoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVotes(votes: List<GroupPollVoteEntity>)

    @Query("DELETE FROM group_poll_votes WHERE poll_id IN (SELECT id FROM group_polls WHERE group_id = :groupId)")
    suspend fun deleteVotesByGroupId(groupId: String)

    @Query("DELETE FROM group_polls")
    suspend fun clearAll()
}

// ── Mappers ──────────────────────────────────────────────────────────────────

fun GroupPollEntity.toModel() = GroupPoll(
    id = id,
    groupId = groupId,
    createdByMemberId = createdByMemberId,
    title = title,
    description = description,
    type = type,
    status = status,
    effectStatus = effectStatus,
    effectData = effectData,
    allowMultipleChoice = allowMultipleChoice,
    startsAt = startsAt,
    endsAt = endsAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun GroupPoll.toEntity() = GroupPollEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    createdByMemberId = createdByMemberId,
    title = title,
    description = description,
    type = type,
    status = status,
    effectStatus = effectStatus,
    effectData = effectData,
    allowMultipleChoice = allowMultipleChoice,
    startsAt = startsAt,
    endsAt = endsAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun GroupPollOptionEntity.toModel() = GroupPollOption(
    id = id,
    pollId = pollId,
    label = label,
    position = position,
    createdAt = createdAt
)

fun GroupPollOption.toEntity() = GroupPollOptionEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    pollId = pollId,
    label = label,
    position = position,
    createdAt = createdAt
)

fun GroupPollVoteEntity.toModel() = GroupPollVote(
    id = id,
    pollId = pollId,
    optionId = optionId,
    memberId = memberId,
    groupId = groupId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun GroupPollVote.toEntity() = GroupPollVoteEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    pollId = pollId,
    optionId = optionId,
    memberId = memberId,
    groupId = groupId,
    createdAt = createdAt,
    updatedAt = updatedAt
)
