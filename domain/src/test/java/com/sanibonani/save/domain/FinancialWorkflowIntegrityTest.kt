package com.sanibonani.save.domain

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import com.sanibonani.save.domain.service.FinancialSecurityService
import com.sanibonani.save.domain.usecase.disbursement.ProcessDisbursementUseCase
import com.sanibonani.save.domain.utils.requiresMfaChallenge
import com.sanibonani.save.domain.utils.toUserMessage
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
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
        coEvery { financialRepository.getVerifiedBalance(any()) } returns Result.success(Money("50000.0"))
        coEvery { payoutRepository.getPendingPayouts() } returns Result.success(emptyList())
    }

    @Test
    fun `disbursement should fail if liquidity is insufficient`() = runBlocking {
        // Given
        val payoutId = "p1"
        val groupId = "g1"
        val amount = Money("1000.0")
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
        coEvery { financialRepository.getVerifiedBalance(groupId) } returns Result.success(Money("500.0")) // Less than amount

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
        val amount = Money("15000.0")
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
        // Presenters must be able to recognise the gate without sniffing the message.
        assertTrue(result.exceptionOrNull() is MfaRequiredException)
        assertTrue(result.exceptionOrNull()!!.requiresMfaChallenge())
    }

    @Test
    fun `the dual-control block is a typed gate with a plain-language message`() = runBlocking {
        val payoutId = "p1"
        val payout = PayoutRequest(
            id = payoutId,
            groupId = "g1",
            amount = Money("15000.0"),
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            status = PayoutStatus.PROCESSING,
            processedBy = "admin1"
        )
        coEvery { payoutRepository.getPayoutById(payoutId) } returns Result.success(payout)

        val result = processDisbursementUseCase(payoutId, "g1", PayoutStatus.COMPLETED, "admin1")

        assertTrue(result.exceptionOrNull() is DualControlViolationException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains(DualControlViolationException.MARKER))
        // Presenters must never show the machine marker to an admin.
        val userMessage = result.exceptionOrNull()!!.toUserMessage()
        assertFalse(userMessage.contains(DualControlViolationException.MARKER))
        assertTrue(userMessage.contains("administrator", ignoreCase = true))
    }

    @Test
    fun `only the security service and the database marker require an mfa challenge`() {
        assertTrue(MfaRequiredException(Money("10000.00")).requiresMfaChallenge())
        // The same gate raised by a DB trigger arrives as a plain exception.
        assertTrue(Exception("MFA_REQUIRED: AAL2 verification missing").requiresMfaChallenge())
        assertFalse(Exception("Transition from PENDING to COMPLETED is not permitted").requiresMfaChallenge())
    }

    @Test
    fun `mfa gate message stays user facing when it reaches a generic error handler`() {
        val message = MfaRequiredException(Money("15000.0")).toUserMessage()
        assertFalse(message.contains("MFA_REQUIRED"))
        assertFalse(message.contains("AAL2"))
        assertTrue(message.contains("verification"))
    }
}
