package com.sanibonani.save.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.usecase.ProcessPaymentUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val processPaymentUseCase = mockk<ProcessPaymentUseCase>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: PaymentViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { supabaseRepo.currentUserId } returns "user-1"
        viewModel = PaymentViewModel(
            supabaseRepo = supabaseRepo,
            groupRepo = groupRepo,
            memberRepo = memberRepo,
            processPaymentUseCase = processPaymentUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadPaymentContext populates state and recalculates preview using existing input`() = runTest {
        val today = LocalDate.now()
        val member = Member(
            id = "member-1",
            groupId = "group-1",
            joinedAt = today.minusMonths(1).withDayOfMonth(1).toString() + "T00:00:00Z"
        )
        val group = Group(
            id = "group-1",
            type = GroupType.STOKVEL,
            monthlyContribution = 200.0,
            paymentDueDay = 1,
            lateFee = 25.0,
            lateFeeGraceDays = 0,
            allowPartialPayment = true
        )
        val contributions = listOf(
            Contribution(
                id = "contribution-1",
                memberId = "member-1",
                groupId = "group-1",
                amount = 50.0,
                status = com.sanibonani.save.domain.model.ContributionStatus.PARTIAL,
                dueDate = today.minusMonths(1).withDayOfMonth(1).toString()
            )
        )

        coEvery { memberRepo.getMemberByUserId("user-1", "group-1") } returns Result.success(member)
        coEvery { groupRepo.getGroupById("group-1") } returns Result.success(group)
        every { memberRepo.getMemberContributions("member-1", "group-1") } returns flowOf(Result.success(contributions))

        viewModel.onAmountChanged(250.0)
        viewModel.loadPaymentContext("group-1")
        advanceUntilIdle()

        val expectedCalculation = PaymentCalculator.calculateStatus(group, member, contributions)
        val expectedPreview = calculateRealtimePaymentPreview(
            group = group,
            member = member,
            contributions = contributions,
            calculation = expectedCalculation,
            inputAmount = 250.0
        )
        val state = viewModel.state.value

        assertFalse(state.isProcessing)
        assertEquals(member, state.member)
        assertEquals(group, state.group)
        assertEquals(contributions, state.contributions)
        assertNotNull(state.calculation)
        assertEquals(expectedCalculation.shortfall, state.calculation?.shortfall ?: -1.0, 0.01)
        assertEquals(expectedCalculation.totalDueNow, state.calculation?.totalDueNow ?: -1.0, 0.01)
        assertEquals(250.0, state.currentInputAmount, 0.01)
        assertEquals(expectedPreview.shortfall, state.realtimeShortfall, 0.01)
        assertEquals(expectedPreview.overpayment, state.realtimeOverpayment, 0.01)
        assertEquals(expectedPreview.nextDueDate, state.nextDueDate)
    }

    @Test
    fun `loadPaymentContext cancels stale contribution observation when switching groups`() = runTest {
        val memberA = Member(id = "member-a", groupId = "group-a", joinedAt = "2024-01-01T00:00:00Z")
        val groupA = Group(id = "group-a", type = GroupType.STOKVEL, monthlyContribution = 100.0, paymentDueDay = 1)
        val memberB = Member(id = "member-b", groupId = "group-b", joinedAt = "2024-01-01T00:00:00Z")
        val groupB = Group(id = "group-b", type = GroupType.STOKVEL, monthlyContribution = 300.0, paymentDueDay = 1)
        val contributionA = Contribution(id = "A", memberId = "member-a", groupId = "group-a", amount = 100.0)
        val contributionB = Contribution(id = "B", memberId = "member-b", groupId = "group-b", amount = 300.0)

        coEvery { memberRepo.getMemberByUserId("user-1", "group-a") } returns Result.success(memberA)
        coEvery { groupRepo.getGroupById("group-a") } returns Result.success(groupA)
        every { memberRepo.getMemberContributions("member-a", "group-a") } returns flow {
            delay(500)
            emit(Result.success(listOf(contributionA)))
        }

        coEvery { memberRepo.getMemberByUserId("user-1", "group-b") } returns Result.success(memberB)
        coEvery { groupRepo.getGroupById("group-b") } returns Result.success(groupB)
        every { memberRepo.getMemberContributions("member-b", "group-b") } returns flowOf(Result.success(listOf(contributionB)))

        viewModel.loadPaymentContext("group-a")
        viewModel.loadPaymentContext("group-b")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("member-b", state.member?.id)
        assertEquals("group-b", state.group?.id)
        assertEquals(listOf("B"), state.contributions.mapNotNull { it.id })
    }

    @Test
    fun `processPayment blocks partial contribution when group disallows partial payments`() = runTest {
        val today = LocalDate.now()
        val member = Member(
            id = "member-1",
            groupId = "group-1",
            joinedAt = today.minusMonths(2).withDayOfMonth(1).toString() + "T00:00:00Z"
        )
        val group = Group(
            id = "group-1",
            type = GroupType.STOKVEL,
            monthlyContribution = 200.0,
            paymentDueDay = 1,
            allowPartialPayment = false,
            lateFee = 0.0
        )

        coEvery { memberRepo.getMemberByUserId("user-1", "group-1") } returns Result.success(member)
        coEvery { groupRepo.getGroupById("group-1") } returns Result.success(group)
        every { memberRepo.getMemberContributions("member-1", "group-1") } returns flowOf(Result.success(emptyList()))

        viewModel.loadPaymentContext("group-1")
        advanceUntilIdle()

        val minDue = viewModel.state.value.calculation?.totalDueNow ?: 0.0
        assertTrue(minDue > 0.0)

        viewModel.processPayment(
            type = "contribution",
            amount = minDue - 10.0,
            groupId = "group-1",
            cardNumber = "4242424242424242",
            expiry = "12/99",
            cvv = "123"
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error?.contains("does not allow partial payments") == true)
        coVerify(exactly = 0) { processPaymentUseCase(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `processPayment maps joining fee to use case and updates success state`() = runTest {
        val member = Member(id = "member-1", groupId = "group-1", joinedAt = "2024-01-01T00:00:00Z")
        val group = Group(id = "group-1", type = GroupType.STOKVEL, monthlyContribution = 200.0, paymentDueDay = 1)

        coEvery { memberRepo.getMemberByUserId("user-1", "group-1") } returns Result.success(member)
        coEvery { groupRepo.getGroupById("group-1") } returns Result.success(group)
        every { memberRepo.getMemberContributions("member-1", "group-1") } returns flowOf(Result.success(emptyList()))
        coEvery {
            processPaymentUseCase(
                PaymentType.JOINING_FEE,
                150.0,
                "group-1",
                any(),
                any(),
                any()
            )
        } returns Result.success("tx-123")

        viewModel.loadPaymentContext("group-1")
        advanceUntilIdle()

        viewModel.processPayment(
            type = "joining_fee",
            amount = 150.0,
            groupId = "group-1",
            cardNumber = "4242424242424242",
            expiry = "12/99",
            cvv = "123"
        )
        advanceTimeBy(2000)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isSuccess)
        assertEquals("tx-123", state.transactionId)
        coVerify {
            processPaymentUseCase(
                PaymentType.JOINING_FEE,
                150.0,
                "group-1",
                member,
                group,
                state.calculation
            )
        }
    }
}

