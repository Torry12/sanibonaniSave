package com.sanibonani.save.data.local

import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

@Entity(
    tableName = "payouts",
    indices = [
        Index("group_id"),
        Index("status"),
    ],
)
data class PayoutEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "member_id") val memberId: String? = null,
    val amount: Money,
    @ColumnInfo(name = "bank_name") val bankName: String,
    @ColumnInfo(name = "account_no") val accountNo: String,
    @ColumnInfo(name = "branch_code") val branchCode: String,
    val status: PayoutStatus,
    @ColumnInfo(name = "payout_type") val type: PayoutType = PayoutType.STANDARD,
    val description: String? = null,
    @ColumnInfo(name = "processed_by") val processedBy: String?,
    @ColumnInfo(name = "processed_at") val processedAt: String?,
    @ColumnInfo(name = "payout_reference") val payoutReference: String?,
    @ColumnInfo(name = "supporting_document_url") val supportingDocumentUrl: String? = null,
    @ColumnInfo(name = "supporting_document_status") val supportingDocumentStatus: DocumentStatus = DocumentStatus.PENDING,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
    val metadata: JsonObject? = null
)

@Dao
interface PayoutDao {
    @Query("SELECT * FROM payouts WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observePayouts(groupId: String): Flow<List<PayoutEntity>>

    @Query("SELECT * FROM payouts WHERE group_id = :groupId")
    suspend fun getGroupPayouts(groupId: String): List<PayoutEntity>

    @Query("SELECT * FROM payouts WHERE member_id = :memberId ORDER BY created_at DESC")
    fun observePayoutsForMember(memberId: String): Flow<List<PayoutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayouts(payouts: List<PayoutEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayout(payout: PayoutEntity): Long

    @Query("DELETE FROM payouts WHERE group_id = :groupId")
    suspend fun deletePayoutsByGroupId(groupId: String)

    @Query("DELETE FROM payouts")
    suspend fun clearAll()

    @RoomTransaction
    suspend fun syncPayouts(groupId: String, payouts: List<PayoutEntity>) {
        val current = getGroupPayouts(groupId).map { it.id }.toSet()
        val incoming = payouts.map { it.id }.toSet()
        (current - incoming).forEach { deletePayout(it) }
        upsertPayouts(payouts)
    }

    @Query("DELETE FROM payouts WHERE id = :id")
    suspend fun deletePayout(id: String)

    @Query("SELECT * FROM payouts WHERE status = 'group_approved' ORDER BY created_at DESC")
    fun observePendingPayouts(): Flow<List<PayoutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllPayouts(payouts: List<PayoutEntity>)
}

// ── Mappers ──────────────────────────────────────────────────────────────────

fun PayoutEntity.toModel() = PayoutRequest(
    id = id,
    groupId = groupId,
    memberId = memberId,
    amount = amount,
    bankName = bankName,
    accountNo = accountNo,
    branchCode = branchCode,
    status = status,
    type = type,
    processedBy = processedBy,
    processedAt = processedAt,
    payoutReference = payoutReference,
    supportingDocumentUrl = supportingDocumentUrl,
    supportingDocumentStatus = supportingDocumentStatus,
    createdAt = createdAt,
    metadata = metadata
)

fun PayoutRequest.toEntity() = PayoutEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    memberId = memberId,
    amount = amount,
    bankName = bankName,
    accountNo = accountNo,
    branchCode = branchCode,
    status = status,
    type = type,
    processedBy = processedBy,
    processedAt = processedAt,
    payoutReference = payoutReference,
    supportingDocumentUrl = supportingDocumentUrl,
    supportingDocumentStatus = supportingDocumentStatus,
    createdAt = createdAt,
    metadata = metadata
)
