package com.sanibonani.save.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "group_health_scores",
    indices = [
        Index("group_id", unique = true),
        Index("generated_at"),
    ],
)
data class GroupHealthScoreEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "overall_score") val overallScore: Int,
    val zone: String,
    @ColumnInfo(name = "components_json") val componentsJson: String,
    @ColumnInfo(name = "recommendations_json") val recommendationsJson: String,
    @ColumnInfo(name = "generated_at") val generatedAt: String,
    @ColumnInfo(name = "expires_at") val expiresAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface GroupHealthScoreDao {
    @Query("SELECT * FROM group_health_scores WHERE group_id = :groupId LIMIT 1")
    fun observeByGroupId(groupId: String): Flow<GroupHealthScoreEntity?>

    @Query("SELECT * FROM group_health_scores WHERE group_id = :groupId LIMIT 1")
    suspend fun getByGroupId(groupId: String): GroupHealthScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(score: GroupHealthScoreEntity): Long

    @Query("DELETE FROM group_health_scores WHERE group_id = :groupId")
    suspend fun deleteByGroupId(groupId: String)

    @Query("DELETE FROM group_health_scores")
    suspend fun clearAll()
}
