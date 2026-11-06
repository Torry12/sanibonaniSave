package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.MainDispatcherRule
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * The member portal shows "Syncing your portal…" while its hydration is out and nothing is on
 * screen, and only a snapshot package releases that. Two ways to never get one existed: the warm-up
 * keeps its cached contexts and returns when both membership reads fail, and a snapshot whose
 * pillars never finished hydrating maps to no package at all. Neither said anything, so the loader
 * ran forever over a book that was not coming and the one control that could have re-pulled it was
 * behind the spinner. The admin dashboard has a bounded verdict for this; the portal had none.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberPortalHydrationVerdictTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val groupId = "g1"
    private val group = Group(id = groupId, name = "LIGHT-Hybrid Savings Group")

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
    private val devSettings = mockk<DevSettingsService>(relaxed = true).apply {
        every { settingsChanged } returns MutableSharedFlow<Unit>()
    }

    private val contexts = MutableStateFlow<Map<String, CachedGroupContext>>(emptyMap())
    private val testDispatcher = mainDispatcherRule.dispatcher

    private fun createViewModel(): MemberOverviewViewModel = mainDispatcherRule.track(MemberOverviewViewModel(
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
    ))

    /** A portal that has rendered parks every live read; this batch is about the load, not the feeds. */
    private fun stubLiveReads() {
        every { groupRepo.observeGroup(groupId) } returns flow { awaitCancellation() }
        every { memberRepo.observeMemberById("m1") } returns flow { awaitCancellation() }
        every { memberRepo.getGroupMembers(groupId) } returns flow { awaitCancellation() }
        every { contributionRepo.observeMemberContributions("m1", groupId) } returns flow { awaitCancellation() }
        every { loanRepo.getMemberLoans("m1") } returns flow { awaitCancellation() }
        every { claimRepo.observeClaimsForMember("m1", groupId) } returns flow { awaitCancellation() }
        every { payoutRepo.observePayoutsForMember("m1") } returns flow { awaitCancellation() }
        every { ledgerRepo.observeMemberLedger("m1") } returns flow { awaitCancellation() }
    }

    private suspend fun TestScope.openPortal(viewModel: MemberOverviewViewModel) {
        viewModel.setActive(true)
        viewModel.selectGroup(groupId)
        // runCurrent, not advanceUntilIdle: opening the portal arms the 7s hydration bound, and
        // draining the queue advances virtual time past it, so every test below would start its
        // measurement from a portal that had already been told it timed out.
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

        every { supabaseRepo.currentUserId } returns "user_123"
        coEvery { supabaseRepo.getUserRole() } returns UserRole.MEMBER
        every { memberContextCacheService.contexts } returns contexts
        every { memberContextCacheService.unverifiedSections } returns MutableStateFlow(emptyMap())
        every { networkService.isOnline } returns flowOf(true)
        every { networkService.isAirplaneModeActive } returns flowOf(false)
        every { paymentCalculator.calculateExpectedMemberGroupPart(any(), any()) } returns Money.ZERO
        stubLiveReads()
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        unmockkAll()
    }

    @Test
    fun `a portal whose warm-up cannot answer says so instead of spinning`() = runTest {
        // Both membership reads failed, so the service kept the cached contexts and returned without
        // warming anything - by design. The portal had no snapshot to render and no bound to say so.
        val viewModel = createViewModel()

        openPortal(viewModel)

        assertTrue(viewModel.state.value.isLoading)

        advanceTimeBy(8_000L)
        runCurrent()

        val state = viewModel.state.value
        assertFalse("the loader has to stop for a read that is not coming", state.isLoading)
        assertNotNull("and the member has to be told the book never arrived", state.error)
    }

    @Test
    fun `a portal that answers inside the bound is never told it timed out`() = runTest {
        val viewModel = createViewModel()

        openPortal(viewModel)

        contexts.value = mapOf(
            groupId to CachedGroupContext(
                member = Member(id = "m1", groupId = groupId, userId = "user_123"),
                group = group,
                isFullyHydrated = true
            )
        )
        runCurrent()

        assertEquals(groupId, viewModel.state.value.group?.id)

        advanceTimeBy(8_000L)
        runCurrent()

        assertNull(
            "a portal that rendered must not be told its load timed out",
            viewModel.state.value.error
        )
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `a snapshot that never finishes hydrating is not left as a permanent loader`() = runTest {
        // The other stranding path: a context exists, but a pillar never answered, so it maps to no
        // package and the portal is left holding isLoading = true with data in the cache it cannot
        // show and no way to be told.
        val viewModel = createViewModel()

        openPortal(viewModel)

        contexts.value = mapOf(
            groupId to CachedGroupContext(
                member = Member(id = "m1", groupId = groupId, userId = "user_123"),
                group = group,
                isFullyHydrated = false
            )
        )
        runCurrent()

        assertTrue("a partial snapshot is not a rendered portal", viewModel.state.value.isLoading)

        advanceTimeBy(8_000L)
        runCurrent()

        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun `warm-up passes that keep arriving still give their verdict inside the bound`() = runTest {
        // Every sync pass stamps a newer lastUpdatedMillis onto the snapshot, so the collector sees a
        // new value and the partial branch armed the watchdog again. Arming cancelled the running
        // delay and restarted it, so passes arriving faster than the bound postponed the verdict
        // forever and the member stayed under "Syncing your portal…" over a book that had already
        // stopped coming.
        val viewModel = createViewModel()

        openPortal(viewModel)

        for (pass in 1..3) {
            advanceTimeBy(4_000L)
            contexts.value = mapOf(
                groupId to CachedGroupContext(
                    member = Member(id = "m1", groupId = groupId, userId = "user_123"),
                    group = group,
                    isFullyHydrated = false,
                    lastUpdatedMillis = pass * 1_000L
                )
            )
            runCurrent()
        }

        val state = viewModel.state.value
        assertFalse(
            "the wait is bounded from when it started, not from the last reminder of it",
            state.isLoading
        )
        assertNotNull("and the member has to be told the book never arrived", state.error)
    }

    @Test
    fun `leaving the portal cancels the hydration verdict`() = runTest {
        val viewModel = createViewModel()

        openPortal(viewModel)
        viewModel.setActive(false)

        advanceTimeBy(8_000L)
        runCurrent()

        assertNull(
            "a load nobody is looking at must not report a timeout to the next screen",
            viewModel.state.value.error
        )
    }

    @Test
    fun `the verdict that releases the loader leaves a Retry that re-pulls the book`() = runTest {
        coEvery { memberContextCacheService.refreshGroup("user_123", groupId) } returns
            Result.failure(IOException("offline"))
        val viewModel = createViewModel()

        openPortal(viewModel)
        advanceTimeBy(8_000L)
        runCurrent()

        viewModel.refreshData()
        advanceUntilIdle()

        coVerify(exactly = 1) { memberContextCacheService.refreshGroup("user_123", groupId) }
        assertTrue(
            "the refusal the member acts on has to still be the one on screen",
            viewModel.state.value.error != null
        )
    }

    @Test
    fun `a refresh that returns no package keeps the retry instead of a blank portal`() = runTest {
        coEvery { memberContextCacheService.refreshGroup("user_123", groupId) } returns Result.success(Unit)
        val viewModel = createViewModel()

        openPortal(viewModel)
        advanceTimeBy(8_000L)
        runCurrent()

        viewModel.refreshData()
        runCurrent()

        assertTrue("the new pull has to take the portal back under the loader", viewModel.state.value.isLoading)
        assertNull("the verdict being retried is no longer the one on screen", viewModel.state.value.error)

        advanceTimeBy(8_000L)
        runCurrent()

        val state = viewModel.state.value
        assertFalse("a refresh that came back with nothing to render has to stop waiting", state.isLoading)
        assertNotNull("and the Retry has to still be the way back to the book", state.error)
        assertNull(state.member)
    }

    @Test
    fun `a refresh that lands a package releases the loader`() = runTest {
        coEvery { memberContextCacheService.refreshGroup("user_123", groupId) } coAnswers {
            contexts.value = mapOf(
                groupId to CachedGroupContext(
                    member = Member(id = "m1", groupId = groupId, userId = "user_123"),
                    group = group,
                    isFullyHydrated = true
                )
            )
            Result.success(Unit)
        }
        val viewModel = createViewModel()

        openPortal(viewModel)
        advanceTimeBy(8_000L)
        runCurrent()

        viewModel.refreshData()
        runCurrent()

        assertEquals(groupId, viewModel.state.value.group?.id)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)

        advanceTimeBy(8_000L)
        runCurrent()

        assertNull(
            "a portal the refresh rendered must not be told that pull timed out",
            viewModel.state.value.error
        )
    }

    @Test
    fun `a refresh in flight is dropped when the member opens another group`() = runTest {
        val refresh = CompletableDeferred<Result<Unit>>()
        coEvery { memberContextCacheService.refreshGroup("user_123", groupId) } coAnswers { refresh.await() }
        val viewModel = createViewModel()

        openPortal(viewModel)
        viewModel.refreshData()
        runCurrent()

        viewModel.selectGroup("g2")
        runCurrent()
        refresh.complete(Result.failure(IOException("offline")))
        runCurrent()

        assertEquals("g2", viewModel.state.value.selectedGroupId)
        assertNull(
            "the group that was left owns that failure",
            viewModel.state.value.error
        )
    }

    @Test
    fun `leaving the portal drops a refresh that has not returned`() = runTest {
        val refresh = CompletableDeferred<Result<Unit>>()
        coEvery { memberContextCacheService.refreshGroup("user_123", groupId) } coAnswers { refresh.await() }
        val viewModel = createViewModel()

        openPortal(viewModel)
        viewModel.refreshData()
        runCurrent()

        viewModel.setActive(false)
        refresh.complete(Result.failure(IOException("offline")))
        advanceTimeBy(8_000L)
        runCurrent()

        assertNull(
            "a pull nobody is waiting on must not become the next screen's error",
            viewModel.state.value.error
        )
    }
}
