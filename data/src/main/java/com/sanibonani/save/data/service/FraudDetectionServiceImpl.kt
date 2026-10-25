package com.sanibonani.save.data.service

import com.sanibonani.save.domain.model.Transaction
import com.sanibonani.save.domain.service.FraudDetectionService
import javax.inject.Inject

class FraudDetectionServiceImpl @Inject constructor() : FraudDetectionService {

    override suspend fun evaluateTransaction(transaction: Transaction): Result<Unit> {
        // Implement simple fraud logic, e.g., max transaction limits
        val maxTransactionSizeCents = 5000000L // R50,000.00
        if (transaction.amount > maxTransactionSizeCents) {
            return Result.failure(Exception("Transaction flagged for potential fraud (exceeds single transaction limit)."))
        }
        return Result.success(Unit)
    }
}
