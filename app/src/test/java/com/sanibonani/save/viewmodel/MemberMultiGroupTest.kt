package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.data.remote.GeoapifyService
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.service.UserProfileCacheService
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
class MemberMultiGroupTest {

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
    private val userProfileCacheService = mockk<UserProfileCacheService>(relaxed = true)

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

        every { supabaseRepo.currentUserId } returns "user_1"
        every { supabaseRepo.currentSession } returns null
        coEvery { supabaseRepo.getUserRole() } returns UserRole.MEMBER
        
        // Mock cache service
        every { contextCacheService.contexts } returns MutableStateFlow(emptyMap())
        every { contextCacheService.getContext(any()) } returns null
        every { contextCacheService.ensureUserSession(any()) } returns Unit
        every { contextCacheService.warmMembershipsInBackground(any(), any()) } returns Unit
        every { contextCacheService.updateContext(any(), any()) } returns Unit
        every { memberRepo.getGroupMembers(any()) } returns flowOf(Result.success(emptyList()))
        every { claimRepo.observeClaimsForMember(any(), any()) } returns flowOf(Result.success(emptyList()))
        every { memberDocumentRepo.observeMemberDocuments(any()) } returns flowOf(Result.success(emptyList()))
        coEvery { validateLoanEligibilityUseCase(any(), any()) } returns ValidateLoanEligibilityUseCase.EligibilityResult.Eligible

        // Default memberships (2 groups)
        val m1 = Member(id = "m1", groupId = "g1", userId = "user_1", status = MemberStatus.ACTIVE, joinedAt = "2024-01-01T00:00:00Z")
        val m2 = Member(id = "m2", groupId = "g2", userId = "user_1", status = MemberStatus.ACTIVE, joinedAt = "2024-02-01T00:00:00Z")
        every { memberRepo.observeMemberships("user_1") } returns flowOf(Result.success(listOf(m1, m2)))

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
    fun `switching between groups isolates state correctly`() = runTest(testDispatcher.scheduler) {
        val g1 = Group(id = "g1", name = "Group 1", monthlyContribution = 100.0)
        val g2 = Group(id = "g2", name = "Group 2", monthlyContribution = 200.0)
        val m1 = Member(id = "m1", groupId = "g1", userId = "user_1", status = MemberStatus.ACTIVE)
        val m2 = Member(id = "m2", groupId = "g2", userId = "user_1", status = MemberStatus.ACTIVE)

        // Setup G1 mocks
        coEvery { memberRepo.getMemberByUserId("user_1", "g1") } returns Result.success(m1)
        every { memberRepo.observeMemberByUserId("user_1", "g1") } returns flowOf(Result.success(m1))
        every { groupRepo.observeGroup("g1") } returns flowOf(Result.success(g1))
        every { memberRepo.getMemberContributions("m1", "g1") } returns flowOf(Result.success(emptyList()))
        every { notificationRepo.observeNotifications("g1") } returns flowOf(Result.success(emptyList()))
        every { beneficiaryRepo.observeBeneficiaries("m1") } returns flowOf(Result.success(emptyList()))
        every { loanRepo.getMemberLoans("m1") } returns flowOf(Result.success(emptyList()))

        // Setup G2 mocks
        coEvery { memberRepo.getMemberByUserId("user_1", "g2") } returns Result.success(m2)
        every { memberRepo.observeMemberByUserId("user_1", "g2") } returns flowOf(Result.success(m2))
        every { groupRepo.observeGroup("g2") } returns flowOf(Result.success(g2))
        every { memberRepo.getMemberContributions("m2", "g2") } returns flowOf(Result.success(emptyList()))
        every { notificationRepo.observeNotifications("g2") } returns flowOf(Result.success(emptyList()))
        every { beneficiaryRepo.observeBeneficiaries("m2") } returns flowOf(Result.success(emptyList()))
        every { loanRepo.getMemberLoans("m2") } returns flowOf(Result.success(emptyList()))

        // 1. Load initial data (defaults to G2 as it's more recent in setup)
        advanceUntilIdle()
        assertEquals("g2", viewModel.uiState.value.currentGroupId)
        assertEquals("Group 2", viewModel.uiState.value.group?.name)

        // 2. Switch to G1
        viewModel.switchGroup("g1")
        advanceUntilIdle()
        
        assertEquals("g1", viewModel.uiState.value.currentGroupId)
        assertEquals("Group 1", viewModel.uiState.value.group?.name)
        assertEquals("m1", viewModel.uiState.value.member?.id)

        // 3. Switch back to G2
        viewModel.switchGroup("g2")
        advanceUntilIdle()
        
        assertEquals("g2", viewModel.uiState.value.currentGroupId)
        assertEquals("Group 2", viewModel.uiState.value.group?.name)
        assertEquals("m2", viewModel.uiState.value.member?.id)
    }
}
