package com.sanibonani.save.data.repository.payment

import com.sanibonani.save.data.remote.gateway.SimulationGateway
import com.sanibonani.save.domain.gateway.BankBackendSimulationModule
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.repository.payment.PaymentInitiationResult
import com.sanibonani.save.domain.repository.payment.PaymentStatusResult
import io.ktor.client.HttpClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PaymentGatewayRepositoryImplTest {

    private val httpClient = mockk<HttpClient>(relaxed = true)
    private val bankModule = mockk<BankBackendSimulationModule>(relaxed = true)
    private val simulation = mockk<SimulationGateway>()
    private val edgeAdapter = mockk<SupabaseEdgeBankAdapter>()

    private lateinit var repository: PaymentGatewayRepositoryImpl

    @Before
    fun setUp() {
        every { simulation.bank } returns bankModule
        repository = PaymentGatewayRepositoryImpl(
            mapOf(
                PaymentMethod.SIMULATED_BANK to SimulatedBankProvider(simulation),
                PaymentMethod.STITCH to StitchPaymentProvider(httpClient),
                PaymentMethod.PAYFAST to PayFastPaymentProvider(httpClient),
                PaymentMethod.BANK to edgeAdapter,
                PaymentMethod.YOCO to edgeAdapter
            )
        )
    }

    @Test
    fun `simulated bank payments route through the simulation gateway`() = runBlocking {
        coEvery { bankModule.simulateSinglePayment(any(), any(), any()) } returns Result.success("sim-ref")

        val initiation = repository.initiatePayment(
            PaymentMethod.SIMULATED_BANK,
            PaymentType.CONTRIBUTION,
            Money("100.0"),
            "group-1",
            "member-1",
            null
        ).getOrThrow()

        assertTrue(initiation.transactionId.startsWith("sim_"))
        assertEquals(PaymentMethod.SIMULATED_BANK, initiation.method)
        assertEquals(
            "sanibonani://vbank/pay?tx=${initiation.transactionId}&method=SIMULATED_BANK&amount=100.00",
            initiation.checkoutUrl
        )
        coVerify(exactly = 1) {
            bankModule.simulateSinglePayment("group-1", Money("100.0"), initiation.transactionId)
        }
    }

    @Test
    fun `a simulation failure is reported to the caller`() = runBlocking {
        coEvery { bankModule.simulateSinglePayment(any(), any(), any()) } returns Result.failure(IOException("sim down"))

        val outcome = repository.initiatePayment(
            PaymentMethod.SIMULATED_BANK,
            PaymentType.CONTRIBUTION,
            Money("100.0"),
            "group-1",
            null,
            null
        )

        assertTrue(outcome.isFailure)
        assertEquals("sim down", outcome.exceptionOrNull()?.message)
    }

    @Test
    fun `bank and yoco payments route through the edge adapter`() = runBlocking {
        coEvery { edgeAdapter.initiate(any(), any(), any(), any(), any(), any()) } answers {
            val method = firstArg<PaymentMethod>()
            Result.success(PaymentInitiationResult("https://edge.example/$method", "edge-ref", method))
        }

        val bank = repository.initiatePayment(PaymentMethod.BANK, PaymentType.CONTRIBUTION, Money("50.0"), "group-1", null, null).getOrThrow()
        val yoco = repository.initiatePayment(PaymentMethod.YOCO, PaymentType.CONTRIBUTION, Money("50.0"), "group-1", null, null).getOrThrow()

        assertEquals("edge-ref", bank.transactionId)
        assertEquals(PaymentMethod.BANK, bank.method)
        assertEquals(PaymentMethod.YOCO, yoco.method)
        coVerify(exactly = 1) { edgeAdapter.initiate(PaymentMethod.BANK, any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { edgeAdapter.initiate(PaymentMethod.YOCO, any(), any(), any(), any(), any()) }
    }

    @Test
    fun `methods without a provider fall back to a manual payment link`() = runBlocking {
        val initiation = repository.initiatePayment(
            PaymentMethod.CASH,
            PaymentType.CONTRIBUTION,
            Money("250.0"),
            "group-1",
            null,
            null
        ).getOrThrow()

        assertTrue(initiation.transactionId.startsWith("ref_"))
        assertEquals(PaymentMethod.CASH, initiation.method)
        assertEquals(
            "sanibonani://payment/manual?tx=${initiation.transactionId}&method=CASH&amount=250.00",
            initiation.checkoutUrl
        )
        coVerify(exactly = 0) { bankModule.simulateSinglePayment(any(), any(), any()) }
    }

    @Test
    fun `verification of a method without a provider fails closed`() = runBlocking {
        val outcome = repository.verifyPayment("tx-1", PaymentMethod.CASH)

        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull()?.message?.contains("No payment provider is configured for CASH") == true)
    }

    @Test
    fun `payouts settle through stitch`() = runBlocking {
        val reference = repository.disburseFunds(
            payoutId = "payout-1",
            amount = Money("300.0"),
            bankName = "Example Bank",
            accountNo = "123456789",
            branchCode = "470010",
            reference = "PAYOUT-1"
        ).getOrThrow()

        assertTrue(reference.startsWith("STITCH-DISB-"))
    }

    @Test
    fun `a provider supplied for a method handles initiation, verification and payout`() = runBlocking {
        val fake = object : PaymentGatewayProvider {
            override suspend fun initiate(
                method: PaymentMethod,
                type: PaymentType,
                amount: Money,
                groupId: String,
                memberId: String?,
                description: String?
            ): Result<PaymentInitiationResult> =
                Result.success(PaymentInitiationResult("https://fake.example/$method", "fake-ref", method))

            override suspend fun verify(transactionId: String, method: PaymentMethod) =
                Result.success(PaymentStatusResult(transactionId, true, Money("10.0")))

            override suspend fun disburse(
                payoutId: String,
                amount: Money,
                bankName: String,
                accountNo: String,
                branchCode: String,
                reference: String
            ): Result<String> = Result.success("FAKE-$payoutId")
        }
        val repository = PaymentGatewayRepositoryImpl(mapOf(PaymentMethod.STITCH to fake))

        val initiation = repository.initiatePayment(
            PaymentMethod.STITCH, PaymentType.CONTRIBUTION, Money("10.0"), "group-1", null, null
        ).getOrThrow()
        assertEquals("fake-ref", initiation.transactionId)

        assertTrue(repository.verifyPayment("tx-1", PaymentMethod.STITCH).getOrThrow().isSuccessful)

        val reference = repository.disburseFunds(
            "payout-1", Money("10.0"), "Bank", "123", "456", "REF"
        ).getOrThrow()
        assertEquals("FAKE-payout-1", reference)
    }
}
