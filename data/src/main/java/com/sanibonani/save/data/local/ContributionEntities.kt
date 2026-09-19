package com.sanibonani.save.data.local

import androidx.room.*
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "contributions",
    indices = [
        Index("member_id"),
        Index("group_id"),
        Index("status"),
        Index("due_date"),
    ],
)
data class ContributionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "policy_id") val policyId: String?,
    val amount: Money,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "due_date") val dueDate: String,
    @ColumnInfo(name = "paid_at") val paidAt: String?,
    val status: ContributionStatus,
    @ColumnInfo(name = "type", defaultValue = "contribution") val type: String = "contribution",
    @ColumnInfo(name = "payment_method", defaultValue = "bank") val paymentMethod: String = "bank",
    @ColumnInfo(name = "late_fees_applied", defaultValue = "0") val lateFeesApplied: Boolean,
    @ColumnInfo(name = "late_fee_amount") val lateFeeAmount: Money = Money.ZERO,
    @ColumnInfo(name = "principal_amount") val principalAmount: Money = Money.ZERO,
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface ContributionDao {
    @Query("SELECT * FROM contributions WHERE member_id = :memberId ORDER BY due_date DESC")
    fun observeContributions(memberId: String): Flow<List<ContributionEntity>>

    @Query("SELECT * FROM contributions WHERE group_id = :groupId ORDER BY due_date DESC")
    fun observeGroupContributions(groupId: String): Flow<List<ContributionEntity>>

    @Query("SELECT * FROM contributions WHERE group_id = :groupId")
    suspend fun getGroupContributions(groupId: String): List<ContributionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContributions(contributions: List<ContributionEntity>): List<Long>

    @Query("DELETE FROM contributions WHERE group_id = :groupId")
    suspend fun deleteContributionsByGroupId(groupId: String)

    @Query("DELETE FROM contributions WHERE member_id = :memberId")
    suspend fun deleteContributionsByMemberId(memberId: String)

    @Query("SELECT * FROM contributions WHERE member_id = :memberId")
    suspend fun getMemberContributions(memberId: String): List<ContributionEntity>

    @Query("DELETE FROM contributions")
    suspend fun clearAll()

    @androidx.room.Transaction
    suspend fun syncGroupContributions(groupId: String, contributions: List<ContributionEntity>) {
        val current = getGroupContributions(groupId).map { it.id }.toSet()
        val incoming = contributions.map { it.id }.toSet()
        (current - incoming).forEach { deleteContribution(it) }
        upsertContributions(contributions)
    }

    @androidx.room.Transaction
    suspend fun syncMemberContributions(memberId: String, contributions: List<ContributionEntity>) {
        val current = getMemberContributions(memberId).map { it.id }.toSet()
        val incoming = contributions.map { it.id }.toSet()
        (current - incoming).forEach { deleteContribution(it) }
        upsertContributions(contributions)
    }

    @Query("DELETE FROM contributions WHERE id = :id")
    suspend fun deleteContribution(id: String)
}
