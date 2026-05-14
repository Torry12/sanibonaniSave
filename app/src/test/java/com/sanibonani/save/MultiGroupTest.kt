package com.sanibonani.save

import android.util.Log
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.viewmodel.AdminViewModel
import com.sanibonani.save.viewmodel.GroupFormEvent
import com.sanibonani.save.viewmodel.GroupViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.google.firebase.crashlytics.FirebaseCrashlytics

@OptIn(ExperimentalCoroutinesApi::class)
class MultiGroupTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val beneficiaryRepo = mockk<BeneficiaryRepository>(relaxed = true)
    private val memberDocumentRepo = mockk<MemberDocumentRepository>(relaxed = true)
    private val actuarialRepo = mockk<ActuarialRepository>(relaxed = true)
    private val notifRepo = mockk<NotificationRepository>(relaxed = true)
    private val paymentRepo = mockk<PaymentRepository>(relaxed = true)
    private val payoutRepo = mockk<PayoutRepository>(relaxed = true)
    private val exportRepo = mockk<ExportRepository>(relaxed = true)
    private val loanRepo = mockk<LoanRepository>(relaxed = true)
    private val claimRepo = mockk<BeneficiaryClaimRepository>(relaxed = true)

    private val getManagedGroupsUseCase = mockk<GetManagedGroupsUseCase>(relaxed = true)
    private val verifyMemberDocumentUseCase = mockk<VerifyMemberDocumentUseCase>(relaxed = true)
    private val updateGroupSettingsUseCase = mockk<UpdateGroupSettingsUseCase>(relaxed = true)
    private val applyViabilityPlanUseCase = mockk<ApplyViabilityPlanUseCase>(relaxed = true)
    private val verifyRelationalDocumentUseCase = mockk<VerifyRelationalDocumentUseCase>(relaxed = true)
    private val calculateViabilityUseCase = mockk<CalculateViabilityUseCase>(relaxed = true)
    private val updateMemberStatusUseCase = mockk<UpdateMemberStatusUseCase>(relaxed = true)
    private val sendNotificationUseCase = mockk<SendNotificationUseCase>(relaxed = true)
    private val requestPayoutUseCase = mockk<RequestPayoutUseCase>(relaxed = true)
    private val validateLoanEligibilityUseCase = mockk<ValidateLoanEligibilityUseCase>(relaxed = true)
    private val generateLoanContractUseCase = mockk<GenerateLoanContractUseCase>(relaxed = true)
    private val getGroupBusinessInsightsUseCase = mockk<GetGroupBusinessInsightsUseCase>(relaxed = true)
    private val ledgerRepo = mockk<LedgerRepository>(relaxed = true)

    private val adminContextCacheService by lazy {
        AdminGroupContextCacheService(
            groupRepo = groupRepo,
            memberRepo = memberRepo,
            notificationRepo = notifRepo,
            payoutRepo = payoutRepo,
            actuarialRepo = actuarialRepo
        )
    }

    private val createGroupUseCase = mockk<CreateGroupUseCase>(relaxed = true)
    private val getPublicGroupsUseCase = mockk<GetPublicGroupsUseCase>(relaxed = true)
    private val geoapifyService = mockk<com.sanibonani.save.data.remote.GeoapifyService>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

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

        val sessionFlow = MutableStateFlow<UserSession?>(null)
        every { supabaseRepo.sessionFlow } returns sessionFlow

        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
        coEvery { getManagedGroupsUseCase(userId) } returns Result.success(listOf(group1, group2))
        
        every { memberRepo.observeMemberships(userId) } returns flowOf(Result.success(listOf(membership1, membership2)))
        coEvery { groupRepo.getGroupById("group-1") } returns Result.success(group1)
        coEvery { groupRepo.getGroupById("group-2") } returns Result.success(group2)

        // Ensure all required flows emit at least one value for both groups
        every { memberRepo.getGroupContributions("group-1") } returns flowOf(Result.success(emptyList()))
        every { memberRepo.getGroupContributions("group-2") } returns flowOf(Result.success(emptyList()))
        every { memberRepo.getGroupMembers("group-1") } returns flowOf(Result.success(emptyList()))
        every { memberRepo.getGroupMembers("group-2") } returns flowOf(Result.success(emptyList()))
        every { groupRepo.observeGroup("group-1") } returns flowOf(Result.success(group1))
        every { groupRepo.observeGroup("group-2") } returns flowOf(Result.success(group2))
        every { notifRepo.observeNotifications("group-1") } returns flowOf(Result.success(emptyList()))
        every { notifRepo.observeNotifications("group-2") } returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts("group-1") } returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts("group-2") } returns flowOf(Result.success(emptyList()))
        every { groupRepo.observeGroupFeeStatus("group-1") } returns flowOf(AdminFeeState.DUE)
        every { groupRepo.observeGroupFeeStatus("group-2") } returns flowOf(AdminFeeState.DUE)

        coEvery { actuarialRepo.computeMetrics(any()) } returns Result.success(com.sanibonani.save.domain.model.ActuarialMetrics())

        // 1. Initialize AdminViewModel
        val sessionFlow = MutableStateFlow<UserSession?>(mockk(relaxed = true))
        every { supabaseRepo.sessionFlow } returns sessionFlow
        
        val adminViewModel = AdminViewModel(
            supabaseRepo, groupRepo, memberRepo, beneficiaryRepo, memberDocumentRepo,
            actuarialRepo, notifRepo, paymentRepo, payoutRepo, exportRepo, loanRepo,
            claimRepo,
            adminContextCacheService,
            verifyMemberDocumentUseCase,
            updateGroupSettingsUseCase,
            applyViabilityPlanUseCase,
            verifyRelationalDocumentUseCase,
            getManagedGroupsUseCase, calculateViabilityUseCase,
            updateMemberStatusUseCase, sendNotificationUseCase, requestPayoutUseCase,
            validateLoanEligibilityUseCase,
            generateLoanContractUseCase,
            getGroupBusinessInsightsUseCase,
            ledgerRepo
        )
        
        advanceUntilIdle()

        // 2. Verify both groups are in managedGroups
        assertEquals(2, adminViewModel.state.value.managedGroups.size)
        assertEquals("First Group", adminViewModel.state.value.group?.name)

        // 3. Switch to the second group
        adminViewModel.selectGroup("group-2")
        advanceUntilIdle()
        
        assertEquals("group-2", adminViewModel.state.value.currentGroupId)
        assertEquals("Second Group", adminViewModel.state.value.group?.name)
    }

    @Test
    fun `creating a second group as a logged in user uses current session`() = runTest {
        val userId = "user-123"
        every { supabaseRepo.currentUserId } returns userId
        coEvery { supabaseRepo.getUserRole() } returns UserRole.GROUP_ADMIN
        
        // Mock createGroup behavior for existing user
        coEvery { 
            createGroupUseCase(any(), any(), any(), any(), any(), any())
        } returns Result.success("new-group-id")
        coEvery { groupRepo.activateGroup(any(), any()) } returns Result.success(Unit)

        val groupViewModel = GroupViewModel(
            groupRepo,
            exportRepo,
            createGroupUseCase,
            getPublicGroupsUseCase,
            geoapifyService
        )
        
        // Simulate a logged-in user filling the form
        groupViewModel.onEvent(GroupFormEvent.NameChanged("New Managed Group"))
        groupViewModel.onEvent(GroupFormEvent.AdminEmailChanged("existing@test.com"))
        groupViewModel.onEvent(GroupFormEvent.AdminPasswordChanged("password123"))
        groupViewModel.onEvent(GroupFormEvent.AdminFullNameChanged("Admin Name"))
        groupViewModel.onEvent(GroupFormEvent.AdminIdNumberChanged("9001015000081"))

        // Execute registration
        groupViewModel.finalizeRegistrationAfterPayment("tx_123")
        advanceUntilIdle()

        assertTrue(groupViewModel.registerState.value.success)
        assertEquals("new-group-id", groupViewModel.registerState.value.createdGroupId)
    }
}
