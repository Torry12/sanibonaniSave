package com.sanibonani.save.whatsapp

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.viewmodel.AdminViewModel
import io.github.jan.supabase.auth.user.UserSession
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelWhatsAppTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private val supabaseRepo           = mockk<SupabaseRepository>(relaxed = true)
    private val groupRepo              = mockk<GroupRepository>(relaxed = true)
    private val memberRepo             = mockk<MemberRepository>(relaxed = true)
    private val beneficiaryRepo        = mockk<BeneficiaryRepository>(relaxed = true)
    private val memberDocumentRepo     = mockk<MemberDocumentRepository>(relaxed = true)
    private val actuarialRepo          = mockk<ActuarialRepository>(relaxed = true)
    private val notifRepo              = mockk<NotificationRepository>(relaxed = true)
    private val paymentRepo            = mockk<PaymentRepository>(relaxed = true)
    private val payoutRepo             = mockk<PayoutRepository>(relaxed = true)
    private val exportRepo             = mockk<ExportRepository>(relaxed = true)
    private val loanRepo               = mockk<LoanRepository>(relaxed = true)
    private val claimRepo              = mockk<BeneficiaryClaimRepository>(relaxed = true)
    private val adminContextCache      = mockk<AdminGroupContextCacheService>(relaxed = true)
    
    private val verifyMemberDocument   = mockk<VerifyMemberDocumentUseCase>(relaxed = true)
    private val updateGroupSettings    = mockk<UpdateGroupSettingsUseCase>(relaxed = true)
    private val applyViabilityPlan     = mockk<ApplyViabilityPlanUseCase>(relaxed = true)
    private val verifyRelationalDoc    = mockk<VerifyRelationalDocumentUseCase>(relaxed = true)
    private val getManagedGroups       = mockk<GetManagedGroupsUseCase>(relaxed = true)
    private val calculateViability     = mockk<CalculateViabilityUseCase>(relaxed = true)
    private val updateMemberStatus     = mockk<UpdateMemberStatusUseCase>(relaxed = true)
    private val sendNotification       = mockk<SendNotificationUseCase>(relaxed = true)
    private val requestPayout          = mockk<RequestPayoutUseCase>(relaxed = true)
    private val validateLoanEligibility = mockk<ValidateLoanEligibilityUseCase>(relaxed = true)
    private val generateLoanContract   = mockk<GenerateLoanContractUseCase>(relaxed = true)
    private val getGroupBusinessInsights = mockk<GetGroupBusinessInsightsUseCase>(relaxed = true)
    private val calculateGroupHealthScore = mockk<CalculateGroupHealthScoreUseCase>(relaxed = true)
    private val healthScoreRepo        = mockk<HealthScoreRepository>(relaxed = true)
    private val ledgerRepo             = mockk<LedgerRepository>(relaxed = true)

    private lateinit var viewModel: AdminViewModel

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)

        Dispatchers.setMain(testDispatcher)

        every { supabaseRepo.currentUserId } returns "admin_user"
        every { supabaseRepo.sessionFlow } returns flowOf(mockk(relaxed = true))
        coEvery { supabaseRepo.getUserRole() } returns UserRole.GROUP_ADMIN

        // All flow-returning methods must be stubbed
        coEvery { getManagedGroups(any(), any()) } returns Result.success(emptyList())
        every { getManagedGroups.observeManagedGroups(any(), any()) } returns flowOf(Result.success(emptyList()))
        coEvery { actuarialRepo.computeMetrics(any()) } returns Result.success(ActuarialMetrics())
        coEvery { getGroupBusinessInsights(any(), any()) } returns GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Empty
        coEvery { calculateGroupHealthScore(any()) } returns Result.success(mockk(relaxed = true))

        every { groupRepo.observeGroup(any()) } returns flowOf(Result.success(null))
        every { groupRepo.observeGroupFeeStatus(any()) } returns flowOf(AdminFeeState.PAID)
        every { memberRepo.getGroupMembers(any()) } returns flowOf(Result.success(emptyList()))
        every { memberRepo.getGroupContributions(any()) } returns flowOf(Result.success(emptyList()))
        every { memberRepo.getMemberContributions(any(), any()) } returns flowOf(Result.success(emptyList()))
        every { notifRepo.observeNotifications(any()) } returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts(any()) } returns flowOf(Result.success(emptyList()))
        every { loanRepo.getGroupLoans(any()) } returns flowOf(Result.success(emptyList()))
        every { claimRepo.observeClaimsForGroup(any()) } returns flowOf(Result.success(emptyList()))
        every { ledgerRepo.observeGroupLedger(any()) } returns flowOf(Result.success(emptyList()))
        every { healthScoreRepo.observeGroupHealthScore(any()) } returns flowOf(Result.failure(NoSuchElementException()))
        every { beneficiaryRepo.observeBeneficiaries(any()) } returns flowOf(Result.success(emptyList()))
        every { memberDocumentRepo.observeMemberDocuments(any()) } returns flowOf(Result.success(emptyList()))

        viewModel = AdminViewModel(
            supabaseRepo, groupRepo, memberRepo, beneficiaryRepo, memberDocumentRepo,
            actuarialRepo, notifRepo, paymentRepo, payoutRepo, exportRepo, loanRepo,
            claimRepo, adminContextCache, verifyMemberDocument, updateGroupSettings,
            applyViabilityPlan, verifyRelationalDoc, getManagedGroups, calculateViability,
            updateMemberStatus, sendNotification, requestPayout, validateLoanEligibility,
            generateLoanContract, getGroupBusinessInsights, calculateGroupHealthScore,
            healthScoreRepo, ledgerRepo
        )
        viewModel.setActive(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun TestScope.injectMemberAndGroup() {
        val groupId = "g1"
        val group = Group(id = groupId, name = "Test Group")
        every { groupRepo.observeGroup(groupId) } returns flowOf(Result.success(group))
        
        viewModel.selectGroup(groupId)
        advanceUntilIdle()
        viewModel.selectMember(Member(id = "m1", groupId = groupId, fullName = "Alice"))
        advanceUntilIdle()
    }

    @Test
    fun `sendWhatsAppTestToSelectedMember success sets whatsAppTestResult`() = runTest {
        injectMemberAndGroup()
        coEvery { sendNotification(any(), any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel.sendWhatsAppTestToSelectedMember()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.whatsAppTestResult)
        assertNull(viewModel.state.value.error)
    }
}
