package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.InsufficientFundsException
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutType
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
 * A member withdrawal moves real money out of the group treasury, so the request must pass the
 * same guards as an admin payout before it is queued.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberFinanceWithdrawalTest {

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

    private val group = Group(id = "g1", name = "Test Group")

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

    private fun memberWithBank(
        bankName: String?,
        accountNo: String?,
        branchCode: String?
    ): Member {
        val member = Member(
            id = "m1",
            groupId = "g1",
            fullName = "Alpha Member",
            status = MemberStatus.ACTIVE
        )
        return member.copy(bankName = bankName, accountNo = accountNo, branchCode = branchCode)
    }

    @Test
    fun `withdrawal without bank details is rejected before anything is queued`() = runTest {
        viewModel.setImpersonationContext(memberWithBank(null, "123456789", "250655"), group)
        advanceUntilIdle()

        viewModel.requestWithdrawal(Money("500.0"), PayoutType.EMERGENCY)
        advanceUntilIdle()

        coVerify(exactly = 0) { payoutRepo.requestPayout(any()) }
        coVerify(exactly = 0) { payoutGuard.validateRequest(any(), any(), any(), any()) }
        assertEquals(false, viewModel.state.value.isLoading)

        // The event channel buffers, so reading it after the action avoids racing the subscriber.
        val events = viewModel.events.take(1).toList()
        assertTrue("emitted events: $events", events.singleOrNull() is UIEvent.ShowError)
        assertTrue((events.single() as UIEvent.ShowError).message.contains("bank name"))
    }

    @Test
    fun `withdrawal blocked by the payout guard never reaches the repository`() = runTest {
        coEvery {
            payoutGuard.validateRequest(any(), any(), any(), any())
        } returns Result.failure(InsufficientFundsException(Money("500.0"), Money("10.0")))
        viewModel.setImpersonationContext(memberWithBank("FNB", "123456789", "250655"), group)
        advanceUntilIdle()

        viewModel.requestWithdrawal(Money("500.0"), PayoutType.EMERGENCY)
        advanceUntilIdle()

        coVerify(exactly = 0) { payoutRepo.requestPayout(any()) }
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun `valid withdrawal is guarded and queued with cleaned bank details`() = runTest {
        coEvery { payoutGuard.validateRequest(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { payoutRepo.requestPayout(any()) } returns Result.success("p1")
        viewModel.setImpersonationContext(memberWithBank(" FNB ", " 123456789 ", " 250655 "), group)
        advanceUntilIdle()

        viewModel.requestWithdrawal(Money("500.0"), PayoutType.EMERGENCY)
        advanceUntilIdle()

        val captured = slot<PayoutRequest>()
        coVerify(exactly = 1) { payoutRepo.requestPayout(capture(captured)) }
        assertEquals("FNB", captured.captured.bankName)
        assertEquals("123456789", captured.captured.accountNo)
        assertEquals("250655", captured.captured.branchCode)
        assertEquals(PayoutType.EMERGENCY, captured.captured.type)
    }
}
