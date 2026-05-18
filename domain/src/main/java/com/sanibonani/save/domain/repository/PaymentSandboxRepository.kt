package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.PaymentType

/**
 * Interface for sandbox/testing operations for payment gateways.
 * Allows simulating various payment states and configurations.
 */
interface PaymentSandboxRepository {
    /**
     * Generates a test payment URL for the given provider.
     */
    suspend fun generateSandboxUrl(
        method: PaymentMethod,
        type: PaymentType,
        amount: Double,
        groupId: String,
        memberId: String? = null
    ): Result<String>

    /**
     * Checks the status of a sandbox transaction.
     */
    suspend fun verifySandboxPayment(transactionId: String, method: PaymentMethod): Result<Boolean>
}
