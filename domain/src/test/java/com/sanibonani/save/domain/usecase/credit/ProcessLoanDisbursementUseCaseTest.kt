package com.sanibonani.save.domain.usecase.credit
import com.sanibonani.save.domain.event.DomainEvent
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.LoanDisbursedEvent
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import com.sanibonani.save.domain.service.FinancialSecurityService
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import io.mockk.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
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
        coEvery { financialRepository.getVerifiedBalance(any()) } returns Result.success(Money("50000.0"))
    }

    @Test
    fun `invoke should disburse loan and log event`() = runBlocking {
        // Given
        val loanId = "l1"
        val loan = Loan(id = loanId, memberId = "m1", groupId = "g1", amount = Money("500.0"))

        coEvery { loanRepository.getLoanById(loanId) } returns Result.success(loan)
        coEvery { loanRepository.disburseLoan(any(), any(), any()) } returns Result.success(Unit)

        // When
        val result = useCase(loanId, "admin1")

        // Then
        assertTrue(result.isSuccess)
        coVerify { loanRepository.disburseLoan(loanId, any(), "admin1") }
        coVerify { auditLogRepository.logEvent(any()) }
    }

    @Test
    fun `a disbursement the server completed survives an unreadable confirmation`() = runBlocking {
        val loanId = "l1"
        val loan = Loan(
            id = loanId,
            memberId = "m1",
            groupId = "g1",
            amount = Money("500.0"),
            status = LoanStatus.PENDING
        )
        coEvery { loanRepository.getLoanById(loanId) } returnsMany listOf(
            Result.success(loan),
            Result.failure(IllegalStateException("read failed"))
        )
        coEvery { loanRepository.disburseLoan(any(), any(), any()) } returns Result.success(Unit)

        val result = useCase(loanId, "admin1")

        // The re-read sat after the write and before the verdict, so a transport hiccup reported a
        // completed disbursement as a failure - and the admin was left holding a button to press
        // again on money that had already left the group.
        assertTrue(result.isSuccess)
        assertEquals(loanId, result.getOrThrow().id)
        coVerify(exactly = 1) { auditLogRepository.logEvent(any()) }
    }

    @Test
    fun `the orchestration does not re-announce a disbursement the repository already announced`() = runBlocking {
        val loanId = "l1"
        val loan = Loan(id = loanId, memberId = "m1", groupId = "g1", amount = Money("500.0"))
        coEvery { loanRepository.getLoanById(loanId) } returns Result.success(loan)
        coEvery { loanRepository.disburseLoan(any(), any(), any()) } returns Result.success(Unit)

        val received = CopyOnWriteArrayList<DomainEvent>()
        val collector = launch(context = Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            EventBus.events.collect { received.add(it) }
        }

        val result = useCase(loanId, "admin1")

        collector.cancel()
        assertTrue(result.isSuccess)
        // LoanRepositoryImpl.disburseLoan emits this event inside the authoritative write, so the
        // second emission here ran the behaviour-tracking handler and the group broadcast twice for
        // one payment.
        assertEquals(0, received.filterIsInstance<LoanDisbursedEvent>().size)
    }
}
