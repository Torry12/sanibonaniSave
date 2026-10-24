package com.sanibonani.save.data.local

import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import com.sanibonani.save.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "user_profiles",
)
data class UserProfileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "full_name") val fullName: String?,
    val email: String?,
    val role: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Data class representing a User Profile with all their group memberships.
 */
data class UserWithMembershipsEntity(
    @Embedded val user: UserProfileEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "user_id"
    )
    val memberships: List<MemberEntity>
)

@Dao
interface UserProfileDao {
    @RoomTransaction
    @Query("SELECT * FROM user_profiles WHERE id = :userId")
    fun observeUserWithMemberships(userId: String): Flow<UserWithMembershipsEntity?>

    @Query("SELECT * FROM user_profiles WHERE id = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE id = :userId LIMIT 1")
    suspend fun getProfile(userId: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("DELETE FROM user_profiles WHERE id = :userId")
    suspend fun deleteProfile(userId: String)
}

// ── Mappers ──────────────────────────────────────────────────────────────────

fun UserProfileEntity.toModel() = UserProfile(
    id = id,
    fullName = fullName,
    email = email,
    role = role
)

fun UserProfile.toEntity() = UserProfileEntity(
    id = id,
    fullName = fullName,
    email = email,
    role = role
)
