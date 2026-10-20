package com.sanibonani.save

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.auth.*
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.system.*
import com.sanibonani.save.domain.usecase.actuarial.ApplyViabilityPlanUseCase
import com.sanibonani.save.domain.usecase.actuarial.CalculateViabilityUseCase
import com.sanibonani.save.domain.usecase.disbursement.ExecuteRoscaPayoutUseCase
import com.sanibonani.save.domain.usecase.payment.ProcessGroupPlatformFeePaymentUseCase
import com.sanibonani.save.domain.usecase.group.*
import com.sanibonani.save.domain.usecase.member.*
import com.sanibonani.save.domain.usecase.system.*
import com.sanibonani.save.domain.service.*
import com.sanibonani.save.domain.service.PlatformFeeService
import com.sanibonani.save.data.utils.DevSettings
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.CachedAdminGroupContext
import com.sanibonani.save.data.sync.RealtimeSyncManager
import com.sanibonani.save.features.admin.presentation.AdminOverviewViewModel
import com.sanibonani.save.features.group.presentation.GroupFormEvent
import com.sanibonani.save.features.group.presentation.GroupRegistrationViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import com.sanibonani.save.domain.model.AuthSession
import org.junit.Rule
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class MultiGroupTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val actuarialRepo = mockk<ActuarialRepository>(relaxed = true)
    private val notifRepo = mockk<NotificationRepository>(relaxed = true)
    private val payoutRepo = mockk<PayoutRepository>(relaxed = true)
    private val exportRepo = mockk<ExportRepository>(relaxed = true)
    private val ledgerRepo = mockk<LedgerRepository>(relaxed = true)
    private val auditRepo = mockk<AuditRepository>(relaxed = true)
    private val healthScoreRepo = mockk<HealthScoreRepository>(relaxed = true)

    private val getManagedGroupsUseCase = mockk<GetManagedGroupsUseCase>(relaxed = true)
    private val getGroupBusinessInsightsUseCase = mockk<GetGroupBusinessInsightsUseCase>(relaxed = true)
    private val calculateViabilityUseCase = mockk<CalculateViabilityUseCase>(relaxed = true)
    private val applyViabilityPlanUseCase = mockk<ApplyViabilityPlanUseCase>(relaxed = true)
    private val sendNotificationUseCase = mockk<SendNotificationUseCase>(relaxed = true)
    private val executeRoscaPayoutUseCase = mockk<ExecuteRoscaPayoutUseCase>(relaxed = true)
    private val processPlatformFeePaymentUseCase = mockk<ProcessGroupPlatformFeePaymentUseCase>(relaxed = true)
    private val platformRepo = mockk<PlatformRepository>(relaxed = true)
    private val financialRepo = mockk<GroupFinancialRepository>(relaxed = true)
    private val networkService = mockk<NetworkConnectivityService>(relaxed = true)
    private val verificationService = mockk<DataVerificationService>(relaxed = true)
    private val devSettings = mockk<DevSettings>(relaxed = true)
    private val syncManager = mockk<RealtimeSyncManager>(relaxed = true)
    private val feeService = mockk<PlatformFeeService>(relaxed = true)

    private val adminContextCacheService = mockk<AdminGroupContextCacheService>(relaxed = true)
    private val selectedGroupIdFlow = MutableStateFlow<String?>(null)
    private val contextsFlow = MutableStateFlow<Map<String, CachedAdminGroupContext>>(emptyMap())

    private val createGroupUseCase = mockk<CreateGroupUseCase>(relaxed = true)
    private val activateGroupUseCase = mockk<ActivateGroupUseCase>(relaxed = true)
    private val locationService = mockk<LocationService>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    // These ViewModels are constructed directly, so nothing ever calls onCleared() and their
    // viewModelScope coroutines outlive the test. They then resume on the shared Dispatchers.Main
    // test wrapper after the class finished and surface as UncaughtExceptionsBeforeTest in the
    // next test class. Parking them in a store makes tearDown cancel them.
    private val viewModelStore = ViewModelStore()
    private var trackedCount = 0

    private fun <T : androidx.lifecycle.ViewModel> tracked(viewModel: T): T {
        viewModelStore.put("vm-${trackedCount++}", viewModel)
        return viewModel
    }

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        // Mock Looper and Handler
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
            true
        }

        every { adminContextCacheService.selectedGroupId } returns selectedGroupIdFlow
        every { adminContextCacheService.contexts } returns contextsFlow
        every { adminContextCacheService.selectGroup(any()) } answers { selectedGroupIdFlow.value = it.invocation.args[0] as String }

        every { devSettings.settingsChanged } returns MutableSharedFlow()
        every { syncManager.syncMetrics } returns MutableStateFlow(emptyMap())
        every { networkService.isOnline } returns flowOf(true)
        every { networkService.isAirplaneModeActive } returns flowOf(false)

        val sessionFlow = MutableStateFlow<com.sanibonani.save.domain.model.AuthSession?>(null)
        every { supabaseRepo.sessionFlow } returns sessionFlow
        every { supabaseRepo.currentUserId } returns "user-123"

        every { feeService.getRegistrationFee() } returns Money("700.0")

        // Provide results for managed groups flow to avoid Loading state
        val mockGroups = listOf(
            Group(id = "group-1", name = "First Group"),
            Group(id = "group-2", name = "Second Group")
        )
        coEvery { getManagedGroupsUseCase(any(), any(), any()) } returns Result.success(mockGroups)
        every { getManagedGroupsUseCase.observeManagedGroups(any(), any(), any()) } returns flowOf(Result.success(mockGroups))

        Dispatchers.setMain(testDispatcher)

        // Avoid ClassCastException from relaxed mocks returning Result<List<T>>
        coEvery { financialRepo.getOutstandingPlatformFees(any()) } returns Result.success(emptyList())
        coEvery { platformRepo.getPlatformAnalytics() } returns Result.success(PlatformAnalytics())
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
        unmockkConstructor(Handler::class)
        unmockkAll()
    }

    @Test
    fun `existing admin can create a second group and switch between them`() = runTest {
        val userId = "user-123"
        val group1 = Group(id = "group-1", name = "First Group", adminUserId = userId)
        val group2 = Group(id = "group-2", name = "Second Group", adminUserId = userId)

        val membership1 = Member(groupId = "group-1", userId = userId, status = MemberStatus.ACTIVE)
        val membership2 = Member(groupId = "group-2", userId = userId, status = MemberStatus.ACTIVE)

        // Mock Supabase & Repos
        every { supabaseRepo.currentUserId } returns userId
        coEvery { getManagedGroupsUseCase(userId, any(), any()) } returns Result.success(listOf(group1, group2))

        every { memberRepo.observeMemberships(userId) } returns flowOf(Result.success(listOf(membership1, membership2)))
        coEvery { groupRepo.getGroupById("group-1") } returns Result.success(group1)
        coEvery { groupRepo.getGroupById("group-2") } returns Result.success(group2)

        // Ensure all required flows emit at least one value for both groups
        every { memberRepo.getGroupMembers(any()) } returns flowOf(Result.success(emptyList()))
        every { groupRepo.observeGroup("group-1") } returns flowOf(Result.success(group1))
        every { groupRepo.observeGroup("group-2") } returns flowOf(Result.success(group2))
        every { notifRepo.observeNotifications(any()) } returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts(any()) } returns flowOf(Result.success(emptyList()))
        every { groupRepo.observeGroupFeeStatus(any()) } returns flowOf(AdminFeeState.PAID)

        contextsFlow.value = mapOf(
            "group-1" to CachedAdminGroupContext(group = group1, isFullyHydrated = true),
            "group-2" to CachedAdminGroupContext(group = group2, isFullyHydrated = true)
        )
        selectedGroupIdFlow.value = "group-1"

        coEvery { actuarialRepo.computeMetrics(any()) } returns Result.success(ActuarialMetrics())

        // 1. Initialize AdminOverviewViewModel
        val sessionFlow = MutableStateFlow<com.sanibonani.save.domain.model.AuthSession?>(mockk(relaxed = true))
        every { supabaseRepo.sessionFlow } returns sessionFlow

        val adminViewModel = tracked(
            AdminOverviewViewModel(
                adminContextCacheService,
                getManagedGroupsUseCase,
                auditRepo,
                getGroupBusinessInsightsUseCase,
                healthScoreRepo,
                calculateViabilityUseCase,
                applyViabilityPlanUseCase,
                sendNotificationUseCase,
                supabaseRepo,
                memberRepo,
                ledgerRepo,
                exportRepo,
                verificationService,
                executeRoscaPayoutUseCase,
                processPlatformFeePaymentUseCase,
                platformRepo,
                financialRepo,
                devSettings,
                syncManager,
                feeService,
                networkService
            )
        )
        adminViewModel.setActive(true)

        advanceUntilIdle()

        // 2. Verify both groups are in managedGroups
        val managedResult = adminViewModel.state.value.managedGroups
        assertTrue(managedResult is AsyncResult.Success)
        assertEquals(2, (managedResult as AsyncResult.Success).data.size)
        assertEquals("First Group", adminViewModel.state.value.group?.name)

        // 3. Switch to the second group
        adminViewModel.selectGroup("group-2")
        // Ensure cache re-emits for the new ID
        contextsFlow.value = contextsFlow.value.toMap()
        advanceUntilIdle()

        // Group name should update after context switch and observation
        assertEquals("Second Group", adminViewModel.state.value.group?.name)
    }

    @Test
    fun `creating a second group as a logged in user uses current session`() = runTest {
        val userId = "user-123"
        every { supabaseRepo.currentUserId } returns userId
        coEvery { supabaseRepo.getUserRole() } returns UserRole.GROUP_ADMIN

        // Mock createGroup behavior for existing user
        coEvery {
            createGroupUseCase(
                group = any(),
                adminEmail = any(),
                adminPassword = any(),
                adminFullName = any(),
                adminPhone = any(),
                adminIdNumber = any(),
                adminStreet = any(),
                adminSuburb = any(),
                adminNotificationPref = any()
            )
        } returns Result.success("new-group-id")
        coEvery { activateGroupUseCase(any(), any()) } returns Result.success(Unit)

        val groupViewModel = tracked(
            GroupRegistrationViewModel(
                createGroupUseCase,
                activateGroupUseCase,
                feeService,
                locationService,
                supabaseRepo
            )
        )

        // Simulate a logged-in user filling the form
        groupViewModel.onEvent(GroupFormEvent.NameChanged("New Managed Group"))
        groupViewModel.onEvent(GroupFormEvent.AdminEmailChanged("existing@test.com"))
        groupViewModel.onEvent(GroupFormEvent.AdminPasswordChanged("password123"))
        groupViewModel.onEvent(GroupFormEvent.AdminFullNameChanged("Admin Name"))
        groupViewModel.onEvent(GroupFormEvent.AdminIdNumberChanged("9001015000085"))
        groupViewModel.onEvent(GroupFormEvent.AdminPhoneChanged("0712345678"))
        groupViewModel.onEvent(GroupFormEvent.TermsAcceptedToggled(true))

        // Submit group first
        groupViewModel.submitGroup()
        advanceUntilIdle()

        // Execute registration
        groupViewModel.finalizeRegistrationAfterPayment("tx_123")
        advanceUntilIdle()

        assertTrue(groupViewModel.state.value.success)
        assertEquals("new-group-id", groupViewModel.state.value.createdGroupId)
    }
}
