package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.service.PayoutGuard
import com.sanibonani.save.domain.usecase.credit.CalculateLoanRepaymentScheduleUseCase
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.member.presentation.MemberFinanceViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The interest rate and processing fee attached to a loan request are the price the member is
 * billed for, so they must come from the published platform config and never from a default that
 * the app invented after a failed read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberFinanceLoanTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val loanRepo = mockk<LoanRepository>(relaxed = true)
    private val payoutRepo = mockk<PayoutRepository>(relaxed = true)
    private val calculateSchedule = mockk<CalculateLoanRepaymentScheduleUseCase>(relaxed = true)
    private val platformRepo = mockk<PlatformRepository>(relaxed = true)
    private val validateLoanEligibility = mockk<ValidateLoanEligibilityUseCase>(relaxed = true)
    private val payoutGuard = mockk<PayoutGuard>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MemberFinanceViewModel

    private val member = Member(
        id = "m1",
        groupId = "g1",
        fullName = "Alpha Member",
        status = MemberStatus.ACTIVE
    )

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        Dispatchers.setMain(testDispatcher)

        viewModel = MemberFinanceViewModel(
            loanRepo,
            payoutRepo,
            calculateSchedule,
            platformRepo,
            validateLoanEligibility,
            payoutGuard
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private val scheduleResult = CalculateLoanRepaymentScheduleUseCase.Result(
        totalToRepay = Money("1200.0"),
        totalInterest = Money("200.0"),
        monthlyInstallment = Money("120.0"),
        processingFee = Money("9.99"),
        effectiveApr = 12.5,
        schedule = emptyList()
    )

    @Test
    fun `a loan request with unreadable platform settings is not billed an invented rate`() = runTest {
        coEvery { platformRepo.getPlatformSettings() } returns Result.failure(
            IllegalStateException("settings unavailable")
        )
        viewModel.setImpersonationContext(member, Group(id = "g1", name = "Test Group"))
        advanceUntilIdle()

        viewModel.requestLoan(Money("1000.0"), "School fees", 10)
        advanceUntilIdle()

        coVerify(exactly = 0) { loanRepo.requestLoan(any()) }
        verify(exactly = 0) { calculateSchedule(any()) }
        assertEquals(false, viewModel.state.value.isLoading)

        // The event channel buffers, so reading it after the action avoids racing the subscriber.
        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowError)
    }

    @Test
    fun `a loan is priced from the platform config when the group sets no rate`() = runTest {
        coEvery {
            platformRepo.getPlatformSettings()
        } returns Result.success(
            PlatformConfig(
                loanInterestRate = Money("12.5"),
                loanProcessingFee = Money("9.99")
            )
        )
        coEvery { loanRepo.requestLoan(any()) } returns Result.success("l1")
        val params = slot<CalculateLoanRepaymentScheduleUseCase.Params>()
        every { calculateSchedule(capture(params)) } returns scheduleResult

        viewModel.setImpersonationContext(member, Group(id = "g1", name = "Test Group"))
        advanceUntilIdle()

        viewModel.requestLoan(Money("1000.0"), "School fees", 10)
        advanceUntilIdle()

        assertEquals(12.5, params.captured.annualInterestRate, 0.0)
        assertEquals(Money("9.99"), params.captured.processingFee)

        val loan = slot<Loan>()
        coVerify { loanRepo.requestLoan(capture(loan)) }
        assertEquals(12.5, loan.captured.interestRate, 0.0)
    }

    @Test
    fun `the group rate overrides the platform rate`() = runTest {
        coEvery {
            platformRepo.getPlatformSettings()
        } returns Result.success(PlatformConfig(loanInterestRate = Money("12.5")))
        coEvery { loanRepo.requestLoan(any()) } returns Result.success("l1")
        every { calculateSchedule(any()) } returns scheduleResult

        viewModel.setImpersonationContext(
            member,
            Group(id = "g1", name = "Test Group", loanInterestRate = 8.0)
        )
        advanceUntilIdle()

        viewModel.requestLoan(Money("1000.0"), "School fees", 10)
        advanceUntilIdle()

        val loan = slot<Loan>()
        coVerify { loanRepo.requestLoan(capture(loan)) }
        assertEquals(8.0, loan.captured.interestRate, 0.0)
    }
}
