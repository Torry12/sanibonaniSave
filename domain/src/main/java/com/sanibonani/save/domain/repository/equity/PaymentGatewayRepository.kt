package com.sanibonani.save.domain.repository.equity

import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.PaymentType

/**
 * Interface for production payment gateways (Stitch, PayFast, YoCo).
 */
interface PaymentGatewayRepository {
    /**
     * Initiates a payment and returns the gateway's checkout/authorization URL
     * and a unique reference/transaction ID.
     */
    suspend fun initiatePayment(
        method: PaymentMethod,
        type: PaymentType,
        amount: Money,
        groupId: String,
        memberId: String? = null,
        description: String? = null
    ): Result<PaymentInitiationResult>

    /**
     * Verifies the status of a payment with the gateway.
     */
    suspend fun verifyPayment(transactionId: String, method: PaymentMethod): Result<PaymentStatusResult>
}

data class PaymentInitiationResult(
    val checkoutUrl: String,
    val transactionId: String,
    val method: PaymentMethod
)

data class PaymentStatusResult(
    val transactionId: String,
    val isSuccessful: Boolean,
    val amount: Money,
    val rawStatus: String? = null
)
