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
import com.sanibonani.save.features.member.presentation.state.MemberUiState
import com.sanibonani.save.service.CachedGroupContext
import com.sanibonani.save.service.MemberGroupContextCacheService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Support Mode opens behind a privacy lock: nothing of the targeted member's may render before their
 * record is verified. When the entry was refused, no record is ever coming, and the lock still
 * outranked the refusal - the admin sat on "Entering Support Mode…" with no top bar, and the one
 * control that could have escaped called refreshData(), which returns early while no member is
 * loaded. The lock has to release on a refusal, and Retry has to re-run the entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberSupportEntryRefusalTest {

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

    private val contexts = MutableStateFlow<Map<String, CachedGroupContext>>(emptyMap())
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

    private fun refuseEntry(reason: String) {
        coEvery { memberContextCacheService.impersonateMember("m1", groupId) } returns
            Result.failure(IllegalStateException(reason))
    }

    private suspend fun TestScope.openSupportMode(viewModel: MemberOverviewViewModel) {
        viewModel.setActive(true)
        viewModel.setPortalContext("m1", groupId)
        advanceUntilIdle()
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
        coEvery { supabaseRepo.getUserRole() } returns UserRole.PLATFORM_ADMIN
        every { memberContextCacheService.contexts } returns contexts
        every { memberContextCacheService.unverifiedSections } returns MutableStateFlow(emptyMap())
        every { networkService.isOnline } returns flowOf(true)
        every { networkService.isAirplaneModeActive } returns flowOf(false)
        every { paymentCalculator.calculateExpectedMemberGroupPart(any(), any()) } returns Money.ZERO
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `a refused entry releases the privacy lock so the refusal can render`() = runTest {
        refuseEntry("The selected member could not be verified.")
        val viewModel = createViewModel()

        openSupportMode(viewModel)

        val state = viewModel.state.value
        assertNull(state.member)
        assertNotNull("the admin has to be told why nothing opened", state.error)
        assertFalse("a lock with no book behind it is only a wall", state.isPrivacyLocked)
        assertTrue(state.supportEntryWasRefused)
        assertFalse(state.isLoading)
    }

    @Test
    fun `an entry still in flight keeps the book locked away`() = runTest {
        coEvery { memberContextCacheService.impersonateMember("m1", groupId) } coAnswers { awaitCancellation() }
        val viewModel = createViewModel()

        openSupportMode(viewModel)

        val state = viewModel.state.value
        assertNull(state.member)
        assertNull(state.error)
        assertTrue("the lock is what stops another member's book rendering", state.isPrivacyLocked)
        assertFalse(state.supportEntryWasRefused)
    }

    @Test
    fun `the Retry on a refused entry re-runs the entry`() = runTest {
        refuseEntry("Network unavailable.")
        val viewModel = createViewModel()
        openSupportMode(viewModel)

        viewModel.refreshData()
        advanceUntilIdle()

        coVerify(exactly = 2) { memberContextCacheService.impersonateMember("m1", groupId) }
    }

    @Test
    fun `a member who is not an operator of the group is never handed a hydration`() = runTest {
        coEvery { supabaseRepo.getUserRole() } returns UserRole.MEMBER
        // The role check falls through to the group's admin row, so the read has to answer with a
        // group this operator does not own: a relaxed mock hands back a value Result<Group> cannot hold.
        coEvery { groupRepo.getGroupById(groupId) } returns
            Result.success(group.copy(adminUserId = "another-admin"))
        val viewModel = createViewModel()

        viewModel.setActive(true)
        viewModel.setPortalContext("m1", groupId)
        advanceUntilIdle()

        coVerify(exactly = 0) { memberContextCacheService.impersonateMember(any(), any()) }
        assertFalse(viewModel.state.value.isImpersonating)
    }

    @Test
    fun `a book that is not the targeted member's stays locked`() {
        val state = MemberUiState(
            member = Member(id = "someone-else", groupId = groupId, userId = "u9"),
            group = group,
            contributions = listOf(),
            isImpersonating = true,
            targetMemberId = "m1",
            isLoading = false,
            error = "The refresh was refused."
        )

        assertTrue(state.isPrivacyLocked)
        assertFalse("an error over a loaded book is not a refused entry", state.supportEntryWasRefused)
    }

    @Test
    fun `leaving support mode never strands the screen on a refusal`() {
        val state = MemberUiState(
            isImpersonating = true,
            targetMemberId = "m1",
            isExiting = true,
            isLoading = false,
            error = "The refresh was refused."
        )

        assertFalse(state.supportEntryWasRefused)
        assertFalse(state.isPrivacyLocked)
    }

    @Test
    fun `no hydration is attempted for a blank target`() = runTest {
        val viewModel = createViewModel()
        viewModel.setActive(true)
        viewModel.setPortalContext(null, groupId)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isImpersonating)
        coVerify(exactly = 0) { memberContextCacheService.impersonateMember(any(), any()) }
    }
}
