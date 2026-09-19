package com.sanibonani.save.domain.usecase.financial

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.financial.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Orchestrates the synchronization of bank data (balance and transactions)
 * from a financial provider to the local system.
 */
class SyncBankTransactionsUseCase @Inject constructor(
    private val financialProviderRepo: FinancialProviderRepository,
    private val bankRepo: BankRepository
) {
    suspend operator fun invoke(groupId: String, providerId: String): Result<Unit> = runCatching {
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

            Unit
        }
    }
}
