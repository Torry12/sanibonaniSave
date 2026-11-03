package com.sanibonani.save.actions

import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDecisionUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import com.sanibonani.save.features.admin.presentation.actions.AdminFinancialActionHost
import com.sanibonani.save.features.admin.presentation.actions.AdminLoanActions
import com.sanibonani.save.features.admin.presentation.state.AdminFinancialUiState
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.service.AdminGroupContextCacheService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Approving a loan fires two writes: the decision and the disbursement. The decision landing is
 * genuinely good news, but the disbursement leg used to swallow its refusal entirely - the `it` was
 * never read - and answer with a bare green "Loan approved". For a money path that is the worst
 * possible message: the member's cash did not move, the admin believes it did, and the reason
 * (`[FIN_001]` insufficient liquidity, `[SEC_001]` MFA demanded for a large payout) is gone.
 */
class AdminLoanApprovalTest {

    private val processLoanDecision = mockk<ProcessLoanDecisionUseCase>()
    private val processLoanDisbursement = mockk<ProcessLoanDisbursementUseCase>()
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val contextCache = mockk<AdminGroupContextCacheService>(relaxed = true)

    private lateinit var actions: AdminLoanActions

    private val pendingLoan = Loan(id = "l1", groupId = "g1", status = LoanStatus.PENDING)

    @Before
    fun setUp() {
        every { contextCache.selectedGroupId } returns MutableStateFlow<String?>("g1")
        every { supabaseRepo.currentUserId } returns "admin1"
        actions = AdminLoanActions(
            processLoanDecision,
            processLoanDisbursement,
            GetAttributableActorIdUseCase(supabaseRepo),
            contextCache
        )
    }

    private fun host() = FakeHost(AdminFinancialUiState(groupLoans = listOf(pendingLoan)))

    @Test
    fun `an approved and disbursed loan reads as approved and disbursed`() {
        val host = host()
        coEvery { processLoanDecision(any(), any(), any(), any()) } returns
            Result.success(pendingLoan.copy(status = LoanStatus.APPROVED))
        coEvery { processLoanDisbursement(any(), any(), any()) } returns
            Result.success(pendingLoan.copy(status = LoanStatus.ACTIVE))

        actions.approveLoan(host, "l1")

        assertEquals(LoanStatus.ACTIVE, host.state.groupLoans.single().status)
        assertEquals("Loan approved and disbursed", host.state.successMessage)
        assertNull(host.reportedError)
    }

    @Test
    fun `a refused disbursement is reported with its reason`() {
        val host = host()
        val refusal = IllegalStateException("[FIN_001] INSUFFICIENT_LIQUIDITY")
        coEvery { processLoanDecision(any(), any(), any(), any()) } returns
            Result.success(pendingLoan.copy(status = LoanStatus.APPROVED))
        coEvery { processLoanDisbursement(any(), any(), any()) } returns Result.failure(refusal)

        actions.approveLoan(host, "l1")

        assertSame(
            "the disbursement refusal must reach the action error path, which also opens the MFA " +
                "challenge when the backend demands it",
            refusal,
            host.reportedError
        )
    }

    @Test
    fun `a refused disbursement does not claim the loan was disbursed`() {
        val host = host()
        coEvery { processLoanDecision(any(), any(), any(), any()) } returns
            Result.success(pendingLoan.copy(status = LoanStatus.APPROVED))
        coEvery { processLoanDisbursement(any(), any(), any()) } returns
            Result.failure(IllegalStateException("[FIN_001] INSUFFICIENT_LIQUIDITY"))

        actions.approveLoan(host, "l1")

        // The row stays APPROVED - that part is true - and nothing may read as an unqualified success.
        assertEquals(LoanStatus.APPROVED, host.state.groupLoans.single().status)
        assertNull(host.state.successMessage)
    }

    private class FakeHost(initial: AdminFinancialUiState) : AdminFinancialActionHost {
        var state = initial
        var reportedError: Throwable? = null

        override val actionState: AdminFinancialUiState get() = state

        override fun updateActionState(reducer: (AdminFinancialUiState) -> AdminFinancialUiState) {
            state = reducer(state)
        }

        override fun launchAction(key: String, block: suspend CoroutineScope.() -> Unit): Job =
            CoroutineScope(Dispatchers.Unconfined).launch { block() }

        override fun reportError(throwable: Throwable) {
            reportedError = throwable
        }

        override fun reportActionError(throwable: Throwable) {
            reportedError = throwable
        }

        override fun publishEvent(event: UIEvent) = Unit

        override fun notifyUser(message: String) = Unit

        override fun reloadFinancialData(groupId: String) = Unit
    }
}
