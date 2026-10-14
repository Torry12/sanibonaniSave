package com.sanibonani.save.data.repository.payment
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.repository.payment.PaymentInitiationResult
import com.sanibonani.save.domain.repository.payment.PaymentStatusResult
import java.util.UUID
import kotlinx.coroutines.delay
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Strategy interface for external payment gateway providers (Stitch, PayFast, YoCo).
 */
interface PaymentGatewayProvider {
    suspend fun initiate(
        method: PaymentMethod,
        type: PaymentType,
        amount: Money,
        groupId: String,
        memberId: String?,
        description: String?
    ): Result<PaymentInitiationResult>

    suspend fun verify(transactionId: String, method: PaymentMethod): Result<PaymentStatusResult>

    suspend fun disburse(
        payoutId: String,
        amount: Money,
        bankName: String,
        accountNo: String,
        branchCode: String,
        reference: String
    ): Result<String>
}

class SimulatedBankProvider(
    private val simulation: com.sanibonani.save.data.remote.gateway.SimulationGateway
) : PaymentGatewayProvider {
    override suspend fun initiate(
        method: PaymentMethod,
        type: PaymentType,
        amount: Money,
        groupId: String,
        memberId: String?,
        description: String?
    ): Result<PaymentInitiationResult> = safeRunCatching {
        val txId = "sim_${UUID.randomUUID().toString().take(8)}"
        simulation.bank.simulateSinglePayment(groupId, amount, txId).getOrThrow()
        PaymentInitiationResult(
            checkoutUrl = "sanibonani://vbank/pay?tx=$txId&method=${method.name}&amount=${amount.toPlainString()}",
            transactionId = txId,
            method = method
        )
    }

    override suspend fun verify(transactionId: String, method: PaymentMethod): Result<PaymentStatusResult> = safeRunCatching {
        val simTx = simulation.bank.getTransactionStatus(transactionId).getOrThrow()
        if (simTx != null) {
            PaymentStatusResult(transactionId, true, simTx.amount)
        } else {
            PaymentStatusResult(transactionId, false, Money.ZERO, "Transaction not found in simulation")
        }
    }

    override suspend fun disburse(
        payoutId: String,
        amount: Money,
        bankName: String,
        accountNo: String,
        branchCode: String,
        reference: String
    ): Result<String> = safeRunCatching {
        // Simulate external API call for banking disbursement
        delay(800)
        "EXT-SIM-${UUID.randomUUID().toString().take(6).uppercase()}"
    }
}
