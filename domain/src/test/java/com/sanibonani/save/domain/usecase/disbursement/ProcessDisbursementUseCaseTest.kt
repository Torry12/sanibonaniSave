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
        coEvery { financialRepository.getVerifiedBalance(any()) } returns Result.success(Money(50000.0))
        coEvery { payoutRepository.getPendingPayouts() } returns Result.success(emptyList())
    }

    @Test
    fun `invoke should complete payout atomically when status is COMPLETED`() = runBlocking {
        // Given
        val payoutId = "p1"
        val payout = PayoutRequest(
            id = payoutId,
            groupId = "g1",
            amount = Money(100.0),
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = PayoutStatus.PROCESSING // Must be in PROCESSING to move to COMPLETED
        )

        coEvery { payoutRepository.getPayoutById(payoutId) } returns Result.success(payout)
        coEvery { payoutRepository.completePayoutAtomic(any(), any(), any()) } returns Result.success(Unit)

        // When
        val result = useCase(payoutId, "g1", PayoutStatus.COMPLETED, "admin1")

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
            amount = Money(15000.0),
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = PayoutStatus.PROCESSING
        )

        every { supabaseRepository.isMfaVerified } returns false
        coEvery { payoutRepository.getPayoutById(payoutId) } returns Result.success(payout)

        // When
        val result = useCase(payoutId, "g1", PayoutStatus.COMPLETED, "admin1")

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("MFA_REQUIRED") == true)
    }
}
