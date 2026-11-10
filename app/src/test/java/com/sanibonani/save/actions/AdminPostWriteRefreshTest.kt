package com.sanibonani.save.actions

import com.sanibonani.save.domain.model.ConsolidationReport
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDecisionUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import com.sanibonani.save.domain.usecase.payment.ReconcileBankTransactionUseCase
import com.sanibonani.save.domain.usecase.system.BuildGroupLedgerStatementUseCase
import com.sanibonani.save.domain.usecase.treasury.ConsolidateGroupLedgerUseCase
import com.sanibonani.save.domain.usecase.treasury.PerformGroupReconciliationUseCase
import com.sanibonani.save.domain.usecase.treasury.ResolveLedgerDiscrepancyUseCase
import com.sanibonani.save.features.admin.presentation.actions.AdminFinancialActionHost
import com.sanibonani.save.features.admin.presentation.actions.AdminLedgerActions
import com.sanibonani.save.features.admin.presentation.actions.AdminLoanActions
import com.sanibonani.save.features.admin.presentation.state.AdminFinancialUiState
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.service.AdminGroupContextCacheService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test

/**
 * A write moves money the cached dashboard renders, so the group has to be re-read for the result
 * to appear. The non-forced warmup keeps an already-hydrated snapshot, which made every one of
 * these actions a no-op after boot: the balance kept showing the pre-write number until the user
 * pull-to-refreshed.
 */
class AdminPostWriteRefreshTest {

    private val contextCache: AdminGroupContextCacheService = mockk(relaxed = true)
    private val supabaseRepo: SupabaseRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { contextCache.selectedGroupId } returns MutableStateFlow("g1")
        every { supabaseRepo.currentUserId } returns "admin1"
    }

    @Test
    fun `consolidating the ledger re-reads the group instead of trusting the cached snapshot`() {
        val consolidate = mockk<ConsolidateGroupLedgerUseCase>()
        coEvery { consolidate(any()) } returns Result.success(ConsolidationReport(groupId = "g1"))

        val actions = AdminLedgerActions(
            mockk<PerformGroupReconciliationUseCase>(relaxed = true),
            mockk<ResolveLedgerDiscrepancyUseCase>(relaxed = true),
            mockk<ReconcileBankTransactionUseCase>(relaxed = true),
            consolidate,
            mockk<BuildGroupLedgerStatementUseCase>(relaxed = true),
            contextCache
        )
        val host = FakeHost(AdminFinancialUiState())

        actions.consolidateLedger(host)

        verify(exactly = 1) { contextCache.warmUpGroup("g1", force = true) }
    }

    @Test
    fun `rejecting a loan re-reads the group it was rejected in`() {
        val decision = mockk<ProcessLoanDecisionUseCase>()
        coEvery { decision(any(), any(), any(), any()) } returns Result.success(
            Loan(id = "l1", memberId = "m1", groupId = "g1", amount = Money("100.0"))
        )

        val actions = AdminLoanActions(
            decision,
            mockk<ProcessLoanDisbursementUseCase>(relaxed = true),
            GetAttributableActorIdUseCase(supabaseRepo),
            contextCache
        )
        val host = FakeHost(AdminFinancialUiState())

        actions.rejectLoan(host, loanId = "l1", reason = "Insufficient security")

        verify(exactly = 1) { contextCache.warmUpGroup("g1", force = true) }
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
