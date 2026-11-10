package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.MainDispatcherRule
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.*
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
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.features.member.presentation.MemberOverviewViewModel
import com.sanibonani.save.features.member.presentation.coordination.MemberDocumentCoordinator
import com.sanibonani.save.features.member.presentation.coordination.MemberLiveObservationCoordinator
import com.sanibonani.save.features.member.presentation.coordination.MemberMessagingCoordinator
import com.sanibonani.save.service.CachedGroupContext
import com.sanibonani.save.service.MemberGroupContextCacheService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * "Recent Activity" and "TOTAL SAVINGS" read different books: the header sums contributions and the
 * list shows the member's rows in `group_ledger`. A member whose only payment was a registration fee
 * therefore has R500 of savings and no group ledger entries at all - the fee is platform revenue and
 * never reaches the group's book. That is the correct answer, but the screen used to reach it through
 * a path that cannot tell an empty ledger from one the server never answered, and it restarted all
 * nine live reads on every warm-up pass. These tests pin the verdict, the log line and the churn.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberLedgerHydrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val groupId = "g1"
    private val member = Member(id = "m1", groupId = groupId)
    private val group = Group(id = groupId, name = "Light Stokvel")

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

    private lateinit var contexts: MutableStateFlow<Map<String, CachedGroupContext>>
    private lateinit var staleSections: MutableStateFlow<Map<String, Set<String>>>
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

    private fun breadcrumbWith(fragment: String): Boolean =
        AppLogger.getBreadcrumbs().any { it.contains(fragment) }

    /**
     * Every sibling read parked so only the ledger branch decides, and each collection of the ledger
     * flow counted - a restarted observation shows up as a second read, not as a second emission.
     */
    private var ledgerReads = 0

    private fun stubSiblingsAndLedger(entries: List<LedgerEntry>) {
        every { groupRepo.observeGroup(groupId) } returns flow { awaitCancellation() }
        every { memberRepo.observeMemberById("m1") } returns flow { awaitCancellation() }
        every { contributionRepo.observeMemberContributions("m1", groupId) } returns
            flow { awaitCancellation() }
        every { loanRepo.getMemberLoans("m1") } returns flow { awaitCancellation() }
        every { claimRepo.observeClaimsForMember("m1", groupId) } returns flow { awaitCancellation() }
        every { payoutRepo.observePayoutsForMember("m1") } returns flow { awaitCancellation() }
        every { memberRepo.getGroupMembers(groupId) } returns flow { awaitCancellation() }
        every { ledgerRepo.observeMemberLedger("m1") } returns flow {
            ledgerReads++
            emit(Result.success(entries))
            awaitCancellation()
        }
    }

    private fun hydrate() {
        contexts.value = mapOf(
            groupId to CachedGroupContext(
                member = member,
                group = group,
                calculation = PaymentCalculation.EMPTY,
                isFullyHydrated = true,
                lastUpdatedMillis = 123L
            )
        )
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
        contexts = MutableStateFlow(emptyMap())
        every { memberContextCacheService.contexts } returns contexts
        staleSections = MutableStateFlow(emptyMap())
        every { memberContextCacheService.unverifiedSections } returns staleSections
        every { networkService.isOnline } returns flowOf(true)
        every { networkService.isAirplaneModeActive } returns flowOf(false)
        every { paymentCalculator.calculateExpectedMemberGroupPart(any(), any()) } returns Money.ZERO
        coEvery { supabaseRepo.getUserRole() } returns UserRole.GROUP_ADMIN

        ledgerReads = 0
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        unmockkAll()
    }

    @Test
    fun `an empty ledger before the read answers is reported as unconfirmed, not as no transactions`() = runTest {
        stubSiblingsAndLedger(emptyList())
        val viewModel = createViewModel()
        viewModel.selectGroup(groupId)
        advanceUntilIdle()
        viewModel.setActive(true)

        assertTrue(
            "nothing has read the ledger yet, so the screen may not claim the member has no entries",
            viewModel.state.value.memberActivityIsUnconfirmed
        )
    }

    @Test
    fun `an empty ledger the server answered is a real answer`() = runTest {
        stubSiblingsAndLedger(emptyList())
        val viewModel = createViewModel()
        viewModel.selectGroup(groupId)
        advanceUntilIdle()
        viewModel.setActive(true)
        hydrate()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.memberLedger.isEmpty())
        assertTrue(
            "a member whose only payment was a platform fee legitimately has no group entries",
            viewModel.state.value.memberLedgerAnswered
        )
        assertFalse(viewModel.state.value.memberActivityIsUnconfirmed)
    }

    @Test
    fun `a stale treasury pillar keeps the empty ledger unconfirmed`() = runTest {
        stubSiblingsAndLedger(emptyList())
        val viewModel = createViewModel()
        viewModel.selectGroup(groupId)
        advanceUntilIdle()
        viewModel.setActive(true)
        hydrate()
        advanceUntilIdle()

        staleSections.value = mapOf(groupId to setOf("treasury"))
        advanceUntilIdle()

        assertTrue(
            "the pillar that fills this book did not land, so an empty list proves nothing",
            viewModel.state.value.memberActivityIsUnconfirmed
        )
    }

    @Test
    fun `the member ledger read leaves a breadcrumb naming the member and the count`() = runTest {
        stubSiblingsAndLedger(listOf(LedgerEntry(id = "e1", groupId = groupId, memberId = "m1")))
        val viewModel = createViewModel()
        viewModel.selectGroup(groupId)
        advanceUntilIdle()
        viewModel.setActive(true)
        hydrate()
        advanceUntilIdle()

        assertTrue(
            "the read has to be tellable from one nobody attempted",
            breadcrumbWith("member ledger read attempted for m1")
        )
        assertTrue(
            "the count is what separates an answered entry from a stale cache",
            breadcrumbWith("member ledger read answered with 1 entry for m1")
        )
    }

    @Test
    fun `a warm-up pass that changes nothing does not restart the live reads`() = runTest {
        stubSiblingsAndLedger(emptyList())
        val viewModel = createViewModel()
        viewModel.selectGroup(groupId)
        advanceUntilIdle()
        viewModel.setActive(true)
        hydrate()
        advanceUntilIdle()
        assertEquals(1, ledgerReads)

        // The cache re-emits the same snapshot with a newer timestamp on every pass; that is not new
        // data, and re-running all nine reads for it doubled every pull in a Support Mode session.
        val unchangedSnapshot = contexts.value.getValue(groupId).copy(lastUpdatedMillis = 456L)
        contexts.value = mapOf(groupId to unchangedSnapshot)
        advanceUntilIdle()

        assertEquals("the ledger read restarted for an unchanged snapshot", 1, ledgerReads)
        assertEquals(
            "the timestamp is still recorded for the sync-time line",
            456L,
            viewModel.state.value.cacheLastSyncByGroup[groupId]
        )
    }

    @Test
    fun `switching to another member starts a read for that member`() = runTest {
        val otherMemberId = "m2"
        val otherGroupId = "g2"
        stubSiblingsAndLedger(emptyList())
        every { groupRepo.observeGroup(otherGroupId) } returns flow { awaitCancellation() }
        every { memberRepo.observeMemberById(otherMemberId) } returns flow { awaitCancellation() }
        every { contributionRepo.observeMemberContributions(otherMemberId, otherGroupId) } returns
            flow { awaitCancellation() }
        every { loanRepo.getMemberLoans(otherMemberId) } returns flow { awaitCancellation() }
        every { claimRepo.observeClaimsForMember(otherMemberId, otherGroupId) } returns
            flow { awaitCancellation() }
        every { payoutRepo.observePayoutsForMember(otherMemberId) } returns flow { awaitCancellation() }
        every { memberRepo.getGroupMembers(otherGroupId) } returns flow { awaitCancellation() }
        every { ledgerRepo.observeMemberLedger(otherMemberId) } returns flow {
            ledgerReads++
            emit(Result.success(emptyList()))
            awaitCancellation()
        }

        val viewModel = createViewModel()
        viewModel.selectGroup(groupId)
        advanceUntilIdle()
        viewModel.setActive(true)
        hydrate()
        advanceUntilIdle()
        assertEquals(1, ledgerReads)

        viewModel.selectGroup(otherGroupId)
        advanceUntilIdle()

        assertFalse(
            "the previous member's verdict has to clear with their rows",
            viewModel.state.value.memberLedgerAnswered
        )

        contexts.value = mapOf(
            otherGroupId to CachedGroupContext(
                member = Member(id = otherMemberId, groupId = otherGroupId),
                group = Group(id = otherGroupId, name = "Another group"),
                calculation = PaymentCalculation.EMPTY,
                isFullyHydrated = true,
                lastUpdatedMillis = 1L
            )
        )
        advanceUntilIdle()

        assertEquals(
            "a different subject is new data; the guard must not hold the old member's book open",
            2,
            ledgerReads
        )
        assertTrue(
            "the new member's read answers for the member now on screen",
            viewModel.state.value.memberLedgerAnswered
        )
    }
}
