package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.service.CachedGroupContext
import com.sanibonani.save.service.MemberGroupContextCacheService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The portal headline summed `members.total_paid`, and `fn_sync_single_member_financials` counts a
 * paid `registration_contribution` into that column - while `internal_post_contribution` books that
 * same row to `platform_ledger`, where there is no `member_id` at all. So a member whose only payment
 * was the joining fee was shown "TOTAL SAVINGS R500.00" next to an empty ledger: both reads correct,
 * the label a lie. These tests pin the split, measured against the Cloud rows of 2026-11-04.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberSavingsVsPlatformFeeTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val groupId = "g1"
    private val member = Member(id = "m1", groupId = groupId)
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
            ObserveMemberClaimsUseCase(claimRepo),
            ObserveMemberPayoutsUseCase(payoutRepo),
            getGroupBusinessInsightsUseCase
        ),
        networkService
    ).also { createdViewModels += it }

    private fun contribution(
        type: String,
        amount: String,
        status: ContributionStatus = ContributionStatus.PAID,
        id: String? = null,
        paidAt: String? = null
    ) = Contribution(
        id = id,
        memberId = "m1",
        groupId = groupId,
        amount = Money(amount),
        status = status,
        type = type,
        paidAt = paidAt
    )

    /** Only the contributions read decides the split here, so every other pull is parked. */
    private fun stubContributions(
        list: List<Contribution>,
        ledger: List<LedgerEntry> = emptyList()
    ) {
        every { groupRepo.observeGroup(groupId) } returns flow { awaitCancellation() }
        every { memberRepo.observeMemberById("m1") } returns flow { awaitCancellation() }
        every { contributionRepo.observeMemberContributions("m1", groupId) } returns flowOf(Result.success(list))
        every { loanRepo.getMemberLoans("m1") } returns flow { awaitCancellation() }
        every { claimRepo.observeClaimsForMember("m1", groupId) } returns flow { awaitCancellation() }
        every { payoutRepo.observePayoutsForMember("m1") } returns flow { awaitCancellation() }
        every { memberRepo.getGroupMembers(groupId) } returns flow { awaitCancellation() }
        every { ledgerRepo.observeMemberLedger("m1") } returns flowOf(Result.success(ledger))
    }

    private suspend fun TestScope.openPortal(totalPaid: Money): MemberUiState {
        val viewModel = createViewModel()
        viewModel.selectGroup(groupId)
        advanceUntilIdle()
        viewModel.setActive(true)
        contexts.value = mapOf(
            groupId to CachedGroupContext(
                member = member.copy(totalPaid = totalPaid),
                group = group,
                calculation = PaymentCalculation.EMPTY,
                isFullyHydrated = true,
                lastUpdatedMillis = 123L
            )
        )
        advanceUntilIdle()
        return viewModel.state.value
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
        contexts = MutableStateFlow(emptyMap())
        every { memberContextCacheService.contexts } returns contexts
        every { memberContextCacheService.unverifiedSections } returns MutableStateFlow(emptyMap())
        every { networkService.isOnline } returns flowOf(true)
        every { networkService.isAirplaneModeActive } returns flowOf(false)
        every { paymentCalculator.calculateExpectedMemberGroupPart(any(), any()) } returns Money.ZERO
        coEvery { supabaseRepo.getUserRole() } returns UserRole.GROUP_ADMIN
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `a member whose only payment was the registration fee has saved nothing in the group`() = runTest {
        stubContributions(listOf(contribution("registration_contribution", "500.00")))

        val state = openPortal(totalPaid = Money("500.00"))

        assertEquals(
            "the fee is platform revenue, so the headline may not call it savings",
            Money.ZERO,
            state.totalSavings
        )
        assertEquals(Money("500.00"), state.platformFeesPaid)
    }

    @Test
    fun `the cached total paid keeps its contribution rows and loses its fee`() = runTest {
        // The twelve monthly rows of Member 2 (Rosca), plus the R150 joining fee that was booked to
        // platform_ledger: members.total_paid reads R6 150, the group holds R6 000.
        stubContributions(
            (1..12).map { contribution("contribution", "500.00") } +
                contribution("registration_contribution", "150.00")
        )

        val state = openPortal(totalPaid = Money("6150.00"))

        assertEquals(Money("6000.00"), state.totalSavings)
        assertEquals(Money("150.00"), state.platformFeesPaid)
    }

    @Test
    fun `member fees are platform money too`() = runTest {
        stubContributions(
            listOf(
                contribution("contribution", "500.00"),
                contribution("member_fee", "20.00"),
                contribution("member_fee_ledger", "30.00")
            )
        )

        val state = openPortal(totalPaid = Money("550.00"))

        assertEquals(Money("500.00"), state.totalSavings)
        assertEquals(Money("50.00"), state.platformFeesPaid)
    }

    @Test
    fun `a joining fee stays in savings because the group ledger holds it`() = runTest {
        // Only the three types above are diverted to the platform; joining_fee still goes through
        // increment_group_balance, so that money really is in the group's book.
        stubContributions(listOf(contribution("joining_fee", "100.00")))

        val state = openPortal(totalPaid = Money("100.00"))

        assertEquals(Money("100.00"), state.totalSavings)
        assertEquals(Money.ZERO, state.platformFeesPaid)
    }

    @Test
    fun `an unpaid registration fee is not money paid anywhere`() = runTest {
        stubContributions(
            listOf(
                contribution("contribution", "500.00"),
                contribution("registration_contribution", "500.00", status = ContributionStatus.DUE)
            )
        )

        val state = openPortal(totalPaid = Money("500.00"))

        assertEquals(Money("500.00"), state.totalSavings)
        assertEquals(Money.ZERO, state.platformFeesPaid)
    }

    @Test
    fun `a contributions read that never answered does not zero the headline`() = runTest {
        every { groupRepo.observeGroup(groupId) } returns flow { awaitCancellation() }
        every { memberRepo.observeMemberById("m1") } returns flow { awaitCancellation() }
        every { contributionRepo.observeMemberContributions("m1", groupId) } returns flow { awaitCancellation() }
        every { loanRepo.getMemberLoans("m1") } returns flow { awaitCancellation() }
        every { claimRepo.observeClaimsForMember("m1", groupId) } returns flow { awaitCancellation() }
        every { payoutRepo.observePayoutsForMember("m1") } returns flow { awaitCancellation() }
        every { memberRepo.getGroupMembers(groupId) } returns flow { awaitCancellation() }
        every { ledgerRepo.observeMemberLedger("m1") } returns flow { awaitCancellation() }

        val state = openPortal(totalPaid = Money("6000.00"))

        assertEquals(
            "nothing has read the fee rows, so the cached figure stands - an unproven R0 is a new lie",
            Money("6000.00"),
            state.totalSavings
        )
        assertEquals(Money.ZERO, state.platformFeesPaid)
    }

    // The transaction list read only group_ledger rows keyed to the member - and the Room query
    // filters is_platform rows out as well - while a registration/member fee is booked to
    // platform_ledger, which has no member_id column at all. The member measured on Cloud on
    // 2026-11-04 (one paid registration_contribution of R500, zero group entries) was therefore
    // shown "No entries in this member's group ledger" beside the card that said they had paid R500:
    // the payment they made never reached the list of transactions they look for.
    @Test
    fun `the registration fee the member paid is in their transaction history`() = runTest {
        stubContributions(
            listOf(contribution("registration_contribution", "500.00", id = "fee-1", paidAt = "2026-03-04T08:00:00Z"))
        )

        val state = openPortal(totalPaid = Money("500.00"))

        val row = state.memberTransactions.single()
        assertEquals(Money("500.00"), row.amount)
        assertEquals("registration_contribution", row.category)
        assertEquals("2026-03-04T08:00:00Z", row.createdAt)
        assertEquals("m1", row.memberId)
        assertTrue("the row is platform money, not a group entry", row.isPlatform)
        assertFalse("the screen may no longer assert an empty book", state.memberActivityIsUnconfirmed)
    }

    @Test
    fun `a fee already booked to the group ledger is not listed twice`() = runTest {
        // 20261101B found seeds that mis-filed registration contributions straight into group_ledger.
        // A row the book really holds renders from the book, once.
        val booked = LedgerEntry(
            id = "l-1",
            groupId = groupId,
            memberId = "m1",
            transactionId = "fee-1",
            amount = Money("500.00"),
            category = "registration_contribution",
            createdAt = "2026-03-04T08:00:00Z"
        )
        stubContributions(
            listOf(contribution("registration_contribution", "500.00", id = "fee-1", paidAt = "2026-03-04T08:00:00Z")),
            ledger = listOf(booked)
        )

        val state = openPortal(totalPaid = Money("500.00"))

        assertEquals(listOf(booked), state.memberTransactions)
    }

    @Test
    fun `a monthly contribution reaches the history only through the ledger`() = runTest {
        val entry = LedgerEntry(
            id = "l-2",
            groupId = groupId,
            memberId = "m1",
            amount = Money("500.00"),
            category = "contribution",
            createdAt = "2026-03-04T08:00:00Z"
        )
        stubContributions(
            (1..12).map { contribution("contribution", "500.00", id = "c$it", paidAt = "2026-03-04T08:00:00Z") },
            ledger = listOf(entry)
        )

        val state = openPortal(totalPaid = Money("6000.00"))

        assertEquals("group-booked rows are not fees, so the ledger alone lists them", listOf(entry), state.memberTransactions)
    }

    @Test
    fun `a fee that has not been paid is not a transaction`() = runTest {
        stubContributions(
            listOf(contribution("registration_contribution", "500.00", id = "fee-1", status = ContributionStatus.DUE))
        )

        val state = openPortal(totalPaid = Money.ZERO)

        assertTrue("nothing was paid, so there is nothing to list", state.memberTransactions.isEmpty())
    }

    @Test
    fun `each platform-booked fee type reaches the history and a group fee does not`() = runTest {
        stubContributions(
            listOf(
                contribution("member_fee", "20.00", id = "fee-2", paidAt = "2026-04-04T08:00:00Z"),
                contribution("member_fee_ledger", "30.00", id = "fee-3", paidAt = "2026-05-04T08:00:00Z"),
                contribution("joining_fee", "100.00", id = "fee-4", paidAt = "2026-06-04T08:00:00Z")
            )
        )

        val state = openPortal(totalPaid = Money("150.00"))

        // joining_fee goes through increment_group_balance, so it belongs to the ledger read rather
        // than to this list - and a row invented for it would be money shown in two books.
        assertEquals(
            setOf("member_fee", "member_fee_ledger"),
            state.memberTransactions.map { it.category }.toSet()
        )
    }

    @Test
    fun `fee rows sit newest first beside the ledger rows`() = runTest {
        val older = LedgerEntry(
            id = "l-old",
            groupId = groupId,
            memberId = "m1",
            amount = Money("500.00"),
            category = "contribution",
            createdAt = "2026-02-04T08:00:00Z"
        )
        val newer = LedgerEntry(
            id = "l-new",
            groupId = groupId,
            memberId = "m1",
            amount = Money("500.00"),
            category = "contribution",
            createdAt = "2026-09-04T08:00:00Z"
        )
        stubContributions(
            listOf(contribution("member_fee", "20.00", id = "fee-1", paidAt = "2026-05-04T08:00:00Z")),
            ledger = listOf(newer, older)
        )

        val state = openPortal(totalPaid = Money("1020.00"))

        assertEquals(
            listOf("l-new", "fee-1", "l-old"),
            state.memberTransactions.map { it.transactionId ?: it.id }
        )
    }
}
