package com.sanibonani.save.domain.usecase.payment
import com.sanibonani.save.domain.model.BankConnectionStatus
import com.sanibonani.save.domain.model.FinancialOperationRequest
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
        val connectionRes = bankRepo.observeBankConnection(groupId).first()
        val connection = connectionRes.getOrNull() ?: throw Exception("No active bank connection found for group.")

        if (connection.status != BankConnectionStatus.CONNECTED) {
            throw Exception("Bank account is not currently connected (Status: ${connection.status})")
        }

        val providerId = connection.providerId

        coroutineScope {
            // Run balance and transaction sync in parallel
            val balanceDeferred = async {
                financialProviderRepo.executeOperation(
                    FinancialOperationRequest(
                        operation = FinancialOperationType.GET_BALANCE,
                        providerId = providerId,
                        groupId = groupId
                    )
                )
            }

            val txDeferred = async {
                financialProviderRepo.executeOperation(
                    FinancialOperationRequest(
                        operation = FinancialOperationType.GET_TRANSACTIONS,
                        providerId = providerId,
                        groupId = groupId
                    )
                )
            }

            val balanceRes = balanceDeferred.await().getOrThrow()
            val txRes = txDeferred.await().getOrThrow()

            if (!balanceRes.success) {
                throw Exception("Balance Sync Failed: ${balanceRes.errorCode ?: "Unknown Error"}")
            }

            if (txRes.success) {
                val rawData = txRes.data["raw_statement"] ?: txRes.data["transactions"]
                val format = txRes.data["format"] ?: "json"

                if (rawData != null) {
                    bankRepo.importRawStatement(rawData, format, groupId).getOrThrow()
                }
            } else {
                throw Exception("Transaction Sync Failed: ${txRes.errorCode ?: "Unknown Error"}")
            }
        }
    }
}
