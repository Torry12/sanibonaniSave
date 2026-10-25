package com.sanibonani.save.domain.usecase.payment
import com.sanibonani.save.domain.model.BankConnection
import com.sanibonani.save.domain.model.BankConnectionStatus
import com.sanibonani.save.domain.model.FinancialOperationRequest
import com.sanibonani.save.domain.model.FinancialOperationResult
import com.sanibonani.save.domain.model.FinancialOperationType
import com.sanibonani.save.domain.repository.payment.BankRepository
import com.sanibonani.save.domain.repository.payment.FinancialProviderRepository
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Orchestrates the synchronization of bank data (balance and transactions)
 * from a financial provider to the local system.
 */
class SyncBankTransactionsUseCase @Inject constructor(
    private val financialProviderRepo: FinancialProviderRepository,
    private val bankRepo: BankRepository
) {
    suspend operator fun invoke(groupId: String): Result<Unit> = safeRunCatching {
        val connection = activeConnection(groupId)

        coroutineScope {
            // Run balance and transaction sync in parallel
            val balanceDeferred = async { fetch(FinancialOperationType.GET_BALANCE, connection.providerId, groupId) }
            val txDeferred = async { fetch(FinancialOperationType.GET_TRANSACTIONS, connection.providerId, groupId) }

            val balanceRes = balanceDeferred.await().getOrThrow()
            val txRes = txDeferred.await().getOrThrow()

            // The provider balance is a liveness probe only: the group balance stays ledger-derived.
            requireSuccess(balanceRes, "Balance Sync Failed")
            importTransactions(txRes, groupId)
        }
    }

    private suspend fun activeConnection(groupId: String): BankConnection {
        val connection = bankRepo.observeBankConnection(groupId).first().getOrNull()
            ?: throw Exception("No active bank connection found for group.")

        if (connection.status != BankConnectionStatus.CONNECTED) {
            throw Exception("Bank account is not currently connected (Status: ${connection.status})")
        }
        return connection
    }

    private suspend fun fetch(
        operation: FinancialOperationType,
        providerId: String,
        groupId: String
    ): Result<FinancialOperationResult> = financialProviderRepo.executeOperation(
        FinancialOperationRequest(
            operation = operation,
            providerId = providerId,
            groupId = groupId
        )
    )

    private fun requireSuccess(result: FinancialOperationResult, label: String) {
        if (!result.success) {
            throw Exception("$label: ${result.errorCode ?: "Unknown Error"}")
        }
    }

    private suspend fun importTransactions(result: FinancialOperationResult, groupId: String) {
        requireSuccess(result, "Transaction Sync Failed")

        val rawData = result.data["raw_statement"] ?: result.data["transactions"]
        val format = result.data["format"] ?: "json"

        if (rawData != null) {
            bankRepo.importRawStatement(rawData, format, groupId).getOrThrow()
        }
    }
}
