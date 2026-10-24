package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.data.sync.RealtimeSyncManager
import com.sanibonani.save.data.utils.DevSettings
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.ExportRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.service.DataVerificationService
import com.sanibonani.save.domain.service.NetworkConnectivityService
import com.sanibonani.save.domain.service.PlatformFeeService
import com.sanibonani.save.domain.usecase.actuarial.ApplyViabilityPlanUseCase
import com.sanibonani.save.domain.usecase.actuarial.CalculateViabilityUseCase
import com.sanibonani.save.domain.usecase.disbursement.ExecuteRoscaPayoutUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.usecase.group.GetManagedGroupsUseCase
import com.sanibonani.save.domain.usecase.payment.ProcessGroupPlatformFeePaymentUseCase
import com.sanibonani.save.domain.usecase.system.SendNotificationUseCase
import com.sanibonani.save.features.admin.presentation.AdminOverviewViewModel
import com.sanibonani.save.features.admin.presentation.coordination.AdminGroupOperationsCoordinator
import com.sanibonani.save.features.admin.presentation.coordination.AdminMessagingCoordinator
import com.sanibonani.save.features.admin.presentation.coordination.AdminPlatformFeeCoordinator
import com.sanibonani.save.features.admin.presentation.coordination.AdminReportingCoordinator
import com.sanibonani.save.features.admin.presentation.coordination.AdminViabilityCoordinator
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.CachedAdminGroupContext
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Characterization tests for [AdminOverviewViewModel].
 *
 * These pin the observable behaviour of the 20-dependency orchestrator before its
 * collaborators are extracted, so the extraction can be proven behaviour-preserving.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminOverviewViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val adminContextCacheService = mockk<AdminGroupContextCacheService>(relaxed = true)
    private val getManagedGroupsUseCase = mockk<GetManagedGroupsUseCase>(relaxed = true)
    private val auditRepo = mockk<AuditRepository>(relaxed = true)
    private val getGroupBusinessInsightsUseCase = mockk<GetGroupBusinessInsightsUseCase>(relaxed = true)
    private val healthScoreRepo = mockk<HealthScoreRepository>(relaxed = true)
    private val calculateViabilityUseCase = mockk<CalculateViabilityUseCase>(relaxed = true)
    private val applyViabilityPlanUseCase = mockk<ApplyViabilityPlanUseCase>(relaxed = true)
    private val sendNotificationUseCase = mockk<SendNotificationUseCase>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val ledgerRepo = mockk<LedgerRepository>(relaxed = true)
    private val exportRepo = mockk<ExportRepository>(relaxed = true)
    private val verificationService = mockk<DataVerificationService>(relaxed = true)
    private val executeRoscaPayoutUseCase = mockk<ExecuteRoscaPayoutUseCase>(relaxed = true)
    private val processPlatformFeePaymentUseCase = mockk<ProcessGroupPlatformFeePaymentUseCase>(relaxed = true)
    private val platformRepo = mockk<PlatformRepository>(relaxed = true)
    private val financialRepo = mockk<GroupFinancialRepository>(relaxed = true)
    private val devSettings = mockk<DevSettings>(relaxed = true)
    private val syncManager = mockk<RealtimeSyncManager>(relaxed = true)
    private val feeService = mockk<PlatformFeeService>(relaxed = true)
    private val networkService = mockk<NetworkConnectivityService>(relaxed = true)

    private val contextsFlow = MutableStateFlow<Map<String, CachedAdminGroupContext>>(emptyMap())
    private val selectedGroupIdFlow = MutableStateFlow<String?>(null)

    private lateinit var viewModel: AdminOverviewViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        Dispatchers.setMain(testDispatcher)

        every { adminContextCacheService.contexts } returns contextsFlow
        every { adminContextCacheService.selectedGroupId } returns selectedGroupIdFlow
        every { supabaseRepo.currentUserId } returns "admin-1"
        coEvery { supabaseRepo.getUserRole() } returns UserRole.GROUP_ADMIN
        coEvery { platformRepo.getPlatformSettings() } returns Result.success(PlatformConfig())
        every { feeService.getRegistrationFee(any()) } returns Money("50.00")
        every { getManagedGroupsUseCase.observeManagedGroups(any(), any(), any()) } returns
            flowOf(Result.success(emptyList()))
        every { devSettings.settingsChanged } returns MutableSharedFlow()
        every { syncManager.syncMetrics } returns MutableStateFlow(emptyMap())
        every { networkService.isOnline } returns flowOf(true)
        every { networkService.isAirplaneModeActive } returns flowOf(false)
        every { healthScoreRepo.observeGroupHealthScore(any()) } returns
            flowOf(Result.failure(Exception("no score")))
        every { ledgerRepo.observeGroupLedger(any()) } returns flowOf(Result.success(emptyList()))
        coEvery { verificationService.verifyGroupAdminData(any(), any(), any()) } returns VerificationResult.Valid
        coEvery { getGroupBusinessInsightsUseCase(any(), any()) } returns Result.success(GroupFinancialInsight())
        coEvery { financialRepo.getOutstandingPlatformFees(any()) } returns Result.success(emptyList())
        coEvery { memberRepo.syncGroupMembers(any()) } returns Result.success(emptyList())
        coEvery { auditRepo.triggerGroupAudit(any()) } returns Result.failure(Exception("no report"))

        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        // Cancel the ViewModel scope while Main is still set, otherwise a coroutine on
        // Dispatchers.Default resumes after resetMain() and fails the *next* test.
        viewModel.viewModelScope.cancel()
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun buildViewModel() = AdminOverviewViewModel(
        adminContextCacheService,
        getManagedGroupsUseCase,
        getGroupBusinessInsightsUseCase,
        healthScoreRepo,
        supabaseRepo,
        ledgerRepo,
        devSettings,
        syncManager,
        AdminViabilityCoordinator(calculateViabilityUseCase, applyViabilityPlanUseCase),
        AdminMessagingCoordinator(sendNotificationUseCase),
        AdminPlatformFeeCoordinator(
            platformRepo,
            financialRepo,
            processPlatformFeePaymentUseCase,
            feeService,
            adminContextCacheService
        ),
        AdminReportingCoordinator(auditRepo, ledgerRepo, exportRepo, verificationService),
        AdminGroupOperationsCoordinator(
            memberRepo,
            ledgerRepo,
            executeRoscaPayoutUseCase,
            adminContextCacheService
        ),
        networkService
    )

    private suspend fun TestScope.hydrateGroup(groupId: String = "g-1") {
        selectedGroupIdFlow.value = groupId
        contextsFlow.value = mapOf(
            groupId to CachedAdminGroupContext(
                group = Group(id = groupId, name = "Test Group"),
                members = emptyList(),
                metrics = ActuarialMetrics(),
                feeStatus = AdminFeeState.DUE,
                isFullyHydrated = true
            )
        )
        advanceUntilIdle()
    }

    @Test
    fun `init exposes current user and registration fee`() = runTest {
        advanceUntilIdle()

        assertEquals("admin-1", viewModel.state.value.currentUserId)
        assertEquals("50.00", viewModel.state.value.platformRegistrationFee.toPlainString())
        assertEquals(true, viewModel.state.value.isSystemOnline)
    }

    @Test
    fun `setSupportMode toggles the support flag`() = runTest {
        viewModel.setSupportMode(true)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isSupportMode)

        viewModel.setSupportMode(false)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSupportMode)
    }

    @Test
    fun `dismissImpersonationBanner sets the dismissed flag`() = runTest {
        viewModel.dismissImpersonationBanner()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isImpersonationBannerDismissed)
    }

    @Test
    fun `selectGroup resets context and hands the selection to the cache`() = runTest {
        viewModel.selectGroup("g-1")
        advanceUntilIdle()

        assertNull(viewModel.state.value.group)
        assertTrue(viewModel.state.value.isLoading)
        verify { adminContextCacheService.selectGroup("g-1") }
        verify { adminContextCacheService.warmUpGroup("g-1") }
    }

    @Test
    fun `hydrated context populates group and members`() = runTest {
        hydrateGroup()

        assertEquals("g-1", viewModel.state.value.group?.id)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `calculateViability is a no-op without a group`() = runTest {
        viewModel.calculateViability("36000.00", "12")
        advanceUntilIdle()

        coVerify(exactly = 0) { calculateViabilityUseCase(any(), any(), any(), any()) }
    }

    @Test
    fun `calculateViability forwards a valid goal and period`() = runTest {
        hydrateGroup()
        coEvery { calculateViabilityUseCase(any(), any(), any(), any()) } returns
            Result.failure(Exception("no plan"))

        viewModel.calculateViability("36000.00", "12")
        advanceUntilIdle()

        coVerify { calculateViabilityUseCase("g-1", Money("36000.00"), 12, any()) }
    }

    @Test
    fun `calculateViability falls back to the group defaults for blank input`() = runTest {
        hydrateGroup()
        coEvery { calculateViabilityUseCase(any(), any(), any(), any()) } returns
            Result.failure(Exception("no plan"))

        viewModel.calculateViability("", "")
        advanceUntilIdle()

        coVerify { calculateViabilityUseCase("g-1", any(), 12, any()) }
    }

    @Test
    fun `broadcastMessage ignores blank text`() = runTest {
        hydrateGroup()

        viewModel.broadcastMessage()
        advanceUntilIdle()

        coVerify(exactly = 0) { sendNotificationUseCase(any(), any(), any(), any(), any()) }
        assertEquals(false, viewModel.state.value.isSendingMessage)
    }

    @Test
    fun `broadcastMessage sends and clears the draft`() = runTest {
        hydrateGroup()
        coEvery { sendNotificationUseCase(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel.updateMessageText("Meeting at 18:00")
        viewModel.broadcastMessage()
        advanceUntilIdle()

        coVerify { sendNotificationUseCase("g-1", "Meeting at 18:00", null, any(), any()) }
        assertTrue(viewModel.state.value.messageSentSuccess)
        assertEquals("", viewModel.state.value.messageText)
        assertEquals(false, viewModel.state.value.isSendingMessage)
    }

    @Test
    fun `payPlatformFees reports success and refreshes the context`() = runTest {
        hydrateGroup()
        coEvery { processPlatformFeePaymentUseCase("g-1") } returns Result.success(Unit)

        viewModel.payPlatformFees()
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertTrue(event is UIEvent.ShowMessage)
        assertEquals("Platform fees paid successfully.", (event as UIEvent.ShowMessage).message)
        assertEquals(false, viewModel.state.value.isPayingPlatformFee)
        verify { adminContextCacheService.warmUpGroup("g-1", force = true) }
    }

    @Test
    fun `executeRoscaPayout surfaces the use case message`() = runTest {
        hydrateGroup()
        coEvery { executeRoscaPayoutUseCase("g-1") } returns Result.success("R500.00 to Thabo")

        viewModel.executeRoscaPayout()
        advanceUntilIdle()

        val event = viewModel.events.first()
        assertTrue(event is UIEvent.ShowMessage)
        assertEquals("ROSCA Payout Processed: R500.00 to Thabo", (event as UIEvent.ShowMessage).message)
    }

    @Test
    fun `setTab and clearError update their state slices`() = runTest {
        viewModel.setTab(3)
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.selectedTab)

        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }
}
