package com.sanibonani.save.data.repository.financial

import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.repository.financial.PaymentInitiationResult
import com.sanibonani.save.domain.repository.financial.PaymentStatusResult

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
    ): Result<PaymentInitiationResult> = runCatching {
        val txId = "sim_${java.util.UUID.randomUUID().toString().take(8)}"
        simulation.bank.simulateSinglePayment(groupId, amount, txId).getOrThrow()
        PaymentInitiationResult(
            checkoutUrl = "sanibonani://vbank/pay?tx=$txId&method=${method.name}&amount=${amount.toDouble()}",
            transactionId = txId,
            method = method
        )
    }

    override suspend fun verify(transactionId: String, method: PaymentMethod): Result<PaymentStatusResult> = runCatching {
        PaymentStatusResult(transactionId, true, Money.ZERO)
    }
}
