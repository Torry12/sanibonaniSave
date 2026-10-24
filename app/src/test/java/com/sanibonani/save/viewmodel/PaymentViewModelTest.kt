package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.auth.*
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.service.PlatformFeeService
import com.sanibonani.save.domain.usecase.equity.ProcessContributionUseCase
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.utils.RealtimePaymentPreview
import com.sanibonani.save.domain.utils.DueBreakdown
import com.sanibonani.save.domain.validation.ValidationResult
import com.sanibonani.save.domain.validation.ValidationUtils
import com.sanibonani.save.features.financial.presentation.PaymentViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.repository.credit.LoanRepository

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val contributionRepo = mockk<ContributionRepository>(relaxed = true)
    private val loanRepo = mockk<LoanRepository>(relaxed = true)
    private val platformConfigRepo = mockk<PlatformConfigRepository>(relaxed = true)
    private val processContributionUseCase = mockk<ProcessContributionUseCase>(relaxed = true)
    private val feeService = mockk<PlatformFeeService>(relaxed = true)
    private val paymentCalculator = spyk(PaymentCalculator(feeService))

    private lateinit var viewModel: PaymentViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        mockkObject(ValidationUtils)
        every { ValidationUtils.validatePaymentFields(any(), any(), any()) } returns ValidationResult.Valid

        every { feeService.calculateMonthlyMemberIndividual(any(), any()) } returns Money.ZERO
        every { feeService.calculateMonthlyGroupTotal(any()) } returns Money.ZERO
        every { feeService.getRegistrationFee() } returns Money.ZERO
        every { feeService.getShortfallRecoveryFee() } returns Money.ZERO

        Dispatchers.setMain(testDispatcher)

        every { supabaseRepo.currentUserId } returns "user_123"
        every { supabaseRepo.sessionFlow } returns flowOf(mockk(relaxed = true))

        viewModel = PaymentViewModel(
            supabaseRepo,
            groupRepo,
            memberRepo,
            contributionRepo,
            loanRepo,
            platformConfigRepo,
            processContributionUseCase,
            paymentCalculator
        )
        viewModel.setActive(true)
    }

    private fun mockPaymentContext(groupId: String = "g1") {
        val member = Member(id = "m1", groupId = groupId, joinedAt = "2020-01-01T00:00:00Z", status = MemberStatus.ACTIVE)
        val group = Group(id = groupId, monthlyContribution = Money("200.0"), paymentDueDay = 1, allowPartialPayment = true)

        every { memberRepo.observeMemberByUserId(any(), any()) } returns flowOf(Result.success(member))
        coEvery { groupRepo.getGroupById(any()) } returns Result.success(group)
        every { contributionRepo.observeMemberContributions(any(), any()) } returns flowOf(Result.success(emptyList()))
        every { loanRepo.getMemberLoans(any()) } returns flowOf(Result.success(emptyList()))

        viewModel.loadPaymentContext(groupId, "contribution")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `loadPaymentContext - success updates state`() = runTest {
        val groupId = "g1"
        val member = Member(id = "m1", groupId = groupId, status = MemberStatus.ACTIVE)
        val group = Group(id = groupId, monthlyContribution = Money("200.0"))

        every { memberRepo.observeMemberByUserId("user_123", groupId) } returns flowOf(Result.success(member))
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(group)
        every { contributionRepo.observeMemberContributions("m1", groupId) } returns flowOf(Result.success(emptyList()))
        every { loanRepo.getMemberLoans("m1") } returns flowOf(Result.success(emptyList()))

        viewModel.loadPaymentContext(groupId, "contribution")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("m1", state.member?.id)
        assertEquals(groupId, state.group?.id)
        assertNotNull(state.calculation)
    }

    @Test
    fun `onAmountChanged - recalculates realtime preview`() = runTest {
        // Initial setup
        val groupId = "g1"
        val member = Member(id = "m1", groupId = groupId, joinedAt = "2020-01-01T00:00:00Z", status = MemberStatus.ACTIVE)
        val group = Group(id = groupId, monthlyContribution = Money("200.0"), paymentDueDay = 1)

        every { memberRepo.observeMemberByUserId("user_123", groupId) } returns flowOf(Result.success(member))
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(group)
        every { contributionRepo.observeMemberContributions("m1", groupId) } returns flowOf(Result.success(emptyList()))
        every { loanRepo.getMemberLoans("m1") } returns flowOf(Result.success(emptyList()))

        viewModel.loadPaymentContext(groupId, "contribution")
        advanceUntilIdle()

        // Before payment change
        val initialShortfall = viewModel.state.value.realtimeShortfall
        assertTrue("Initial shortfall should be > 0 (found $initialShortfall)", initialShortfall.isPositive)

        // Change amount to cover shortfall
        viewModel.onAmountChanged(initialShortfall)

        val updatedState = viewModel.state.value
        assertTrue("Expected shortfall to be zero", updatedState.realtimeShortfall.isZero)
    }

    @Test
    fun `processPayment - success updates state to success`() = runTest {
        val groupId = "g1"
        val amount = Money("1000")
        val txId = "tx_123"

        mockPaymentContext(groupId)
        advanceUntilIdle()

        coEvery { processContributionUseCase.invoke(
            type = any(),
            amount = any(),
            groupId = any(),
            member = any(),
            group = any(),
            calculation = any(),
            method = any()
        ) } returns ContributionOutcome.Success(txId)

        viewModel.processPayment(
            type = "contribution",
            amount = amount,
            groupId = groupId,
            cardNumber = "4242424242424242",
            expiry = "12/28",
            cvv = "123"
        )

        advanceTimeBy(2100)
        runCurrent()

        val state = viewModel.state.value
        assertTrue("Expected success to be true", state.isSuccess)
        assertEquals(txId, state.transactionId)
        assertFalse(state.isProcessing)
    }

    @Test
    fun `processPayment - failure updates error state`() = runTest {
        val amount = Money("1000")

        mockPaymentContext("g1")
        advanceUntilIdle()

        coEvery { processContributionUseCase.invoke(any(), any(), any(), any(), any(), any(), any()) } returns ContributionOutcome.Failure("Network error")

        viewModel.processPayment(
            type = "contribution",
            amount = amount,
            groupId = "g1",
            cardNumber = "4242424242424242",
            expiry = "12/28",
            cvv = "123"
        )

        advanceTimeBy(2100)
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isSuccess)
        assertEquals("Network error", state.error)
        assertFalse(state.isProcessing)
    }
}
