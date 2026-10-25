package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.auth.*
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.service.NetworkConnectivityService
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.domain.usecase.group.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel
import com.sanibonani.save.features.member.presentation.coordination.MemberDocumentCoordinator
import com.sanibonani.save.features.member.presentation.coordination.MemberMessagingCoordinator
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
class MemberOverviewViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val memberContextCacheService = mockk<MemberGroupContextCacheService>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val contributionRepo = mockk<ContributionRepository>(relaxed = true)
    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val ledgerRepo = mockk<LedgerRepository>(relaxed = true)
    private val exportRepo = mockk<ExportRepository>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val loanRepo = mockk<LoanRepository>(relaxed = true)
    private val auditRepo = mockk<AuditRepository>(relaxed = true)
    private val notifRepo = mockk<NotificationRepository>(relaxed = true)
    private val getGroupBusinessInsightsUseCase = mockk<GetGroupBusinessInsightsUseCase>(relaxed = true)
    private val paymentCalculator = mockk<PaymentCalculator>(relaxed = true)
    private val networkService = mockk<NetworkConnectivityService>(relaxed = true)

    private lateinit var viewModel: MemberOverviewViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<MemberOverviewViewModel>()

    private fun createViewModel(): MemberOverviewViewModel = MemberOverviewViewModel(
        memberContextCacheService,
        memberRepo,
        contributionRepo,
        groupRepo,
        ledgerRepo,
        supabaseRepo,
        loanRepo,
        getGroupBusinessInsightsUseCase,
        paymentCalculator,
        MemberMessagingCoordinator(notifRepo),
        MemberDocumentCoordinator(exportRepo, ledgerRepo, auditRepo),
        networkService
    ).also { createdViewModels += it }

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        // Mock Looper
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        Dispatchers.setMain(testDispatcher)

        every { supabaseRepo.currentUserId } returns "user_123"
        every { memberContextCacheService.contexts } returns MutableStateFlow(emptyMap())
        every { networkService.isOnline } returns flowOf(true)
        every { networkService.isAirplaneModeActive } returns flowOf(false)

        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        // Cancel every ViewModel scope while Main is still set, otherwise a coroutine on
        // Dispatchers.Default resumes after resetMain() and fails the *next* test.
        createdViewModels.forEach { it.viewModelScope.cancel() }
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `selectGroup updates state and warms up cache`() = runTest {
        val groupId = "test_group"

        viewModel.selectGroup(groupId)
        advanceUntilIdle()

        assertEquals(groupId, viewModel.state.value.selectedGroupId)
        coVerify { memberContextCacheService.warmUpForUser("user_123") }
    }

    @Test
    fun `loadMemberships automatically selects first group if none selected`() = runTest {
        val memberships = listOf(Member(id = "m1", groupId = "g1"))
        every { memberRepo.observeMemberships("user_123") } returns flowOf(Result.success<List<Member>>(memberships))

        // Re-creating to capture the every stub
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("g1", viewModel.state.value.selectedGroupId)
    }
}
