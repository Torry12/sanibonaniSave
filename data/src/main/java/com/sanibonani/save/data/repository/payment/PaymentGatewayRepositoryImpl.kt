package com.sanibonani.save.data.repository.payment
import com.sanibonani.save.data.remote.gateway.SimulationGateway
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.repository.payment.PaymentGatewayRepository
import com.sanibonani.save.domain.repository.payment.PaymentInitiationResult
import com.sanibonani.save.domain.repository.payment.PaymentStatusResult
import io.ktor.client.HttpClient
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class PaymentGatewayRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val simulation: SimulationGateway,
    private val supabaseEdgeBankAdapter: SupabaseEdgeBankAdapter
) : PaymentGatewayRepository {

    private val providers: Map<PaymentMethod, PaymentGatewayProvider> by lazy {
        mapOf(
            PaymentMethod.SIMULATED_BANK to SimulatedBankProvider(simulation),
            PaymentMethod.STITCH to StitchPaymentProvider(httpClient),
            PaymentMethod.PAYFAST to PayFastPaymentProvider(httpClient),
            PaymentMethod.BANK to supabaseEdgeBankAdapter,
            PaymentMethod.YOCO to supabaseEdgeBankAdapter
        )
    }

    override suspend fun initiatePayment(
        method: PaymentMethod,
        type: PaymentType,
        amount: Money,
        groupId: String,
        memberId: String?,
        description: String?
    ): Result<PaymentInitiationResult> {
        val provider = providers[method] ?: return fallbackInitiation(method, amount)
        return provider.initiate(method, type, amount, groupId, memberId, description)
    }

    private fun fallbackInitiation(method: PaymentMethod, amount: Money): Result<PaymentInitiationResult> = safeRunCatching {
        // AI Note: In production, every PaymentMethod should have a dedicated provider.
        // Fallback is primarily for development/testing when a real gateway is not configured.
        val txId = "ref_${UUID.randomUUID().toString().take(8)}"
        val checkoutUrl = "sanibonani://payment/manual?tx=$txId&method=${method.name}&amount=${amount.toPlainString()}"
        PaymentInitiationResult(checkoutUrl, txId, method)
    }

    override suspend fun verifyPayment(transactionId: String, method: PaymentMethod): Result<PaymentStatusResult> = safeRunCatching {
        val provider = providers[method]
            ?: error("No payment provider is configured for ${method.name}; cannot report it as verified.")
        provider.verify(transactionId, method).getOrThrow()
    }

    override suspend fun disburseFunds(
        payoutId: String,
        amount: Money,
        bankName: String,
        accountNo: String,
        branchCode: String,
        reference: String
    ): Result<String> = safeRunCatching {
        // Stitch is the payout rail; PayFast remains a collection-only provider.
        providers.getValue(PaymentMethod.STITCH)
            .disburse(payoutId, amount, bankName, accountNo, branchCode, reference)
            .getOrThrow()
    }
}
