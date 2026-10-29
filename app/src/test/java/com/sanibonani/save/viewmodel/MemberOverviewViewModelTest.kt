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
import com.sanibonani.save.domain.usecase.group.GetGroupByIdUseCase
import com.sanibonani.save.domain.usecase.group.ObserveGroupDetailUseCase
import com.sanibonani.save.domain.usecase.auth.BuildDocumentAuthHeadersUseCase
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.auth.GetCurrentUserRoleUseCase
import com.sanibonani.save.domain.usecase.credit.ObserveMemberLoansUseCase
import com.sanibonani.save.domain.usecase.equity.ObserveMemberContributionsUseCase
import com.sanibonani.save.domain.usecase.member.ObserveGroupMembersUseCase
import com.sanibonani.save.domain.usecase.member.ObserveMemberByIdUseCase
import com.sanibonani.save.domain.usecase.member.ObserveMembershipsUseCase
import com.sanibonani.save.domain.usecase.treasury.ObserveMemberLedgerUseCase
import com.sanibonani.save.domain.usecase.admin.GenerateMemberAuditReportUseCase
import com.sanibonani.save.domain.usecase.system.BuildGroupLedgerStatementUseCase
import com.sanibonani.save.domain.usecase.system.ExportGroupConstitutionUseCase
import com.sanibonani.save.domain.usecase.system.ExportLedgerStatementUseCase
import com.sanibonani.save.domain.usecase.system.ExportMembershipContractUseCase
import com.sanibonani.save.domain.usecase.system.SendNotificationUseCase
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel
import com.sanibonani.save.features.member.presentation.coordination.MemberDocumentCoordinator
import com.sanibonani.save.features.member.presentation.coordination.MemberLiveObservationCoordinator
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
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.GroupFinancialInsight
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.service.CachedGroupContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow

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
    private lateinit var contexts: MutableStateFlow<Map<String, CachedGroupContext>>
    private val testDispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<MemberOverviewViewModel>()

    private fun createViewModel(): MemberOverviewViewModel = MemberOverviewViewModel(
        memberContextCacheService,
        ObserveMembershipsUseCase(memberRepo),
        GetGroupByIdUseCase(groupRepo),
        GetAttributableActorIdUseCase(supabaseRepo),
        GetCurrentUserRoleUseCase(supabaseRepo),
        BuildDocumentAuthHeadersUseCase(supabaseRepo),
        paymentCalculator,
        MemberMessagingCoordinator(SendNotificationUseCase(notifRepo)),
        MemberDocumentCoordinator(
            GenerateMemberAuditReportUseCase(auditRepo),
            ExportMembershipContractUseCase(exportRepo),
            ExportGroupConstitutionUseCase(exportRepo),
            ExportLedgerStatementUseCase(exportRepo),
            BuildGroupLedgerStatementUseCase(ledgerRepo, exportRepo)
        ),
        MemberLiveObservationCoordinator(
            ObserveGroupMembersUseCase(memberRepo),
            ObserveMemberContributionsUseCase(contributionRepo),
            ObserveGroupDetailUseCase(groupRepo),
            ObserveMemberLedgerUseCase(ledgerRepo),
            ObserveMemberLoansUseCase(loanRepo),
            ObserveMemberByIdUseCase(memberRepo),
            getGroupBusinessInsightsUseCase
        ),
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
        contexts = MutableStateFlow(emptyMap())
        every { memberContextCacheService.contexts } returns contexts
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
    fun `refreshData performs a full cache refresh for the selected group`() = runTest {
        viewModel.selectGroup("test_group")
        advanceUntilIdle()
        coEvery { memberContextCacheService.refreshGroup("user_123", "test_group") } returns
            Result.success(Unit)

        viewModel.refreshData()
        advanceUntilIdle()

        coVerify(exactly = 1) { memberContextCacheService.refreshGroup("user_123", "test_group") }
        assertFalse(viewModel.state.value.isLoading)
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

    @Test
    fun `active portal hydrates cache then streams live data until deactivated`() = runTest {
        val groupId = "g1"
        val member = Member(id = "m1", groupId = groupId, fullName = "Cached member")
        val group = Group(id = groupId, name = "Cached group")
        val liveMember = member.copy(fullName = "Live member")
        val liveGroup = group.copy(name = "Live group")
        val contribution = Contribution(id = "c1", memberId = "m1", groupId = groupId)
        val ledgerEntry = LedgerEntry(id = "entry1", groupId = groupId, memberId = "m1")
        val insight = GroupFinancialInsight(groupId = groupId)
        val liveGroupObservationStopped = MutableStateFlow(false)

        every { groupRepo.observeGroup(groupId) } returns flow {
            try {
                emit(Result.success(liveGroup))
                awaitCancellation()
            } finally {
                liveGroupObservationStopped.value = true
            }
        }
        every { memberRepo.observeMemberById("m1") } returns flowOf(Result.success(liveMember))
        every { contributionRepo.observeMemberContributions("m1", groupId) } returns
            flowOf(Result.success(listOf(contribution)))
        every { loanRepo.getMemberLoans("m1") } returns flowOf(Result.success(emptyList()))
        every { ledgerRepo.observeMemberLedger("m1") } returns flowOf(Result.success(listOf(ledgerEntry)))
        every { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(listOf(member)))
        every { getGroupBusinessInsightsUseCase(any(), any()) } returns Result.success(insight)
        every { paymentCalculator.calculateExpectedMemberGroupPart(any(), any()) } returns Money.ZERO

        viewModel.selectGroup(groupId)
        advanceUntilIdle()
        viewModel.setActive(true)
        contexts.value = mapOf(
            groupId to CachedGroupContext(
                member = member,
                group = group,
                calculation = PaymentCalculation.EMPTY,
                isFullyHydrated = true,
                lastUpdatedMillis = 123L
            )
        )
        advanceUntilIdle()

        assertEquals(groupId, viewModel.state.value.selectedGroupId)
        assertEquals("Live member", viewModel.state.value.member?.fullName)
        assertEquals("Live group", viewModel.state.value.group?.name)
        assertEquals(listOf(contribution), viewModel.state.value.contributions)
        assertEquals(listOf(ledgerEntry), viewModel.state.value.memberLedger)
        assertEquals(insight, viewModel.state.value.businessInsight?.getOrNull())
        assertEquals(123L, viewModel.state.value.cacheLastSyncByGroup[groupId])
        assertFalse(viewModel.state.value.isLoading)

        viewModel.setActive(false)
        runCurrent()
        assertTrue(liveGroupObservationStopped.value)
    }

    @Test
    fun `support portal renders identity verified context when some cached sections failed`() = runTest {
        val groupId = "g-support"
        val member = Member(id = "m-support", groupId = groupId, userId = "operator", fullName = "Selected member")
        val group = Group(id = groupId, name = "Support group")
        val partialContext = CachedGroupContext(
            member = member,
            group = group,
            isFullyHydrated = false
        )
        coEvery { supabaseRepo.getUserRole() } returns UserRole.PLATFORM_ADMIN
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberContextCacheService.impersonateMember(member.id!!, groupId) } coAnswers {
            contexts.value = mapOf(groupId to partialContext)
            Result.success(partialContext)
        }
        every { paymentCalculator.calculateExpectedMemberGroupPart(group, member) } returns Money.ZERO
        every { groupRepo.observeGroup(groupId) } returns flow { awaitCancellation() }
        every { memberRepo.observeMemberById(member.id!!) } returns flow { awaitCancellation() }
        every { contributionRepo.observeMemberContributions(member.id!!, groupId) } returns flow { awaitCancellation() }
        every { loanRepo.getMemberLoans(member.id!!) } returns flow { awaitCancellation() }
        every { ledgerRepo.observeMemberLedger(member.id!!) } returns flow { awaitCancellation() }
        every { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(listOf(member)))
        every { getGroupBusinessInsightsUseCase(any(), any()) } returns Result.success(GroupFinancialInsight(groupId = groupId))

        viewModel.setPortalContext(member.id, groupId)
        advanceUntilIdle()
        viewModel.setActive(true)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isImpersonating)
        assertEquals(member.id, viewModel.state.value.member?.id)
        assertEquals(groupId, viewModel.state.value.group?.id)
        assertFalse(viewModel.state.value.isLoading)
    }
}
