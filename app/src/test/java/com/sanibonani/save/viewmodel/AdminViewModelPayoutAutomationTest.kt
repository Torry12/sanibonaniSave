package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import io.github.jan.supabase.auth.user.UserSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelPayoutAutomationTest {

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
    private val ledgerRepo = mockk<LedgerRepository>(relaxed = true)

    private val verifyMemberDocumentUseCase = mockk<VerifyMemberDocumentUseCase>(relaxed = true)
    private val updateGroupSettingsUseCase = mockk<UpdateGroupSettingsUseCase>(relaxed = true)
    private val applyViabilityPlanUseCase = mockk<ApplyViabilityPlanUseCase>(relaxed = true)
    private val verifyRelationalDocumentUseCase = mockk<VerifyRelationalDocumentUseCase>(relaxed = true)
    private val getManagedGroupsUseCase = mockk<GetManagedGroupsUseCase>(relaxed = true)
    private val calculateViabilityUseCase = mockk<CalculateViabilityUseCase>(relaxed = true)
    private val updateMemberStatusUseCase = mockk<UpdateMemberStatusUseCase>(relaxed = true)
    private val sendNotificationUseCase = mockk<SendNotificationUseCase>(relaxed = true)
    private val requestPayoutUseCase = mockk<RequestPayoutUseCase>(relaxed = true)
    private val validateLoanEligibilityUseCase = mockk<ValidateLoanEligibilityUseCase>(relaxed = true)
    private val generateLoanContractUseCase = mockk<GenerateLoanContractUseCase>(relaxed = true)
    private val getGroupBusinessInsightsUseCase = mockk<GetGroupBusinessInsightsUseCase>(relaxed = true)
    private val calculateGroupHealthScoreUseCase = mockk<CalculateGroupHealthScoreUseCase>(relaxed = true)
    private val healthScoreRepo = mockk<HealthScoreRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submitPayoutRequest for ROSCA sends automated group-admin notification`() = runTest {
        val userId = "admin-user"
        val groupId = "group-1"
        val group = Group(
            id = groupId,
            name = "ROSCA Circle",
            type = GroupType.ROSCA,
            adminUserId = userId,
            balance = 5000.0
        )

        val sessionFlow = MutableStateFlow<UserSession?>(mockk(relaxed = true))
        every { supabaseRepo.sessionFlow } returns sessionFlow
        every { supabaseRepo.currentUserId } returns userId

        coEvery { getManagedGroupsUseCase(userId) } returns Result.success(listOf(group))
        every { getManagedGroupsUseCase.observeManagedGroups(userId, adminOnly = true) } returns flowOf(Result.success(listOf(group)))
        every { groupRepo.observeGroup(groupId) } returns flowOf(Result.success(group))
        every { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(emptyList()))
        every { memberRepo.getGroupContributions(groupId) } returns flowOf(Result.success(emptyList()))
        every { notifRepo.observeNotifications(groupId) } returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts(groupId) } returns flowOf(Result.success(emptyList()))
        every { groupRepo.observeGroupFeeStatus(groupId) } returns flowOf(AdminFeeState.DUE)
        every { loanRepo.getGroupLoans(groupId) } returns flowOf(Result.success(emptyList()))
        every { claimRepo.observeClaimsForGroup(groupId) } returns flowOf(Result.success(emptyList()))
        every { ledgerRepo.observeGroupLedger(groupId) } returns flowOf(Result.success(emptyList()))
        coEvery { actuarialRepo.computeMetrics(groupId) } returns Result.success(ActuarialMetrics())
        every { getGroupBusinessInsightsUseCase(any(), any()) } returns GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Empty

        coEvery { requestPayoutUseCase(groupId, 1500.0, "FNB", "123456789", "250655") } returns Result.success("payout-1")
        coEvery {
            sendNotificationUseCase.invoke(
                groupId = groupId,
                memberId = null,
                message = any(),
                triggerEvent = NotifEvent.PAYOUT_REQUESTED,
                channel = NotifChannel.BOTH
            )
        } returns Result.success(Unit)

        val vm = AdminViewModel(
            supabaseRepo,
            groupRepo,
            memberRepo,
            beneficiaryRepo,
            memberDocumentRepo,
            actuarialRepo,
            notifRepo,
            paymentRepo,
            payoutRepo,
            exportRepo,
            loanRepo,
            claimRepo,
            AdminGroupContextCacheService(groupRepo, memberRepo, notifRepo, payoutRepo, actuarialRepo),
            verifyMemberDocumentUseCase,
            updateGroupSettingsUseCase,
            applyViabilityPlanUseCase,
            verifyRelationalDocumentUseCase,
            getManagedGroupsUseCase,
            calculateViabilityUseCase,
            updateMemberStatusUseCase,
            sendNotificationUseCase,
            requestPayoutUseCase,
            validateLoanEligibilityUseCase,
            generateLoanContractUseCase,
            getGroupBusinessInsightsUseCase,
            calculateGroupHealthScoreUseCase,
            healthScoreRepo,
            ledgerRepo
        )

        vm.setActive(true)

        advanceUntilIdle()

        vm.updatePayoutAmount("1500")
        vm.updatePayoutBank("FNB")
        vm.updatePayoutAccount("123456789")
        vm.updatePayoutBranch("250655")
        vm.submitPayoutRequest()

        advanceUntilIdle()

        coVerify(exactly = 1) { requestPayoutUseCase(groupId, 1500.0, "FNB", "123456789", "250655") }
        coVerify(exactly = 1) {
            sendNotificationUseCase.invoke(
                groupId = groupId,
                memberId = null,
                message = match { it.contains("ROSCA payout request", ignoreCase = true) },
                triggerEvent = NotifEvent.PAYOUT_REQUESTED,
                channel = NotifChannel.BOTH
            )
        }
    }

    @Test
    fun `approveAndEscalatePayoutRequest notifies platform admin for final approval`() = runTest {
        val userId = "admin-user"
        val groupId = "group-1"
        val payoutId = "payout-1"
        val group = Group(
            id = groupId,
            name = "ROSCA Circle",
            type = GroupType.ROSCA,
            adminUserId = userId,
            balance = 5000.0
        )
        val existingPayout = PayoutRequest(
            id = payoutId,
            groupId = groupId,
            amount = 1500.0,
            bankName = "FNB",
            accountNo = "123456789",
            branchCode = "250655",
            status = PayoutStatus.PENDING
        )

        val sessionFlow = MutableStateFlow<UserSession?>(mockk(relaxed = true))
        every { supabaseRepo.sessionFlow } returns sessionFlow
        every { supabaseRepo.currentUserId } returns userId

        coEvery { getManagedGroupsUseCase(userId) } returns Result.success(listOf(group))
        every { getManagedGroupsUseCase.observeManagedGroups(userId, adminOnly = true) } returns flowOf(Result.success(listOf(group)))
        every { groupRepo.observeGroup(groupId) } returns flowOf(Result.success(group))
        every { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(emptyList()))
        every { memberRepo.getGroupContributions(groupId) } returns flowOf(Result.success(emptyList()))
        every { notifRepo.observeNotifications(groupId) } returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts(groupId) } returns flowOf(Result.success(listOf(existingPayout)))
        every { groupRepo.observeGroupFeeStatus(groupId) } returns flowOf(AdminFeeState.DUE)
        every { loanRepo.getGroupLoans(groupId) } returns flowOf(Result.success(emptyList()))
        every { claimRepo.observeClaimsForGroup(groupId) } returns flowOf(Result.success(emptyList()))
        every { ledgerRepo.observeGroupLedger(groupId) } returns flowOf(Result.success(emptyList()))
        coEvery { actuarialRepo.computeMetrics(groupId) } returns Result.success(ActuarialMetrics())
        every { getGroupBusinessInsightsUseCase(any(), any()) } returns GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Empty

        coEvery { payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.GROUP_APPROVED, null) } returns Result.success(Unit)
        coEvery { sendNotificationUseCase.notifyPlatformAdmin(any()) } returns Result.success(Unit)

        val vm = AdminViewModel(
            supabaseRepo,
            groupRepo,
            memberRepo,
            beneficiaryRepo,
            memberDocumentRepo,
            actuarialRepo,
            notifRepo,
            paymentRepo,
            payoutRepo,
            exportRepo,
            loanRepo,
            claimRepo,
            AdminGroupContextCacheService(groupRepo, memberRepo, notifRepo, payoutRepo, actuarialRepo),
            verifyMemberDocumentUseCase,
            updateGroupSettingsUseCase,
            applyViabilityPlanUseCase,
            verifyRelationalDocumentUseCase,
            getManagedGroupsUseCase,
            calculateViabilityUseCase,
            updateMemberStatusUseCase,
            sendNotificationUseCase,
            requestPayoutUseCase,
            validateLoanEligibilityUseCase,
            generateLoanContractUseCase,
            getGroupBusinessInsightsUseCase,
            calculateGroupHealthScoreUseCase,
            healthScoreRepo,
            ledgerRepo
        )

        vm.setActive(true)

        advanceUntilIdle()

        vm.approveAndEscalatePayoutRequest(payoutId)
        advanceUntilIdle()

        coVerify(exactly = 1) { payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.GROUP_APPROVED, null) }
        coVerify(exactly = 1) {
            sendNotificationUseCase.notifyPlatformAdmin(match {
                it.contains("PAYOUT ESCALATION") &&
                    it.contains(groupId) &&
                    it.contains(payoutId)
            })
        }
    }

    @Test
    fun `approveAndEscalatePayoutRequest shows retry message when platform admin notification fails`() = runTest {
        val userId = "admin-user"
        val groupId = "group-1"
        val payoutId = "payout-1"
        val group = Group(
            id = groupId,
            name = "ROSCA Circle",
            type = GroupType.ROSCA,
            adminUserId = userId,
            balance = 5000.0
        )
        val existingPayout = PayoutRequest(
            id = payoutId,
            groupId = groupId,
            amount = 1500.0,
            bankName = "FNB",
            accountNo = "123456789",
            branchCode = "250655",
            status = PayoutStatus.PENDING
        )

        val sessionFlow = MutableStateFlow<UserSession?>(mockk(relaxed = true))
        every { supabaseRepo.sessionFlow } returns sessionFlow
        every { supabaseRepo.currentUserId } returns userId

        coEvery { getManagedGroupsUseCase(userId) } returns Result.success(listOf(group))
        every { getManagedGroupsUseCase.observeManagedGroups(userId, adminOnly = true) } returns flowOf(Result.success(listOf(group)))
        every { groupRepo.observeGroup(groupId) } returns flowOf(Result.success(group))
        every { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(emptyList()))
        every { memberRepo.getGroupContributions(groupId) } returns flowOf(Result.success(emptyList()))
        every { notifRepo.observeNotifications(groupId) } returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts(groupId) } returns flowOf(Result.success(listOf(existingPayout)))
        every { groupRepo.observeGroupFeeStatus(groupId) } returns flowOf(AdminFeeState.DUE)
        every { loanRepo.getGroupLoans(groupId) } returns flowOf(Result.success(emptyList()))
        every { claimRepo.observeClaimsForGroup(groupId) } returns flowOf(Result.success(emptyList()))
        every { ledgerRepo.observeGroupLedger(groupId) } returns flowOf(Result.success(emptyList()))
        coEvery { actuarialRepo.computeMetrics(groupId) } returns Result.success(ActuarialMetrics())
        every { getGroupBusinessInsightsUseCase(any(), any()) } returns GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Empty

        coEvery { payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.GROUP_APPROVED, null) } returns Result.success(Unit)
        coEvery { sendNotificationUseCase.notifyPlatformAdmin(any()) } returns Result.failure(IllegalStateException("notification failed"))

        val vm = AdminViewModel(
            supabaseRepo,
            groupRepo,
            memberRepo,
            beneficiaryRepo,
            memberDocumentRepo,
            actuarialRepo,
            notifRepo,
            paymentRepo,
            payoutRepo,
            exportRepo,
            loanRepo,
            claimRepo,
            AdminGroupContextCacheService(groupRepo, memberRepo, notifRepo, payoutRepo, actuarialRepo),
            verifyMemberDocumentUseCase,
            updateGroupSettingsUseCase,
            applyViabilityPlanUseCase,
            verifyRelationalDocumentUseCase,
            getManagedGroupsUseCase,
            calculateViabilityUseCase,
            updateMemberStatusUseCase,
            sendNotificationUseCase,
            requestPayoutUseCase,
            validateLoanEligibilityUseCase,
            generateLoanContractUseCase,
            getGroupBusinessInsightsUseCase,
            calculateGroupHealthScoreUseCase,
            healthScoreRepo,
            ledgerRepo
        )

        vm.setActive(true)

        advanceUntilIdle()

        vm.approveAndEscalatePayoutRequest(payoutId)
        advanceUntilIdle()

        assertEquals(
            "Request escalated. Platform admin notification will retry.",
            vm.state.value.successMessage
        )
        coVerify(exactly = 1) { payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.GROUP_APPROVED, null) }
        coVerify(exactly = 1) { sendNotificationUseCase.notifyPlatformAdmin(any()) }
    }

    @Test
    fun `ROSCA payout flow submit then escalate triggers both automated notifications`() = runTest {
        val userId = "admin-user"
        val groupId = "group-1"
        val payoutId = "payout-1"
        val group = Group(
            id = groupId,
            name = "ROSCA Circle",
            type = GroupType.ROSCA,
            adminUserId = userId,
            balance = 5000.0
        )
        val existingPayout = PayoutRequest(
            id = payoutId,
            groupId = groupId,
            amount = 1500.0,
            bankName = "FNB",
            accountNo = "123456789",
            branchCode = "250655",
            status = PayoutStatus.PENDING
        )

        val sessionFlow = MutableStateFlow<UserSession?>(mockk(relaxed = true))
        every { supabaseRepo.sessionFlow } returns sessionFlow
        every { supabaseRepo.currentUserId } returns userId

        coEvery { getManagedGroupsUseCase(userId) } returns Result.success(listOf(group))
        every { getManagedGroupsUseCase.observeManagedGroups(userId, adminOnly = true) } returns flowOf(Result.success(listOf(group)))
        every { groupRepo.observeGroup(groupId) } returns flowOf(Result.success(group))
        every { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(emptyList()))
        every { memberRepo.getGroupContributions(groupId) } returns flowOf(Result.success(emptyList()))
        every { notifRepo.observeNotifications(groupId) } returns flowOf(Result.success(emptyList()))
        every { payoutRepo.observePayouts(groupId) } returns flowOf(Result.success(listOf(existingPayout)))
        every { groupRepo.observeGroupFeeStatus(groupId) } returns flowOf(AdminFeeState.DUE)
        every { loanRepo.getGroupLoans(groupId) } returns flowOf(Result.success(emptyList()))
        every { claimRepo.observeClaimsForGroup(groupId) } returns flowOf(Result.success(emptyList()))
        every { ledgerRepo.observeGroupLedger(groupId) } returns flowOf(Result.success(emptyList()))
        coEvery { actuarialRepo.computeMetrics(groupId) } returns Result.success(ActuarialMetrics())
        every { getGroupBusinessInsightsUseCase(any(), any()) } returns GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Empty

        coEvery { requestPayoutUseCase(groupId, 1500.0, "FNB", "123456789", "250655") } returns Result.success("created-payout")
        coEvery {
            sendNotificationUseCase.invoke(
                groupId = groupId,
                memberId = null,
                message = any(),
                triggerEvent = NotifEvent.PAYOUT_REQUESTED,
                channel = NotifChannel.BOTH
            )
        } returns Result.success(Unit)
        coEvery { payoutRepo.updatePayoutStatus(payoutId, PayoutStatus.GROUP_APPROVED, null) } returns Result.success(Unit)
        coEvery { sendNotificationUseCase.notifyPlatformAdmin(any()) } returns Result.success(Unit)

        val vm = AdminViewModel(
            supabaseRepo,
            groupRepo,
            memberRepo,
            beneficiaryRepo,
            memberDocumentRepo,
            actuarialRepo,
            notifRepo,
            paymentRepo,
            payoutRepo,
            exportRepo,
            loanRepo,
            claimRepo,
            AdminGroupContextCacheService(groupRepo, memberRepo, notifRepo, payoutRepo, actuarialRepo),
            verifyMemberDocumentUseCase,
            updateGroupSettingsUseCase,
            applyViabilityPlanUseCase,
            verifyRelationalDocumentUseCase,
            getManagedGroupsUseCase,
            calculateViabilityUseCase,
            updateMemberStatusUseCase,
            sendNotificationUseCase,
            requestPayoutUseCase,
            validateLoanEligibilityUseCase,
            generateLoanContractUseCase,
            getGroupBusinessInsightsUseCase,
            calculateGroupHealthScoreUseCase,
            healthScoreRepo,
            ledgerRepo
        )

        vm.setActive(true)

        advanceUntilIdle()

        vm.updatePayoutAmount("1500")
        vm.updatePayoutBank("FNB")
        vm.updatePayoutAccount("123456789")
        vm.updatePayoutBranch("250655")
        vm.submitPayoutRequest()
        advanceUntilIdle()

        vm.approveAndEscalatePayoutRequest(payoutId)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            sendNotificationUseCase.invoke(
                groupId = groupId,
                memberId = null,
                message = match { it.contains("ROSCA payout request", ignoreCase = true) },
                triggerEvent = NotifEvent.PAYOUT_REQUESTED,
                channel = NotifChannel.BOTH
            )
        }
        coVerify(exactly = 1) {
            sendNotificationUseCase.notifyPlatformAdmin(match { it.contains("PAYOUT ESCALATION") && it.contains(payoutId) })
        }
    }
}

