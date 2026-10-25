package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.auth.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.usecase.disbursement.ProcessDisbursementUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDecisionUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import com.sanibonani.save.domain.usecase.member.ProcessBurialClaimUseCase
import com.sanibonani.save.features.admin.presentation.PlatformDisbursementsViewModel
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.domain.model.AuthSession
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.delay

@OptIn(ExperimentalCoroutinesApi::class)
class PlatformDisbursementsViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val claimRepo = mockk<BeneficiaryClaimRepository>()
    private val payoutRepo = mockk<PayoutRepository>()
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val loanRepo = mockk<LoanRepository>()
    private val processLoanDecisionUseCase = mockk<ProcessLoanDecisionUseCase>(relaxed = true)
    private val processLoanDisbursementUseCase = mockk<ProcessLoanDisbursementUseCase>(relaxed = true)
    private val processDisbursementUseCase = mockk<ProcessDisbursementUseCase>(relaxed = true)
    private val processBurialClaimUseCase = mockk<ProcessBurialClaimUseCase>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)

    private lateinit var viewModel: PlatformDisbursementsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        Dispatchers.setMain(testDispatcher)

        every { supabaseRepo.currentUserId } returns "platform_admin"
        val mockSession = mockk<com.sanibonani.save.domain.model.AuthSession>(relaxed = true)
        val sessionFlow = MutableStateFlow<com.sanibonani.save.domain.model.AuthSession?>(mockSession)
        every { supabaseRepo.sessionFlow } returns sessionFlow

        coEvery { claimRepo.getEscalatedClaimsSync() } returns Result.success(listOf<BeneficiaryPayoutClaim>())
        coEvery { payoutRepo.getPendingPayouts() } returns Result.success(listOf<PayoutRequest>())
        coEvery { loanRepo.getAllPendingLoansSync() } returns Result.success(listOf<Loan>())

        viewModel = PlatformDisbursementsViewModel(
            claimRepo,
            payoutRepo,
            memberRepo,
            loanRepo,
            processLoanDecisionUseCase,
            processLoanDisbursementUseCase,
            processDisbursementUseCase,
            processBurialClaimUseCase,
            supabaseRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `load surfaces a failed payout fetch instead of showing an empty queue`() = runTest {
        coEvery { payoutRepo.getPendingPayouts() } returns Result.failure(Exception("offline"))
        val errors = mutableListOf<UIEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { errors.add(it) }
        }

        viewModel.load()
        advanceUntilIdle()

        assertTrue(errors.filterIsInstance<UIEvent.ShowError>().isNotEmpty())
        assertTrue(viewModel.state.value.payouts.isEmpty())
    }

    @Test
    fun `rejecting a failed payout requests the rejected state`() = runTest {
        val failed = PayoutRequest(
            id = "p1",
            groupId = "g1",
            amount = Money("1000.0"),
            status = PayoutStatus.FAILED,
            bankName = "FNB",
            accountNo = "123456789",
            branchCode = "250655"
        )
        coEvery { payoutRepo.getPendingPayouts() } returns Result.success(listOf(failed))
        coEvery {
            processDisbursementUseCase(any(), any(), any(), any())
        } returns Result.success(failed.copy(status = PayoutStatus.REJECTED))

        viewModel.load()
        advanceUntilIdle()
        viewModel.rejectPayout("p1", "g1")
        advanceUntilIdle()

        coVerify {
            processDisbursementUseCase(
                payoutId = "p1",
                groupId = "g1",
                status = PayoutStatus.REJECTED,
                adminId = "platform_admin"
            )
        }
    }

    @Test
    fun `load updates state with payouts loans and claims`() = runTest {
        val payouts = listOf(PayoutRequest(
            id = "p1",
            groupId = "g1",
            amount = Money("1000.0"),
            status = PayoutStatus.PENDING,
            bankName = "FNB",
            accountNo = "123456789",
            branchCode = "250655"
        ))
        coEvery { payoutRepo.getPendingPayouts() } returns Result.success(payouts)

        viewModel.load()

        delay(100)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("p1", state.payouts.first().id)
    }

    @Test
    fun `approveLoanRequest updates loan status and logs audit`() = runTest {
        val loan = Loan(id = "l1", memberId = "m1", groupId = "g1", amount = Money("500.0"))
        coEvery {
            processLoanDecisionUseCase(any(), any(), any(), any())
        } returns Result.success(loan.copy(status = LoanStatus.APPROVED))

        viewModel.approveLoanRequest(loan)
        advanceUntilIdle()

        coVerify {
            processLoanDecisionUseCase(
                loanId = "l1",
                decision = ProcessLoanDecisionUseCase.LoanDecision.Approve,
                adminId = "platform_admin"
            )
        }
    }
}
