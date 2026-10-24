package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.Transaction

/**
 * Service for evaluating and flagging potential fraudulent activity.
 */
interface FraudDetectionService {
    /**
     * Evaluates a transaction for fraud (velocity spikes, duplicate checks).
     */
    suspend fun evaluateTransaction(transaction: Transaction): Result<Unit>
}
