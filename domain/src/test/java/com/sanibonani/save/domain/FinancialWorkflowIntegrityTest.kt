package com.sanibonani.save.domain

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

class FinancialWorkflowIntegrityTest {

    private val payoutRepository: PayoutRepository = mockk()
    private val supabaseRepository: SupabaseRepository = mockk()
    private val financialRepository: GroupFinancialRepository = mockk()

    private lateinit var securityService: FinancialSecurityService
    private lateinit var processDisbursementUseCase: ProcessDisbursementUseCase

    @Before
    fun setUp() {
        securityService = FinancialSecurityService(supabaseRepository, financialRepository)
        processDisbursementUseCase = ProcessDisbursementUseCase(payoutRepository, securityService)

        every { supabaseRepository.isMfaVerified } returns true
        coEvery { financialRepository.getVerifiedBalance(any()) } returns Result.success(Money(50000.0))
        coEvery { payoutRepository.getPendingPayouts() } returns Result.success(emptyList())
    }

    @Test
    fun `disbursement should fail if liquidity is insufficient`() = runBlocking {
        // Given
        val payoutId = "p1"
        val groupId = "g1"
        val amount = Money(1000.0)
        val payout = PayoutRequest(
            id = payoutId,
            groupId = groupId,
            amount = amount,
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = PayoutStatus.PROCESSING
        )

        coEvery { payoutRepository.getPayoutById(payoutId) } returns Result.success(payout)
        coEvery { financialRepository.getVerifiedBalance(groupId) } returns Result.success(Money(500.0)) // Less than amount

        // When
        val result = processDisbursementUseCase(payoutId, groupId, PayoutStatus.COMPLETED, "admin1")

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("INSUFFICIENT_LIQUIDITY") == true)
    }

    @Test
    fun `high value disbursement should fail if MFA not verified`() = runBlocking {
        // Given
        val payoutId = "p1"
        val amount = Money(15000.0)
        val payout = PayoutRequest(
            id = payoutId,
            groupId = "g1",
            amount = amount,
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = PayoutStatus.PROCESSING
        )

        every { supabaseRepository.isMfaVerified } returns false
        coEvery { payoutRepository.getPayoutById(payoutId) } returns Result.success(payout)

        // When
        val result = processDisbursementUseCase(payoutId, "g1", PayoutStatus.COMPLETED, "admin1")

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("MFA_REQUIRED") == true)
    }
}
