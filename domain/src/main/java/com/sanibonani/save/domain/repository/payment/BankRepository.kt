package com.sanibonani.save.domain.repository.payment
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Interface for bank-specific operations, including statement imports and reconciliation.
 */
interface BankRepository {
    /**
     * Imports a list of raw bank transactions into the system.
     */
    suspend fun importBankTransactions(transactions: List<BankTransaction>): Result<Unit>

    /**
     * Observes unreconciled bank transactions for a specific group.
     */
    fun observeUnreconciledTransactions(groupId: String): Flow<Result<List<BankTransaction>>>

    /**
     * Imports a raw bank statement string, parsing it using the specified format.
     */
    suspend fun importRawStatement(rawData: String, format: String, groupId: String): Result<Unit>

    /**
     * Manually reconciles a bank transaction with a ledger entry.
     */
    suspend fun reconcileManual(bankTxId: String, ledgerId: String): Result<Unit>

    /**
     * Ignores a bank transaction (e.g., internal transfers, non-group related entries).
     */
    suspend fun ignoreTransaction(bankTxId: String, reason: String): Result<Unit>

    /**
     * Observes the active bank connection for a group.
     */
    fun observeBankConnection(groupId: String): Flow<Result<BankConnection?>>

    /**
     * Saves or updates a bank connection.
     */
    suspend fun saveBankConnection(connection: BankConnection): Result<Unit>

    /**
     * Removes a bank connection.
     */
    suspend fun deleteBankConnection(groupId: String): Result<Unit>
}
