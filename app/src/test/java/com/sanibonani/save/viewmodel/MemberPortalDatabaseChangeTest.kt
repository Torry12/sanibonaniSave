package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.admin.AuditRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.ExportRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.service.DevSettingsService
import com.sanibonani.save.domain.service.NetworkConnectivityService
import com.sanibonani.save.domain.usecase.admin.GenerateMemberAuditReportUseCase
import com.sanibonani.save.domain.usecase.auth.BuildDocumentAuthHeadersUseCase
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.auth.GetCurrentUserRoleUseCase
import com.sanibonani.save.domain.usecase.credit.ObserveMemberLoansUseCase
import com.sanibonani.save.domain.usecase.disbursement.ObserveMemberPayoutsUseCase
import com.sanibonani.save.domain.usecase.equity.ObserveMemberContributionsUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupByIdUseCase
import com.sanibonani.save.domain.usecase.group.ObserveGroupDetailUseCase
import com.sanibonani.save.domain.usecase.member.ObserveGroupMembersUseCase
import com.sanibonani.save.domain.usecase.member.ObserveMemberByIdUseCase
import com.sanibonani.save.domain.usecase.member.ObserveMemberClaimsUseCase
import com.sanibonani.save.domain.usecase.member.ObserveMembershipsUseCase
import com.sanibonani.save.domain.usecase.system.BuildGroupLedgerStatementUseCase
import com.sanibonani.save.domain.usecase.system.ExportGroupConstitutionUseCase
import com.sanibonani.save.domain.usecase.system.ExportLedgerStatementUseCase
import com.sanibonani.save.domain.usecase.system.ExportMembershipContractUseCase
import com.sanibonani.save.domain.usecase.system.SendNotificationUseCase
import com.sanibonani.save.domain.usecase.treasury.ObserveMemberLedgerUseCase
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel
import com.sanibonani.save.features.member.presentation.coordination.MemberDocumentCoordinator
import com.sanibonani.save.features.member.presentation.coordination.MemberLiveObservationCoordinator
import com.sanibonani.save.features.member.presentation.coordination.MemberMessagingCoordinator
import com.sanibonani.save.service.CachedGroupContext
import com.sanibonani.save.service.MemberGroupContextCacheService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * A developer database switch replaces both the Supabase client and the Room file, so every row the
 * member portal is holding - and every live read streaming it - belongs to a tier that is gone.
 * `AdminOverviewViewModel` already answers that change by dropping its cache and re-warming; the
 * portal had no collector at all, so it kept rendering the closed database's rows, and its nine live
 * flows stayed subscribed to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberPortalDatabaseChangeTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val groupId = "g1"
    private val group = Group(id = groupId, name = "LIGHT-Hybrid Savings Group")
    private val membership = Member(id = "m1", groupId = groupId, userId = "user_123")

    private val memberContextCacheService = mockk<MemberGroupContextCacheService>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val contributionRepo = mockk<ContributionRepository>(relaxed = true)
    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val ledgerRepo = mockk<LedgerRepository>(relaxed = true)
    private val exportRepo = mockk<ExportRepository>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val loanRepo = mockk<LoanRepository>(relaxed = true)
    private val claimRepo = mockk<BeneficiaryClaimRepository>(relaxed = true)
    private val payoutRepo = mockk<PayoutRepository>(relaxed = true)
    private val auditRepo = mockk<AuditRepository>(relaxed = true)
    private val notifRepo = mockk<NotificationRepository>(relaxed = true)
    private val getGroupBusinessInsightsUseCase = mockk<GetGroupBusinessInsightsUseCase>(relaxed = true)
    private val paymentCalculator = mockk<PaymentCalculator>(relaxed = true)
    private val networkService = mockk<NetworkConnectivityService>(relaxed = true)
    private val devSettings = mockk<DevSettingsService>(relaxed = true)

    private val settingsChangedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val contexts = MutableStateFlow<Map<String, CachedGroupContext>>(emptyMap())
    private val testDispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<MemberOverviewViewModel>()

    private val membershipSubscriptions = AtomicInteger()
    private val ledgerSubscriptions = AtomicInteger()

    /** Opening a portal already warms through two paths, so the change is judged by the count it adds. */
    private val warmUps = AtomicInteger()

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
            ObserveMemberClaimsUseCase(claimRepo),
            ObserveMemberPayoutsUseCase(payoutRepo),
            getGroupBusinessInsightsUseCase
        ),
        devSettings,
        networkService
    ).also { createdViewModels += it }

    /** The memberships read the portal re-subscribes to, counting every new collector. */
    private fun stubMemberships() {
        every { memberRepo.observeMemberships("user_123") } returns flow {
            membershipSubscriptions.incrementAndGet()
            emit(Result.success(listOf(membership)))
            awaitCancellation()
        }
    }

    private fun stubLiveReads() {
        every { groupRepo.observeGroup(groupId) } returns flow { awaitCancellation() }
        every { memberRepo.observeMemberById("m1") } returns flow { awaitCancellation() }
        every { memberRepo.getGroupMembers(groupId) } returns flow { awaitCancellation() }
        every { contributionRepo.observeMemberContributions("m1", groupId) } returns
            flow { awaitCancellation() }
        every { loanRepo.getMemberLoans("m1") } returns flow { awaitCancellation() }
        every { claimRepo.observeClaimsForMember("m1", groupId) } returns flow { awaitCancellation() }
        every { payoutRepo.observePayoutsForMember("m1") } returns flow { awaitCancellation() }
        every { ledgerRepo.observeMemberLedger("m1") } returns flow {
            ledgerSubscriptions.incrementAndGet()
            awaitCancellation()
        }
    }

    private fun hydratedSnapshot() = mapOf(
        groupId to CachedGroupContext(
            member = membership,
            group = group,
            isFullyHydrated = true
        )
    )

    private suspend fun TestScope.openPortal(viewModel: MemberOverviewViewModel) {
        viewModel.setActive(true)
        viewModel.selectGroup(groupId)
        runCurrent()
    }

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk<FirebaseCrashlytics>(relaxed = true)

        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        Dispatchers.setMain(testDispatcher)

        every { supabaseRepo.currentUserId } returns "user_123"
        coEvery { supabaseRepo.getUserRole() } returns UserRole.MEMBER
        every { memberContextCacheService.contexts } returns contexts
        every { memberContextCacheService.unverifiedSections } returns MutableStateFlow(emptyMap())
        // The service dropping its snapshots is what lets the portal's own re-render happen, so the
        // stand-in has to empty the same map the screen reads.
        every { memberContextCacheService.invalidateForDatabaseChange() } answers {
            contexts.value = emptyMap()
        }
        every { networkService.isOnline } returns flowOf(true)
        every { networkService.isAirplaneModeActive } returns flowOf(false)
        every { paymentCalculator.calculateExpectedMemberGroupPart(any(), any()) } returns Money.ZERO
        every { devSettings.settingsChanged } returns settingsChangedFlow
        coEvery { memberContextCacheService.warmUpForUser(any()) } coAnswers {
            warmUps.incrementAndGet()
            Unit
        }
        stubMemberships()
        stubLiveReads()
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `a tier change drops the rows read from the replaced database`() = runTest {
        val viewModel = createViewModel()
        openPortal(viewModel)
        contexts.value = hydratedSnapshot()
        runCurrent()
        assertEquals(groupId, viewModel.state.value.group?.id)

        val warmupsBeforeChange = warmUps.get()
        settingsChangedFlow.emit(Unit)
        runCurrent()

        verify(exactly = 1) { memberContextCacheService.invalidateForDatabaseChange() }
        val state = viewModel.state.value
        assertNull("a member row from the closed tier is not a fallback", state.member)
        assertEquals("the group stays the group the member was looking at", groupId, state.selectedGroupId)
        assertTrue("and the refill is a load in progress, not a blank screen", state.isLoading)
        assertEquals(
            "the warm-up has to be asked of the tier now in front, not of the cache just emptied",
            warmupsBeforeChange + 1,
            warmUps.get()
        )
        assertEquals(2, membershipSubscriptions.get())
    }

    @Test
    fun `a tier change during Support Mode re-enters the same member`() = runTest {
        coEvery { supabaseRepo.getUserRole() } returns UserRole.PLATFORM_ADMIN
        coEvery { memberContextCacheService.impersonateMember("m1", groupId) } returns
            Result.success(CachedGroupContext(member = membership, group = group, isFullyHydrated = true))

        val viewModel = createViewModel()
        viewModel.setActive(true)
        viewModel.setPortalContext("m1", groupId)
        runCurrent()
        assertTrue(viewModel.state.value.isImpersonating)

        settingsChangedFlow.emit(Unit)
        runCurrent()

        coVerify(exactly = 2) { memberContextCacheService.impersonateMember("m1", groupId) }
        val state = viewModel.state.value
        assertTrue("the operator is still in someone else's book", state.isImpersonating)
        assertEquals("m1", state.targetMemberId)
        assertEquals(groupId, state.selectedGroupId)
    }

    @Test
    fun `a tier change with no portal open restarts the membership read alone`() = runTest {
        // Nothing is selected, so there is no group to re-warm - but the memberships themselves were
        // read from the tier that has just been replaced, and re-collecting them is what picks the
        // group that exists on the new one.
        every { memberRepo.observeMemberships("user_123") } returns flow {
            membershipSubscriptions.incrementAndGet()
            emit(Result.success(emptyList()))
            awaitCancellation()
        }
        val viewModel = createViewModel()
        viewModel.setActive(true)
        runCurrent()
        val warmupsBeforeChange = warmUps.get()

        settingsChangedFlow.emit(Unit)
        runCurrent()

        verify(exactly = 1) { memberContextCacheService.invalidateForDatabaseChange() }
        assertEquals(2, membershipSubscriptions.get())
        assertEquals(
            "no group on screen means no book to re-hydrate",
            warmupsBeforeChange,
            warmUps.get()
        )
        assertFalse("a portal nobody opened must not sit on a loader", viewModel.state.value.isLoading)
    }

    @Test
    fun `the live feeds rebind to the database that replaced the one they were reading`() = runTest {
        val viewModel = createViewModel()
        openPortal(viewModel)
        contexts.value = hydratedSnapshot()
        runCurrent()
        assertEquals(1, ledgerSubscriptions.get())

        settingsChangedFlow.emit(Unit)
        runCurrent()
        contexts.value = hydratedSnapshot()
        runCurrent()

        assertEquals(
            "a feed subscribed to a closed Room instance never delivers the new tier's rows",
            2,
            ledgerSubscriptions.get()
        )
        assertEquals(groupId, viewModel.state.value.group?.id)
    }
}
