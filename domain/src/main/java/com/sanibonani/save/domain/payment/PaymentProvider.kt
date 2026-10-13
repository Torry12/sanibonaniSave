package com.sanibonani.save.domain.payment

/**
 * Interface for payment processing providers (e.g., Stitch, Paystack, or Mock).
 */
interface PaymentProvider {
    /**
     * Collects payment from a member (inflow).
     * @return Result with transaction reference/ID.
     */
    suspend fun collect(amount: Long, currency: String): Result<String>

    /**
     * Disburses payment to a member (outflow).
     * @return Result with transaction reference/ID.
     */
    suspend fun disburse(amount: Long, currency: String): Result<String>

    /**
     * Refunds a previous transaction.
     */
    suspend fun refund(transactionId: String): Result<Boolean>

    /**
     * Verifies the status of a transaction.
     */
    suspend fun verify(transactionId: String): Result<Boolean>
}
