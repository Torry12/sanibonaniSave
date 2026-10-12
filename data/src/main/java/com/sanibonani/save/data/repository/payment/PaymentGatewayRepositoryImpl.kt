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

    private fun fallbackInitiation(method: PaymentMethod, amount: Money): Result<PaymentInitiationResult> = runCatching {
        // AI Note: In production, every PaymentMethod should have a dedicated provider.
        // Fallback is primarily for development/testing when a real gateway is not configured.
        val txId = "ref_${UUID.randomUUID().toString().take(8)}"
        val checkoutUrl = "sanibonani://payment/manual?tx=$txId&method=${method.name}&amount=${amount.toPlainString()}"
        PaymentInitiationResult(checkoutUrl, txId, method)
    }

    override suspend fun verifyPayment(transactionId: String, method: PaymentMethod): Result<PaymentStatusResult> = runCatching {
        val provider = providers[method]
        if (provider != null) {
            provider.verify(transactionId, method).getOrThrow()
        } else {
            PaymentStatusResult(transactionId, true, Money.ZERO)
        }
    }

    override suspend fun disburseFunds(
        payoutId: String,
        amount: Money,
        bankName: String,
        accountNo: String,
        branchCode: String,
        reference: String
    ): Result<String> = runCatching {
        // By default, route to STITCH (or use group metadata to determine the payout gateway)
        val provider = providers[PaymentMethod.STITCH]
            ?: providers[PaymentMethod.PAYFAST]
            ?: providers.values.first()

        provider.disburse(payoutId, amount, bankName, accountNo, branchCode, reference).getOrThrow()
    }
}
