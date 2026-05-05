package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.ProcessPaymentUseCase
import com.sanibonani.save.data.validation.ValidationUtils
import com.sanibonani.save.data.validation.ValidationResult
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

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val processPaymentUseCase = mockk<ProcessPaymentUseCase>(relaxed = true)

    private lateinit var viewModel: PaymentViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        
        mockkObject(ValidationUtils)
        every { ValidationUtils.validatePaymentFields(any(), any(), any()) } returns ValidationResult.Valid

        Dispatchers.setMain(testDispatcher)

        every { supabaseRepo.currentUserId } returns "user_123"

        viewModel = PaymentViewModel(supabaseRepo, groupRepo, memberRepo, processPaymentUseCase)
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
        val group = Group(id = groupId, monthlyContribution = 200.0)
        
        coEvery { memberRepo.getMemberByUserId("user_123", groupId) } returns Result.success(member)
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(group)
        every { memberRepo.getMemberContributions("m1", groupId) } returns flowOf(Result.success(emptyList()))

        viewModel.loadPaymentContext(groupId)
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
        val member = Member(id = "m1", groupId = groupId, joinedAt = "2026-01-01T00:00:00Z", status = MemberStatus.ACTIVE)
        val group = Group(id = groupId, monthlyContribution = 200.0, paymentDueDay = 1)
        
        coEvery { memberRepo.getMemberByUserId("user_123", groupId) } returns Result.success(member)
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(group)
        every { memberRepo.getMemberContributions("m1", groupId) } returns flowOf(Result.success(emptyList()))

        viewModel.loadPaymentContext(groupId)
        advanceUntilIdle()

        // Before payment change
        val initialShortfall = viewModel.state.value.realtimeShortfall
        assertTrue("Initial shortfall should be > 0 (found $initialShortfall)", initialShortfall > 0)

        // Change amount to cover shortfall
        viewModel.onAmountChanged(initialShortfall)
        
        val updatedState = viewModel.state.value
        assertEquals(0.0, updatedState.realtimeShortfall, 0.01)
    }

    @Test
    fun `processPayment - success updates state to success`() = runTest {
        val groupId = "g1"
        val txId = "tx_123"
        
        coEvery { processPaymentUseCase.invoke(
            type = any(), 
            amount = any(), 
            groupId = any(), 
            member = any(), 
            group = any(), 
            calculation = any()
        ) } returns Result.success(txId)

        viewModel.processPayment(
            type = "contribution",
            amount = 500.0,
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
        coEvery { processPaymentUseCase.invoke(any(), any(), any(), any(), any(), any()) } returns Result.failure(Exception("Network error"))

        viewModel.processPayment(
            type = "contribution",
            amount = 500.0,
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
