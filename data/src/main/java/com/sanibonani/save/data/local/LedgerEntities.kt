package com.sanibonani.save.data.local

import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import com.sanibonani.save.domain.model.*

@Entity(
    tableName = "group_ledger",
    indices = [Index("group_id")]
)
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "member_id") val memberId: String? = null,
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    val amount: Money,
    @ColumnInfo(name = "balance_after") val balanceAfter: Money,
    val description: String,
    val category: String,
    @ColumnInfo(name = "is_platform") val isPlatform: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String?
)

@Entity(
    tableName = "settlements",
    indices = [Index("transaction_id")]
)
data class SettlementRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "provider_reference") val providerReference: String?,
    val status: SettlementStatus,
    @ColumnInfo(name = "settled_at") val settledAt: Long?,
    @ColumnInfo(name = "fee_deducted") val feeDeducted: Money
)

@Entity(
    tableName = "reconciliations",
    indices = [Index("group_id")]
)
data class ReconciliationRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "period_start") val periodStart: String,
    @ColumnInfo(name = "period_end") val periodEnd: String,
    val status: ReconciliationStatus,
    @ColumnInfo(name = "ledger_balance") val ledgerBalance: Money,
    @ColumnInfo(name = "actual_balance") val actualBalance: Money,
    val discrepancy: Money,
    val notes: String?,
    @ColumnInfo(name = "performed_by") val performedBy: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?
)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM group_ledger WHERE group_id = :groupId AND is_platform = 0 ORDER BY created_at DESC")
    fun observeLedger(groupId: String): kotlinx.coroutines.flow.Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM group_ledger WHERE member_id = :memberId AND is_platform = 0 ORDER BY created_at DESC")
    fun observeMemberLedger(memberId: String): kotlinx.coroutines.flow.Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM group_ledger WHERE is_platform = 1 ORDER BY created_at DESC")
    fun observePlatformLedger(): kotlinx.coroutines.flow.Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM group_ledger WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observeConsolidatedLedger(groupId: String): kotlinx.coroutines.flow.Flow<List<LedgerEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLedger(entries: List<LedgerEntryEntity>)

    @Query("DELETE FROM group_ledger WHERE group_id = :groupId AND is_platform = 0")
    suspend fun clearLedger(groupId: String)

    @RoomTransaction
    suspend fun syncLedger(groupId: String, entries: List<LedgerEntryEntity>) {
        clearLedger(groupId)
        upsertLedger(entries)
    }

    @Query("DELETE FROM group_ledger")
    suspend fun clearAll()

    // ── Settlements ───────────────────────────────────────────────────────────
    @Query("SELECT * FROM settlements ORDER BY settled_at DESC")
    fun observeSettlements(): kotlinx.coroutines.flow.Flow<List<SettlementRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettlements(settlements: List<SettlementRecordEntity>)

    @Query("DELETE FROM settlements")
    suspend fun clearAllSettlements()

    // ── Reconciliations ───────────────────────────────────────────────────────
    @Query("SELECT * FROM reconciliations WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observeReconciliations(groupId: String): kotlinx.coroutines.flow.Flow<List<ReconciliationRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReconciliations(reconciliations: List<ReconciliationRecordEntity>)

    @Query("DELETE FROM reconciliations WHERE group_id = :groupId")
    suspend fun clearReconciliations(groupId: String)

    @Query("UPDATE reconciliations SET status = :status, notes = :notes WHERE id = :id")
    suspend fun updateReconciliationStatus(id: String, status: ReconciliationStatus, notes: String)

    @Query("SELECT * FROM settlements WHERE transaction_id = :transactionId LIMIT 1")
    suspend fun getSettlementByTransactionId(transactionId: String): SettlementRecordEntity?

    @Query("DELETE FROM group_ledger WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ── Mappers ──────────────────────────────────────────────────────────────────

fun LedgerEntryEntity.toModel() = LedgerEntry(
    id = id,
    groupId = groupId,
    memberId = memberId,
    transactionId = transactionId,
    amount = amount,
    balanceAfter = balanceAfter,
    description = description,
    category = category,
    isPlatform = isPlatform,
    createdAt = createdAt
)

fun LedgerEntry.toLedgerEntity() = LedgerEntryEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    memberId = memberId,
    transactionId = transactionId,
    amount = amount,
    balanceAfter = balanceAfter,
    description = description,
    category = category,
    isPlatform = isPlatform,
    createdAt = createdAt
)

fun SettlementRecordEntity.toModel() = SettlementRecord(
    id = id,
    transactionId = transactionId,
    providerReference = providerReference,
    status = status,
    settledAt = settledAt,
    feeDeducted = feeDeducted
)

fun SettlementRecord.toEntity() = SettlementRecordEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    transactionId = transactionId,
    providerReference = providerReference,
    status = status,
    settledAt = settledAt,
    feeDeducted = feeDeducted
)

fun ReconciliationRecordEntity.toModel() = ReconciliationRecord(
    id = id,
    groupId = groupId,
    periodStart = periodStart.toLongOrNull() ?: 0L,
    periodEnd = periodEnd.toLongOrNull() ?: 0L,
    status = status,
    ledgerBalance = ledgerBalance,
    actualBalance = actualBalance,
    discrepancy = discrepancy,
    notes = notes,
    performedBy = performedBy,
    createdAt = createdAt
)

fun ReconciliationRecord.toEntity() = ReconciliationRecordEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    periodStart = periodStart.toString(),
    periodEnd = periodEnd.toString(),
    status = status,
    ledgerBalance = ledgerBalance,
    actualBalance = actualBalance,
    discrepancy = discrepancy,
    notes = notes,
    performedBy = performedBy,
    createdAt = createdAt
)
