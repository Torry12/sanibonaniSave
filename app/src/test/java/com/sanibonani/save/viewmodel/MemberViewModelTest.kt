package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.data.remote.GeoapifyService
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.service.CachedGroupContext
import com.sanibonani.save.service.MemberGroupContextCacheService
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemberViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val supabaseRepo = mockk<SupabaseRepository>()
    private val memberRepo = mockk<MemberRepository>()
    private val groupRepo = mockk<GroupRepository>()
    private val beneficiaryRepo = mockk<BeneficiaryRepository>()
    private val notificationRepo = mockk<NotificationRepository>()
    private val exportRepo = mockk<ExportRepository>()
    private val loanRepo = mockk<LoanRepository>()
    private val claimRepo = mockk<BeneficiaryClaimRepository>(relaxed = true)
    private val memberDocumentRepo = mockk<MemberDocumentRepository>(relaxed = true)
    private val credentialsRepo = mockk<CredentialsRepository>(relaxed = true)
    private val registerMemberUseCase = mockk<RegisterMemberUseCase>()
    private val sendNotificationUseCase = mockk<SendNotificationUseCase>()
    private val validateLoanEligibilityUseCase = mockk<ValidateLoanEligibilityUseCase>()
    private val validateBurialClaimEligibilityUseCase = mockk<ValidateBurialClaimEligibilityUseCase>()
    private val getGroupBusinessInsightsUseCase = mockk<com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase>(relaxed = true)
    private val geoapifyService = mockk<GeoapifyService>()
    private val contextCacheService = mockk<MemberGroupContextCacheService>()
    private val userProfileCacheService = mockk<com.sanibonani.save.service.UserProfileCacheService>(relaxed = true)

    private lateinit var viewModel: MemberViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        
        Dispatchers.setMain(testDispatcher)

        // Basic stubs to prevent initialization crashes
        every { supabaseRepo.currentUserId } returns "user_123"
        every { supabaseRepo.currentSession } returns null
        coEvery { supabaseRepo.getUserRole() } returns UserRole.MEMBER
        
        every { memberRepo.observeMemberships(any()) } returns flowOf(Result.success(emptyList()))
        every { memberRepo.getGroupMembers(any()) } returns flowOf(Result.success(emptyList()))
        every { claimRepo.observeClaimsForMember(any(), any()) } returns flowOf(Result.success(emptyList()))
        every { memberDocumentRepo.observeMemberDocuments(any()) } returns flowOf(Result.success(emptyList()))
        every { contextCacheService.contexts } returns MutableStateFlow<Map<String, CachedGroupContext>>(emptyMap())
        every { contextCacheService.getContext(any()) } returns null
        every { contextCacheService.ensureUserSession(any()) } returns Unit
        every { contextCacheService.warmMembershipsInBackground(any(), any()) } returns Unit
        coEvery { validateLoanEligibilityUseCase(any(), any()) } returns ValidateLoanEligibilityUseCase.EligibilityResult.Eligible

        // Fix for MockKException: updateContext is called within flows
        val slot = slot<(CachedGroupContext) -> CachedGroupContext>()
        every { contextCacheService.updateContext(any(), capture(slot)) } answers {
            // Just invoke the lambda to simulate update
            val initial = CachedGroupContext()
            slot.captured(initial)
            Unit
        }

        viewModel = MemberViewModel(
            supabaseRepo, memberRepo, groupRepo, beneficiaryRepo, notificationRepo,
            exportRepo, loanRepo, claimRepo, memberDocumentRepo, credentialsRepo,
            registerMemberUseCase, sendNotificationUseCase,
            validateLoanEligibilityUseCase, validateBurialClaimEligibilityUseCase,
            getGroupBusinessInsightsUseCase, geoapifyService, contextCacheService,
            userProfileCacheService
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `switchGroup resets state and starts observation`() = runTest(testDispatcher.scheduler) {
        val groupId = "new_group_id"
        
        coEvery { memberRepo.getMemberByUserId(any(), groupId) } returns Result.success(mockk(relaxed = true))
        every { memberRepo.observeMemberByUserId(any(), groupId) } returns flowOf(Result.success(null))
        every { groupRepo.observeGroup(groupId) } returns flowOf(Result.success(null))
        every { notificationRepo.observeNotifications(groupId) } returns flowOf(Result.success(emptyList()))
        every { beneficiaryRepo.observeBeneficiaries(any()) } returns flowOf(Result.success(emptyList()))
        every { loanRepo.getMemberLoans(any()) } returns flowOf(Result.success(emptyList()))

        viewModel.switchGroup(groupId)
        
        val state = viewModel.uiState.value
        assertEquals(groupId, state.currentGroupId)
        assertTrue(state.isLoading)
    }

    @Test
    fun `dashboard calculation identifies overdue status`() = runTest(testDispatcher.scheduler) {
        val groupId = "g1"
        val member = Member(id = "m1", groupId = groupId, userId = "user_123", status = MemberStatus.ACTIVE, joinedAt = "2026-01-01T00:00:00Z")
        val group = Group(id = groupId, name = "Test Group", monthlyContribution = 200.0, paymentDueDay = 1)
        val contributions = listOf(
            Contribution(id = "c1", memberId = "m1", groupId = groupId, amount = 200.0, status = ContributionStatus.PAID, dueDate = "2026-01-01")
        )

        coEvery { memberRepo.getMemberByUserId("user_123", groupId) } returns Result.success(member)
        every { memberRepo.observeMemberByUserId("user_123", groupId) } returns flowOf(Result.success(member))
        every { groupRepo.observeGroup(groupId) } returns flowOf(Result.success(group))
        every { memberRepo.getMemberContributions("m1", groupId) } returns flowOf(Result.success(contributions))
        every { notificationRepo.observeNotifications(groupId) } returns flowOf(Result.success(emptyList()))
        every { beneficiaryRepo.observeBeneficiaries("m1") } returns flowOf(Result.success(emptyList()))
        every { loanRepo.getMemberLoans("m1") } returns flowOf(Result.success(emptyList()))

        viewModel.startRealtimeObservation(groupId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("m1", state.member?.id)
        assertNotNull(state.calculation)
        assertTrue("Member should be flagged as having shortfall or overdue", (state.calculation?.shortfall ?: 0.0) > 0 || (state.calculation?.isOverdue ?: false))
    }

    @Test
    fun `requestLoan handles ineligibility`() = runTest(testDispatcher.scheduler) {
        val groupId = "g1"
        val member = Member(id = "m1", groupId = groupId)
        val group = Group(id = groupId)
        
        coEvery { memberRepo.getMemberByUserId(any(), groupId) } returns Result.success(member)
        every { memberRepo.observeMemberByUserId(any(), groupId) } returns flowOf(Result.success(member))
        every { groupRepo.observeGroup(groupId) } returns flowOf(Result.success(group))
        every { memberRepo.getMemberContributions(any(), any()) } returns flowOf(Result.success(emptyList()))
        every { notificationRepo.observeNotifications(groupId) } returns flowOf(Result.success(emptyList()))
        every { beneficiaryRepo.observeBeneficiaries(any()) } returns flowOf(Result.success(emptyList()))
        every { loanRepo.getMemberLoans(any()) } returns flowOf(Result.success(emptyList()))

        viewModel.startRealtimeObservation(groupId)
        advanceUntilIdle()

        coEvery { validateLoanEligibilityUseCase(any(), any()) } returns 
            ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible("Member must be joined for at least 6 months")

        viewModel.requestLoan(1000.0, 6, "Medical")
        advanceUntilIdle()

        assertEquals("Member must be joined for at least 6 months", viewModel.uiState.value.error)
        coVerify(exactly = 0) { loanRepo.requestLoan(any()) }
    }
}
