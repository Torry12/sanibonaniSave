package com.sanibonani.save.domain.repository.payment
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

    /**
     * Executes a physical payout to a recipient's bank account.
     * Returns the external banking reference ID on success.
     */
    suspend fun disburseFunds(
        payoutId: String,
        amount: Money,
        bankName: String,
        accountNo: String,
        branchCode: String,
        reference: String
    ): Result<String>
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
