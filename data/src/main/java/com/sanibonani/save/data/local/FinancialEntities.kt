package com.sanibonani.save.data.local

import androidx.room.*
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "bank_transactions",
    indices = [Index("group_id")]
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

@Entity(tableName = "financial_providers")
data class FinancialProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: FinancialProviderType,
    @ColumnInfo(name = "logo_url") val logoUrl: String?,
    @ColumnInfo(name = "supported_ops") val supportedOps: List<FinancialOperationType>,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    val metadata: Map<String, String>,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
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
