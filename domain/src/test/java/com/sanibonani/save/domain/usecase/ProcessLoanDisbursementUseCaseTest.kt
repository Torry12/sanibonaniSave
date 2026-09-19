package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.system.SupabaseRepository
import com.sanibonani.save.domain.repository.treasury.AuditLogRepository
import com.sanibonani.save.domain.repository.treasury.GroupFinancialRepository
import com.sanibonani.save.domain.service.FinancialSecurityService
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProcessLoanDisbursementUseCaseTest {

    private lateinit var useCase: ProcessLoanDisbursementUseCase
    private val loanRepository: LoanRepository = mockk()
    private val supabaseRepository: SupabaseRepository = mockk()
    private val auditLogRepository: AuditLogRepository = mockk(relaxed = true)
    private val financialRepository: GroupFinancialRepository = mockk()

    @Before
    fun setUp() {
        val securityService = FinancialSecurityService(supabaseRepository, financialRepository)
        useCase = ProcessLoanDisbursementUseCase(
            loanRepository,
            securityService,
            auditLogRepository
        )
        every { supabaseRepository.isMfaVerified } returns true
        coEvery { financialRepository.getVerifiedBalance(any()) } returns Result.success(Money(50000.0))
    }

    @Test
    fun `invoke should disburse loan and log event`() = runBlocking {
        // Given
        val loanId = "l1"
        val loan = Loan(id = loanId, memberId = "m1", groupId = "g1", amount = Money(500.0))

        coEvery { loanRepository.getLoanById(loanId) } returns Result.success(loan)
        coEvery { loanRepository.disburseLoan(any(), any(), any()) } returns Result.success(Unit)

        // When
        val result = useCase(loanId, "admin1")

        // Then
        assertTrue(result.isSuccess)
        coVerify { loanRepository.disburseLoan(loanId, any(), "admin1") }
        coVerify { auditLogRepository.logEvent(any()) }
    }
}
