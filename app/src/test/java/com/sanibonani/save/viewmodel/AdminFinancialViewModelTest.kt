package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.RequestPayoutUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminFinancialViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val notifRepo = mockk<NotificationRepository>(relaxed = true)
    private val payoutRepo = mockk<PayoutRepository>(relaxed = true)
    private val actuarialRepo = mockk<ActuarialRepository>(relaxed = true)
    private val ledgerRepo = mockk<LedgerRepository>(relaxed = true)
    private val loanRepo = mockk<LoanRepository>(relaxed = true)
    private val claimRepo = mockk<BeneficiaryClaimRepository>(relaxed = true)
    private val requestPayoutUseCase = mockk<RequestPayoutUseCase>(relaxed = true)

    private val adminContextCacheService = AdminGroupContextCacheService(groupRepo, memberRepo, notifRepo, payoutRepo, actuarialRepo)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submitPayoutRequest calls use case and updates state`() = runTest {
        val groupId = "group-1"
        adminContextCacheService.selectGroup(groupId)

        coEvery { requestPayoutUseCase(groupId = groupId, memberId = null, requestedAmount = Money(1500.0)) } returns Result.success("payout-1")

        val vm = AdminFinancialViewModel(
            adminContextCacheService,
            ledgerRepo,
            payoutRepo,
            loanRepo,
            claimRepo,
            requestPayoutUseCase
        )

        vm.updatePayoutAmount("1500")
        vm.submitPayoutRequest()

        advanceUntilIdle()

        coVerify(exactly = 1) { requestPayoutUseCase(groupId = groupId, memberId = null, requestedAmount = Money(1500.0)) }
        assertEquals(true, vm.state.value.payoutRequestSuccess)
    }

    @Test
    fun `approveAndEscalatePayoutRequest updates status via repository`() = runTest {
        val payoutId = "payout-1"
        coEvery { payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.GROUP_APPROVED) } returns Result.success(Unit)

        val vm = AdminFinancialViewModel(
            adminContextCacheService,
            ledgerRepo,
            payoutRepo,
            loanRepo,
            claimRepo,
            requestPayoutUseCase
        )

        vm.approveAndEscalatePayoutRequest(payoutId)
        advanceUntilIdle()

        coVerify(exactly = 1) { payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.GROUP_APPROVED) }
        assertEquals("Request escalated for platform approval", vm.state.value.successMessage)
    }
}
