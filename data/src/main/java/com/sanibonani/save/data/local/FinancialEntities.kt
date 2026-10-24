package com.sanibonani.save.data.local

import androidx.room.*
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "bank_transactions",
    indices = [
        Index("group_id"),
        Index("bank_reference", unique = true),
        Index("status")
    ]
)
data class BankTransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "bank_reference") val bankReference: String,
    val amount: Money,
    @ColumnInfo(name = "transaction_date") val transactionDate: String,
    val description: String?,
    val status: BankTransactionStatus,
    @ColumnInfo(name = "reconciled_with") val reconciledWith: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?
)

@Entity(
    tableName = "bank_connections",
    indices = [
        Index("group_id", unique = true),
        Index("provider_id")
    ]
)
data class BankConnectionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "provider_id") val providerId: String,
    val status: BankConnectionStatus,
    @ColumnInfo(name = "account_name") val accountName: String?,
    @ColumnInfo(name = "account_number_hidden") val accountNumberHidden: String?,
    @ColumnInfo(name = "linked_at") val linkedAt: String,
    @ColumnInfo(name = "expires_at") val expiresAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null
)

@Entity(tableName = "financial_providers")
data class FinancialProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: FinancialProviderType,
    @ColumnInfo(name = "logo_url") val logoUrl: String?,
    @ColumnInfo(name = "supported_ops") val supportedOps: List<FinancialOperationType>,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    val metadata: Map<String, String>,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null
)

@Entity(
    tableName = "ledger_checkpoints",
    indices = [Index("group_id")]
)
data class LedgerCheckpointEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "ledger_entry_id") val ledgerEntryId: String,
    val balance: Money,
    @ColumnInfo(name = "created_at") val createdAt: String?
)

@Dao
interface BankTransactionDao {
    @Query("SELECT * FROM bank_transactions WHERE group_id = :groupId ORDER BY transaction_date DESC")
    fun observeByGroup(groupId: String): Flow<List<BankTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transactions: List<BankTransactionEntity>)

    @Query("UPDATE bank_transactions SET status = :status, reconciled_with = :ledgerId WHERE id = :id")
    suspend fun updateReconciliationStatus(id: String, status: BankTransactionStatus, ledgerId: String?)

    @Query("SELECT * FROM bank_transactions WHERE id = :id")
    suspend fun getById(id: String): BankTransactionEntity?

    @Query("DELETE FROM bank_transactions")
    suspend fun clearAll()

    // ── Connections ──────────────────────────────────────────────────────────
    @Query("SELECT * FROM bank_connections WHERE group_id = :groupId")
    fun observeConnection(groupId: String): Flow<BankConnectionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConnection(connection: BankConnectionEntity)

    @Query("DELETE FROM bank_connections WHERE group_id = :groupId")
    suspend fun deleteConnectionByGroup(groupId: String)
}

@Dao
interface FinancialProviderDao {
    @Query("SELECT * FROM financial_providers")
    fun observeAll(): Flow<List<FinancialProviderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(provider: FinancialProviderEntity)

    @Query("DELETE FROM financial_providers")
    suspend fun clearAll()
}

@Dao
interface LedgerCheckpointDao {
    @Query("SELECT * FROM ledger_checkpoints WHERE group_id = :groupId")
    fun observeByGroup(groupId: String): Flow<List<LedgerCheckpointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkpoint: LedgerCheckpointEntity)

    @Query("DELETE FROM ledger_checkpoints")
    suspend fun clearAll()
}

// ── Mappers ──────────────────────────────────────────────────────────────────

fun BankTransactionEntity.toModel() = BankTransaction(
    id = id,
    groupId = groupId,
    bankReference = bankReference,
    amount = amount,
    transactionDate = transactionDate,
    description = description,
    status = status,
    reconciledWith = reconciledWith,
    createdAt = createdAt
)

fun BankTransaction.toEntity() = BankTransactionEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    bankReference = bankReference,
    amount = amount,
    transactionDate = transactionDate,
    description = description,
    status = status,
    reconciledWith = reconciledWith,
    createdAt = createdAt
)

fun BankConnectionEntity.toModel() = BankConnection(
    id = id,
    groupId = groupId,
    providerId = providerId,
    status = status,
    accountName = accountName,
    accountNumberHidden = accountNumberHidden,
    linkedAt = linkedAt,
    expiresAt = expiresAt
)

fun BankConnection.toEntity() = BankConnectionEntity(
    id = id,
    groupId = groupId,
    providerId = providerId,
    status = status,
    accountName = accountName,
    accountNumberHidden = accountNumberHidden,
    linkedAt = linkedAt,
    expiresAt = expiresAt
)

fun FinancialProviderEntity.toModel() = FinancialProvider(
    id = id,
    name = name,
    type = type,
    logoUrl = logoUrl,
    supportedOperations = supportedOps,
    isActive = isActive,
    metadata = metadata
)

fun FinancialProvider.toEntity() = FinancialProviderEntity(
    id = id,
    name = name,
    type = type,
    logoUrl = logoUrl,
    supportedOps = supportedOperations,
    isActive = isActive,
    metadata = metadata
)

