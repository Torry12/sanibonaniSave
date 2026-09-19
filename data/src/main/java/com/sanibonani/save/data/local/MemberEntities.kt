package com.sanibonani.save.data.local

import androidx.room.*
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "members",
    indices = [
        Index("group_id"),
        Index("user_id"),
        Index("status"),
        Index("member_key", unique = true),
    ],
)
data class MemberEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "user_id") val userId: String?,
    @ColumnInfo(name = "member_key") val memberKey: String?,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "id_number") val idNumber: String?,
    val phone: String?,
    val email: String?,
    val street: String?,
    val suburb: String?,
    val city: String?,
    val province: String?,
    val status: MemberStatus,
    @ColumnInfo(name = "joined_at") val joinedAt: String?,
    @ColumnInfo(name = "probation_end_at") val probationEndAt: String?,
    @ColumnInfo(name = "profile_photo_url") val profilePhotoUrl: String?,
    @ColumnInfo(name = "document_1_url") val document1Url: String?,
    @ColumnInfo(name = "document_1_type") val document1Type: String?,
    @ColumnInfo(name = "document_1_status") val document1Status: DocumentStatus,
    @ColumnInfo(name = "document_2_url") val document2Url: String?,
    @ColumnInfo(name = "document_2_type") val document2Type: String?,
    @ColumnInfo(name = "document_2_status") val document2Status: DocumentStatus,
    @ColumnInfo(name = "document_3_url") val document3Url: String?,
    @ColumnInfo(name = "document_3_type") val document3Type: String?,
    @ColumnInfo(name = "document_3_status") val document3Status: DocumentStatus,
    @ColumnInfo(name = "document_4_url") val document4Url: String?,
    @ColumnInfo(name = "document_4_type") val document4Type: String?,
    @ColumnInfo(name = "document_4_status") val document4Status: DocumentStatus,
    @ColumnInfo(name = "document_5_url") val document5Url: String?,
    @ColumnInfo(name = "document_5_type") val document5Type: String?,
    @ColumnInfo(name = "document_5_status") val document5Status: DocumentStatus,

    @ColumnInfo(name = "contract_url") val contractUrl: String?,
    @ColumnInfo(name = "contract_status") val contractStatus: DocumentStatus,

    // Banking Details
    @ColumnInfo(name = "bank_name") val bankName: String? = null,
    @ColumnInfo(name = "account_no") val accountNo: String? = null,
    @ColumnInfo(name = "branch_code") val branchCode: String? = null,

    // Burial Society specific
    @ColumnInfo(name = "beneficiary_count") val beneficiaryCount: Int?,
    @ColumnInfo(name = "beneficiary_over_65_count") val beneficiaryOver65Count: Int?,
    @ColumnInfo(name = "monthly_contribution_override") val monthlyContributionOverride: Money?,

    @ColumnInfo(name = "total_contributions") val totalContributions: Int?,
    @ColumnInfo(name = "total_paid", defaultValue = "0.0") val totalPaid: Money = Money.ZERO,
    @ColumnInfo(name = "fcm_token") val fcmToken: String?,
    @ColumnInfo(name = "notification_pref") val notificationPref: NotificationPref,
    @ColumnInfo(name = "rosca_position") val roscaPosition: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE group_id = :groupId ORDER BY full_name ASC")
    suspend fun getMembersSync(groupId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE group_id = :groupId ORDER BY full_name ASC")
    fun observeMembers(groupId: String): Flow<List<MemberEntity>>

    @Query(
        """
        SELECT * FROM members
        WHERE group_id = :groupId
          AND (LOWER(status) = 'active' OR LOWER(status) = 'probation')
        ORDER BY full_name ASC
        """,
    )
    fun observeActiveMembers(groupId: String): Flow<List<MemberEntity>>

    @Query(
        """
        SELECT * FROM members
        WHERE group_id = :groupId
          AND (LOWER(status) = 'active' OR LOWER(status) = 'probation')
        ORDER BY full_name ASC
        """,
    )
    suspend fun getActiveMembersSync(groupId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE id = :id LIMIT 1")
    suspend fun getMemberById(id: String): MemberEntity?

    @Query("SELECT * FROM members WHERE id = :id")
    fun observeMemberById(id: String): Flow<MemberEntity?>

    @Query("SELECT * FROM members WHERE user_id = :userId AND group_id = :groupId LIMIT 1")
    suspend fun getMemberByUserId(userId: String, groupId: String): MemberEntity?

    @Query("SELECT * FROM members WHERE user_id = :userId AND group_id = :groupId")
    fun observeMemberByUserId(userId: String, groupId: String): Flow<MemberEntity?>

    @Query("SELECT * FROM members WHERE user_id = :userId")
    suspend fun getAllMemberships(userId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE user_id = :userId")
    fun observeAllMemberships(userId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE member_key = :memberKey LIMIT 1")
    suspend fun getMemberByMemberKey(memberKey: String): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<MemberEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: MemberEntity): Long

    @Query("DELETE FROM members WHERE group_id = :groupId")
    suspend fun deleteMembersByGroupId(groupId: String)

    @Query("DELETE FROM members")
    suspend fun clearAll()

    @Query("SELECT id FROM members WHERE user_id = :userId")
    suspend fun getMemberIdsForUser(userId: String): List<String>

    @androidx.room.Transaction
    suspend fun syncMembershipsForUser(userId: String, members: List<MemberEntity>) {
        val current = getMemberIdsForUser(userId).toSet()
        val incoming = members.asSequence().map { it.id }.toSet()
        (current - incoming).forEach { deleteMember(it) }
        if (members.isNotEmpty()) {
            upsertMembers(members)
        }
    }

    @androidx.room.Transaction
    suspend fun syncMembers(groupId: String, members: List<MemberEntity>) {
        val current = getMembersSync(groupId).asSequence().map { it.id }.toSet()
        val incoming = members.asSequence().map { it.id }.toSet()
        val toDelete = current - incoming
        toDelete.forEach { memberId -> deleteMember(memberId) }
        if (members.isNotEmpty()) {
            upsertMembers(members)
        }
    }

    @Query("DELETE FROM members WHERE id = :id")
    suspend fun deleteMember(id: String)

    @Query("SELECT * FROM members WHERE id IN (:ids)")
    suspend fun getMembersByIds(ids: List<String>): List<MemberEntity>
}
