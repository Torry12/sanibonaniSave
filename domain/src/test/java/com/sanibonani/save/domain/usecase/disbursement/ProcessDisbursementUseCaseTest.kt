package com.sanibonani.save.domain.usecase.disbursement
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import com.sanibonani.save.domain.service.FinancialSecurityService
import com.sanibonani.save.domain.usecase.disbursement.ProcessDisbursementUseCase
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProcessDisbursementUseCaseTest {

    private lateinit var useCase: ProcessDisbursementUseCase
    private val payoutRepository: PayoutRepository = mockk()
    private val supabaseRepository: SupabaseRepository = mockk()
    private val financialRepository: GroupFinancialRepository = mockk()
    private lateinit var securityService: FinancialSecurityService

    @Before
    fun setUp() {
        securityService = FinancialSecurityService(supabaseRepository, financialRepository)
        useCase = ProcessDisbursementUseCase(
            payoutRepository,
            securityService
        )
        every { supabaseRepository.isMfaVerified } returns true
        coEvery { financialRepository.getVerifiedBalance(any()) } returns Result.success(Money("50000.0"))
        coEvery { payoutRepository.getPendingPayouts() } returns Result.success(emptyList())
    }

    @Test
    fun `invoke should complete payout atomically when status is COMPLETED`() = runBlocking {
        // Given
        val payoutId = "p1"
        val payout = PayoutRequest(
            id = payoutId,
            groupId = "g1",
            amount = Money("100.0"),
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = PayoutStatus.PROCESSING // Must be in PROCESSING to move to COMPLETED
        )

        coEvery { payoutRepository.getPayoutById(payoutId) } returns Result.success(payout)
        coEvery { payoutRepository.completePayoutAtomic(any(), any(), any()) } returns Result.success(Unit)

        // When
        val result = useCase(payoutId, PayoutStatus.COMPLETED, "admin1")

        // Then
        assertTrue(result.isSuccess)
        coVerify { payoutRepository.completePayoutAtomic(payoutId, "admin1", any()) }
    }

    @Test
    fun `invoke should fail if MFA required but not verified`() = runBlocking {
        // Given
        val payoutId = "p1"
        val payout = PayoutRequest(
            id = payoutId,
            groupId = "g1",
            amount = Money("15000.0"),
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = PayoutStatus.PROCESSING
        )

        every { supabaseRepository.isMfaVerified } returns false
        coEvery { payoutRepository.getPayoutById(payoutId) } returns Result.success(payout)

        // When
        val result = useCase(payoutId, PayoutStatus.COMPLETED, "admin1")

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("MFA_REQUIRED") == true)
    }

    @Test
    fun `the admin who processed a high-value payout cannot be the one who completes it`() = runBlocking {
        val payout = PayoutRequest(
            id = "p1",
            groupId = "g1",
            amount = Money("15000.0"),
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = PayoutStatus.PROCESSING,
            processedBy = "admin1"
        )
        // MFA is verified in setUp, so dual control is the only thing blocking this completion.
        coEvery { payoutRepository.getPayoutById("p1") } returns Result.success(payout)

        val result = useCase("p1", PayoutStatus.COMPLETED, "admin1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("DUAL_CONTROL_VIOLATION") == true)
        coVerify(exactly = 0) { payoutRepository.completePayoutAtomic(any(), any(), any()) }
    }

    @Test
    fun `a second admin can complete a payout another admin processed`() = runBlocking {
        val payout = PayoutRequest(
            id = "p1",
            groupId = "g1",
            amount = Money("15000.0"),
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = PayoutStatus.PROCESSING,
            processedBy = "admin1"
        )
        coEvery { payoutRepository.getPayoutById("p1") } returns Result.success(payout)
        coEvery { payoutRepository.completePayoutAtomic(any(), any(), any()) } returns Result.success(Unit)

        val result = useCase("p1", PayoutStatus.COMPLETED, "admin2")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { payoutRepository.completePayoutAtomic("p1", "admin2", any()) }
    }

    @Test
    fun `a low-value payout may still be completed by the admin who processed it`() = runBlocking {
        val payout = PayoutRequest(
            id = "p1",
            groupId = "g1",
            amount = Money("4000.0"),
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = PayoutStatus.PROCESSING,
            processedBy = "admin1"
        )
        coEvery { payoutRepository.getPayoutById("p1") } returns Result.success(payout)
        coEvery { payoutRepository.completePayoutAtomic(any(), any(), any()) } returns Result.success(Unit)

        val result = useCase("p1", PayoutStatus.COMPLETED, "admin1")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `a payout below the dual-control floor may be completed by the same admin`() = runBlocking {
        // The floor is read from the security service, the same single source of truth the AAL2
        // gate uses, so the app can never disagree with itself about what counts as high-value.
        val amount = FinancialSecurityService.DUAL_CONTROL_THRESHOLD - Money("0.01")
        val payout = PayoutRequest(
            id = "p1",
            groupId = "g1",
            amount = amount,
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = PayoutStatus.PROCESSING,
            processedBy = "admin1"
        )
        coEvery { payoutRepository.getPayoutById("p1") } returns Result.success(payout)
        coEvery { payoutRepository.completePayoutAtomic(any(), any(), any()) } returns Result.success(Unit)

        val result = useCase("p1", PayoutStatus.COMPLETED, "admin1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { payoutRepository.completePayoutAtomic("p1", "admin1", any()) }
    }

    @Test
    fun `a payout with no recorded processor is not treated as a self-completion`() = runBlocking {
        val payout = PayoutRequest(
            id = "p1",
            groupId = "g1",
            amount = Money("15000.0"),
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = PayoutStatus.PROCESSING,
            processedBy = null
        )
        coEvery { payoutRepository.getPayoutById("p1") } returns Result.success(payout)
        coEvery { payoutRepository.completePayoutAtomic(any(), any(), any()) } returns Result.success(Unit)

        val result = useCase("p1", PayoutStatus.COMPLETED, "admin1")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `a transition with no signed-in administrator is refused before anything is written`() = runBlocking {
        // currentUserId can be blank during a session race; stamping processed_by with it would
        // poison both the audit trail and the dual-control gate.
        coEvery { payoutRepository.getPayoutById("p1") } returns
            Result.success(pendingPayout(Money("100.0"), status = PayoutStatus.PROCESSING))

        val result = useCase("p1", PayoutStatus.COMPLETED, "   ")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { payoutRepository.completePayoutAtomic(any(), any(), any()) }
        coVerify(exactly = 0) { payoutRepository.updatePayoutStatus(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `committed payouts are counted against the balance before approval`() = runBlocking {
        coEvery { payoutRepository.getPayoutById("p1") } returns Result.success(pendingPayout(Money("40000.0")))
        coEvery { payoutRepository.getPendingPayouts() } returns Result.success(
            listOf(pendingPayout(Money("20000.0"), id = "p2", status = PayoutStatus.GROUP_APPROVED))
        )

        val result = useCase("p1", PayoutStatus.GROUP_APPROVED, "admin1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("INSUFFICIENT_LIQUIDITY") == true)
        coVerify(exactly = 0) { payoutRepository.updatePayoutStatus(any(), any(), any(), any()) }
    }

    @Test
    fun `an unreadable pending payout queue blocks approval instead of pretending to be empty`() = runBlocking {
        coEvery { payoutRepository.getPayoutById("p1") } returns Result.success(pendingPayout(Money("40000.0")))
        // R50 000 of balance would cover R40 000 only if the queue were really empty.
        coEvery { payoutRepository.getPendingPayouts() } returns Result.failure(Exception("offline"))

        val result = useCase("p1", PayoutStatus.GROUP_APPROVED, "admin1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { payoutRepository.updatePayoutStatus(any(), any(), any(), any()) }
    }

    @Test
    fun `a transition stamps the admin who performed it as the processor`() = runBlocking {
        // Dual control can only block a self-completion if the earlier transition recorded who
        // made it: an unstamped payout leaves processed_by null and the guard compares against
        // nothing.
        val payout = PayoutRequest(
            id = "p1",
            groupId = "g1",
            amount = Money("15000.0"),
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = PayoutStatus.GROUP_APPROVED
        )
        coEvery { payoutRepository.getPayoutById("p1") } returns Result.success(payout)
        coEvery { payoutRepository.updatePayoutStatus(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        val result = useCase("p1", PayoutStatus.PROCESSING, "admin1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            payoutRepository.updatePayoutStatus("p1", PayoutStatus.PROCESSING, null, null, "admin1")
        }
    }

    @Test
    fun `liquidity is checked against the payout's own group`() = runBlocking {
        val payout = pendingPayout(Money("40000.0")).copy(groupId = "g-real")
        coEvery { payoutRepository.getPayoutById("p1") } returns Result.success(payout)
        coEvery { payoutRepository.updatePayoutStatus(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        val result = useCase("p1", PayoutStatus.GROUP_APPROVED, "admin1")

        assertTrue(result.isSuccess)
        coVerify { financialRepository.getVerifiedBalance("g-real") }
    }

    @Test
    fun `committed payouts in another group do not consume this payout's liquidity`() = runBlocking {
        val payout = pendingPayout(Money("40000.0")).copy(groupId = "g-real")
        coEvery { payoutRepository.getPayoutById("p1") } returns Result.success(payout)
        coEvery { payoutRepository.getPendingPayouts() } returns Result.success(
            listOf(
                pendingPayout(Money("20000.0"), id = "p2", status = PayoutStatus.GROUP_APPROVED)
                    .copy(groupId = "g-other")
            )
        )
        coEvery { payoutRepository.updatePayoutStatus(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        val result = useCase("p1", PayoutStatus.GROUP_APPROVED, "admin1")

        assertTrue(result.isSuccess)
        coVerify { financialRepository.getVerifiedBalance("g-real") }
    }

    @Test
    fun `committed payouts in the payout's own group still consume its liquidity`() = runBlocking {
        val payout = pendingPayout(Money("40000.0")).copy(groupId = "g-real")
        coEvery { payoutRepository.getPayoutById("p1") } returns Result.success(payout)
        coEvery { payoutRepository.getPendingPayouts() } returns Result.success(
            listOf(
                pendingPayout(Money("20000.0"), id = "p2", status = PayoutStatus.GROUP_APPROVED)
                    .copy(groupId = "g-real")
            )
        )

        val result = useCase("p1", PayoutStatus.GROUP_APPROVED, "admin1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("INSUFFICIENT_LIQUIDITY") == true)
        coVerify { financialRepository.getVerifiedBalance("g-real") }
        coVerify(exactly = 0) { payoutRepository.updatePayoutStatus(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failed payout can be rejected instead of remaining stuck`() = runBlocking {
        coEvery { payoutRepository.getPayoutById("p1") } returns
            Result.success(pendingPayout(Money("100.0"), status = PayoutStatus.FAILED))
        coEvery { payoutRepository.updatePayoutStatus(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        val result = useCase("p1", PayoutStatus.REJECTED, "admin1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            payoutRepository.updatePayoutStatus("p1", PayoutStatus.REJECTED, null, null, "admin1")
        }
    }

    private fun pendingPayout(amount: Money, id: String = "p1", status: PayoutStatus = PayoutStatus.PENDING) =
        PayoutRequest(
            id = id,
            groupId = "g1",
            amount = amount,
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = status
        )
}
