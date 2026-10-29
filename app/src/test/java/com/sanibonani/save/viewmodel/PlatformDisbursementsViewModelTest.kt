package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.auth.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.credit.GetPendingLoanRequestsUseCase
import com.sanibonani.save.domain.usecase.disbursement.GetPendingPayoutsUseCase
import com.sanibonani.save.domain.usecase.disbursement.ProcessDisbursementUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDecisionUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import com.sanibonani.save.domain.usecase.member.GetEscalatedClaimsUseCase
import com.sanibonani.save.domain.usecase.member.GetMemberByIdUseCase
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
            GetEscalatedClaimsUseCase(claimRepo),
            GetPendingPayoutsUseCase(payoutRepo),
            GetMemberByIdUseCase(memberRepo),
            GetPendingLoanRequestsUseCase(loanRepo),
            processLoanDecisionUseCase,
            processLoanDisbursementUseCase,
            processDisbursementUseCase,
            processBurialClaimUseCase,
            GetAttributableActorIdUseCase(supabaseRepo)
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
    fun `a failed section refresh keeps the data already on screen`() = runTest {
        val claim = BeneficiaryPayoutClaim(id = "c1", groupId = "g1")
        val payout = PayoutRequest(
            id = "p1",
            groupId = "g1",
            amount = Money("1000.0"),
            status = PayoutStatus.PENDING,
            bankName = "FNB",
            accountNo = "123456789",
            branchCode = "250655"
        )
        val loan = Loan(id = "l1", memberId = "m1", groupId = "g1", amount = Money("500.0"))
        coEvery { claimRepo.getEscalatedClaimsSync() } returns Result.success(listOf(claim))
        coEvery { payoutRepo.getPendingPayouts() } returns Result.success(listOf(payout))
        coEvery { loanRepo.getAllPendingLoansSync() } returns Result.success(listOf(loan))

        viewModel.load()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.payouts.size)

        coEvery { claimRepo.getEscalatedClaimsSync() } returns
            Result.failure(Exception("HTTP 400 column face_photo_status does not exist"))
        coEvery { payoutRepo.getPendingPayouts() } returns Result.success(emptyList())
        coEvery { loanRepo.getAllPendingLoansSync() } returns Result.success(emptyList())
        val errors = mutableListOf<UIEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { errors.add(it) }
        }

        viewModel.load()
        advanceUntilIdle()

        // A failed fetch is not a verified empty response: the claims queue keeps
        // what is on screen, while sections that answered are replaced as usual.
        assertEquals(listOf("c1"), viewModel.state.value.escalatedClaims.map { it.id })
        assertTrue(viewModel.state.value.payouts.isEmpty())
        assertTrue(viewModel.state.value.loanRequestsByGroup.isEmpty())
        assertTrue(errors.filterIsInstance<UIEvent.ShowError>()
            .any { it.message.contains("escalated claims") })
    }

    @Test
    fun `the refresh error names every failed section at once`() = runTest {
        coEvery { claimRepo.getEscalatedClaimsSync() } returns Result.failure(Exception("offline"))
        coEvery { loanRepo.getAllPendingLoansSync() } returns Result.failure(Exception("offline"))
        val errors = mutableListOf<UIEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { errors.add(it) }
        }

        viewModel.load()
        advanceUntilIdle()

        val shown = errors.filterIsInstance<UIEvent.ShowError>().single()
        assertTrue(shown.message.contains("escalated claims"))
        assertTrue(shown.message.contains("loan requests"))
        assertFalse(shown.message.contains("payout requests"))
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
            processDisbursementUseCase(any(), any(), any())
        } returns Result.success(failed.copy(status = PayoutStatus.REJECTED))

        viewModel.load()
        advanceUntilIdle()
        viewModel.rejectPayout("p1")
        advanceUntilIdle()

        coVerify {
            processDisbursementUseCase(
                payoutId = "p1",
                status = PayoutStatus.REJECTED,
                adminId = "platform_admin"
            )
        }
    }

    @Test
    fun `a blank session does not ask the domain to stamp an empty admin`() = runTest {
        val pending = PayoutRequest(
            id = "p1",
            groupId = "g1",
            amount = Money("1000.0"),
            status = PayoutStatus.PENDING,
            bankName = "FNB",
            accountNo = "123456789",
            branchCode = "250655"
        )
        coEvery { payoutRepo.getPendingPayouts() } returns Result.success(listOf(pending))
        every { supabaseRepo.currentUserId } returns null
        val errors = mutableListOf<UIEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { errors.add(it) }
        }

        viewModel.load()
        advanceUntilIdle()
        viewModel.approvePayout("p1")
        advanceUntilIdle()

        coVerify(exactly = 0) { processDisbursementUseCase(any(), any(), any()) }
        assertTrue(errors.filterIsInstance<UIEvent.ShowError>().isNotEmpty())
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

    @Test
    fun `a blank session does not ask the domain to stamp an empty claim reviewer`() = runTest {
        every { supabaseRepo.currentUserId } returns null
        val errors = mutableListOf<UIEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { errors.add(it) }
        }

        viewModel.approveBurialClaim("c1", "documents verified")
        advanceUntilIdle()

        coVerify(exactly = 0) { processBurialClaimUseCase(any(), any(), any(), any(), any()) }
        assertTrue(errors.filterIsInstance<UIEvent.ShowError>()
            .any { it.message.contains("Sign in again") })
    }

    @Test
    fun `a blank session reports a blocked loan approval instead of dropping the tap`() = runTest {
        every { supabaseRepo.currentUserId } returns null
        val loan = Loan(id = "l1", memberId = "m1", groupId = "g1", amount = Money("500.0"))
        val errors = mutableListOf<UIEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { errors.add(it) }
        }

        viewModel.approveLoanRequest(loan)
        advanceUntilIdle()

        coVerify(exactly = 0) { processLoanDecisionUseCase(any(), any(), any(), any()) }
        assertTrue(errors.filterIsInstance<UIEvent.ShowError>()
            .any { it.message.contains("Sign in again") })
    }
}
