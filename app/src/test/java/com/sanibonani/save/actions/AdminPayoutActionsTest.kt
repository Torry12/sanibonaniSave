package com.sanibonani.save.actions

import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.disbursement.ProcessDisbursementUseCase
import com.sanibonani.save.domain.usecase.disbursement.RequestPayoutUseCase
import com.sanibonani.save.domain.usecase.disbursement.VerifyPayoutDocumentUseCase
import com.sanibonani.save.features.admin.presentation.actions.AdminFinancialActionHost
import com.sanibonani.save.features.admin.presentation.actions.AdminPayoutActions
import com.sanibonani.save.features.admin.presentation.state.AdminFinancialUiState
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.service.AdminGroupContextCacheService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdminPayoutActionsTest {

    private val requestPayoutUseCase: RequestPayoutUseCase = mockk()
    private val payoutRepo: PayoutRepository = mockk(relaxed = true)
    private val processDisbursementUseCase: ProcessDisbursementUseCase = mockk(relaxed = true)
    private val supabaseRepo: SupabaseRepository = mockk(relaxed = true)
    private val contextCache: AdminGroupContextCacheService = mockk(relaxed = true)

    private lateinit var actions: AdminPayoutActions

    @Before
    fun setUp() {
        every { contextCache.selectedGroupId } returns MutableStateFlow<String?>("g1")
        every { supabaseRepo.currentUserId } returns "admin1"
        actions = AdminPayoutActions(
            requestPayoutUseCase,
            VerifyPayoutDocumentUseCase(payoutRepo),
            processDisbursementUseCase,
            GetAttributableActorIdUseCase(supabaseRepo),
            contextCache
        )
    }

    @Test
    fun `a malformed payout amount is reported instead of crashing the screen`() {
        val host = FakeHost(AdminFinancialUiState(payoutAmount = "not-a-number"))

        actions.submitPayoutRequest(host)

        assertNotNull(host.reportedError)
        coVerify(exactly = 0) { requestPayoutUseCase(any(), any(), any()) }
    }

    @Test
    fun `a valid amount is forwarded to the request use case`() {
        val host = FakeHost(AdminFinancialUiState(payoutAmount = "1500.00", selectedMemberId = "m1"))
        coEvery { requestPayoutUseCase(any(), any(), any()) } returns Result.success("p1")

        actions.submitPayoutRequest(host)

        coVerify {
            requestPayoutUseCase(
                groupId = "g1",
                memberId = "m1",
                requestedAmount = Money("1500.00")
            )
        }
        assertTrue(host.actionState.payoutRequestSuccess)
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
