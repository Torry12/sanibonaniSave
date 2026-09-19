package com.sanibonani.save.data.local

import androidx.room.*
import com.sanibonani.save.domain.model.*

@Entity(
    tableName = "group_ledger",
    indices = [Index("group_id")]
)
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    val amount: Money,
    @ColumnInfo(name = "balance_after") val balanceAfter: Money,
    val description: String,
    val category: String,
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
    @ColumnInfo(name = "settled_at") val settledAt: String?,
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
    @Query("SELECT * FROM group_ledger WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observeLedger(groupId: String): kotlinx.coroutines.flow.Flow<List<LedgerEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLedger(entries: List<LedgerEntryEntity>)

    @Query("DELETE FROM group_ledger WHERE group_id = :groupId")
    suspend fun clearLedger(groupId: String)

    @Transaction
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
}

// ── Mappers ──────────────────────────────────────────────────────────────────

fun LedgerEntryEntity.toModel() = LedgerEntry(
    id = id,
    groupId = groupId,
    transactionId = transactionId,
    amount = amount,
    balanceAfter = balanceAfter,
    description = description,
    category = category,
    createdAt = createdAt
)

fun LedgerEntry.toLedgerEntity() = LedgerEntryEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    transactionId = transactionId,
    amount = amount,
    balanceAfter = balanceAfter,
    description = description,
    category = category,
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
    periodStart = periodStart,
    periodEnd = periodEnd,
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
    periodStart = periodStart,
    periodEnd = periodEnd,
    status = status,
    ledgerBalance = ledgerBalance,
    actualBalance = actualBalance,
    discrepancy = discrepancy,
    notes = notes,
    performedBy = performedBy,
    createdAt = createdAt
)
