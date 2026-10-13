package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.CalculateGroupHealthScoreUseCase
import com.sanibonani.save.domain.usecase.ProcessBurialClaimUseCase
import com.sanibonani.save.domain.usecase.ProcessPayoutUseCase
import io.github.jan.supabase.auth.user.UserSession
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
class PlatformAdminViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val platformRepo = mockk<PlatformRepository>()
    private val payoutRepo = mockk<PayoutRepository>()
    private val loanRepo = mockk<LoanRepository>()
    private val processPayoutUseCase = mockk<ProcessPayoutUseCase>()
    private val processBurialClaimUseCase = mockk<ProcessBurialClaimUseCase>()
    private val claimRepo = mockk<BeneficiaryClaimRepository>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>()
    private val notifRepo = mockk<NotificationRepository>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>()
    private val platformConfigRepo = mockk<PlatformConfigRepository>(relaxed = true)
    private val calculateGroupHealthScoreUseCase = mockk<CalculateGroupHealthScoreUseCase>()
    private val exportRepo = mockk<ExportRepository>(relaxed = true)

    private lateinit var viewModel: PlatformAdminViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        // Mock Android framework statics so the ViewModel can run in unit tests
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0

        Dispatchers.setMain(testDispatcher)

        // Default success for all parallel calls made by init { loadData(); loadSettings() }
        coEvery { platformRepo.getPlatformSettings() } returns Result.success(mapOf(
            "monthly_per_member" to 15.0,
            "registration_fee" to 750.0,
            "payout_fee" to 5.0,
            "whatsapp_fee" to 0.50,
            "late_fee_percent" to 10.0,
            "auto_suspension_days" to 30.0
        ))
        coEvery { platformRepo.getPlatformAnalytics() } returns Result.success(PlatformAnalytics())
        coEvery { platformRepo.getAllGroups() } returns Result.success(emptyList())
        coEvery { platformRepo.getPlatformPayments() } returns Result.success(emptyList())
        coEvery { payoutRepo.getPendingPayouts() } returns Result.success(emptyList())
        coEvery { platformRepo.getAuditLogs(any()) } returns Result.success(emptyList())
        coEvery { platformRepo.getPlatformLedger() } returns Result.success(emptyList())
        coEvery { platformRepo.getMemberBehaviorInsights() } returns Result.success(emptyList())
        coEvery { platformRepo.logAuditEvent(any()) } returns Result.success(Unit)
        coEvery { calculateGroupHealthScoreUseCase(any()) } returns Result.success(GroupHealthScore("g1", 80, RiskZone.GREEN, emptyMap(), emptyList(), "", ""))
        every { claimRepo.observeEscalatedClaims() } returns flowOf(Result.success(emptyList()))
        every { loanRepo.getGroupLoans(any()) } returns flowOf(Result.success(emptyList()))
        coEvery { memberRepo.getMemberById(any()) } returns Result.success(Member(id = "m1", fullName = "Test Member", groupId = "g1"))

        every { platformConfigRepo.current() } returns PlatformConfig(
            monthlyMemberFee = 15.0,
            registrationFee = 750.0,
            payoutFee = 5.0,
            whatsappFee = 0.50,
            lateFeePercent = 10.0,
            autoSuspensionDays = 30
        )
        every { supabaseRepo.currentUserId } returns "platform_admin"
        val mockSession = mockk<UserSession>(relaxed = true)
        val mockUser = mockk<io.github.jan.supabase.auth.user.UserInfo>(relaxed = true)
        every { mockUser.id } returns "platform_admin"
        every { mockSession.user } returns mockUser
        val sessionFlow = MutableStateFlow<UserSession?>(mockSession)
        every { supabaseRepo.sessionFlow } returns sessionFlow

        viewModel = PlatformAdminViewModel(
            platformRepo,
            payoutRepo,
            loanRepo,
            processPayoutUseCase,
            claimRepo,
            processBurialClaimUseCase,
            memberRepo,
            notifRepo,
            supabaseRepo,
            platformConfigRepo,
            calculateGroupHealthScoreUseCase,
            exportRepo
        )
    }

    private fun TestScope.ensureActive() {
        viewModel.setActive(true)
        advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `loadData updates state with analytics, groups and payouts`() = runTest {
        ensureActive()
        val analytics = PlatformAnalytics(totalGroups = 10, totalMembers = 100)
        val groups = listOf(Group(id = "g1", name = "Group 1"))
        val payouts = listOf(PayoutRequest(
            id = "p1", 
            groupId = "g1", 
            amount = 1000.0, 
            status = PayoutStatus.PENDING,
            bankName = "FNB",
            accountNo = "123456789",
            branchCode = "250655"
        ))

        coEvery { platformRepo.getPlatformAnalytics() } returns Result.success(analytics)
        coEvery { platformRepo.getAllGroups() } returns Result.success(groups)
        coEvery { payoutRepo.getPendingPayouts() } returns Result.success(payouts)

        viewModel.loadData()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(10, state.analytics.totalGroups)
        assertEquals("Group 1", state.groups.first().name)
        assertEquals("p1", state.payouts.first().id)
        assertFalse(state.isLoading)
    }

    @Test
    fun `saveGlobalFees updates persistence and platform config repository`() = runTest {
        ensureActive()
        viewModel.updateMemberCharge("20.0")
        viewModel.updateRegistrationFee("800.0")
        viewModel.updatePayoutFee("10.0")
        viewModel.updateWhatsappFee("1.0")
        viewModel.updateLateFeePercent("15.0")
        viewModel.updateAutoSuspensionDays("45")
        
        coEvery { platformRepo.updateGlobalFees(20.0, 800.0, 10.0, 1.0, 15.0, 45) } returns Result.success(Unit)
        coEvery { platformRepo.broadcastPlatformMessage(any()) } returns Result.success(Unit)

        viewModel.saveGlobalFees()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.saveSuccess)
        coVerify { platformRepo.updateGlobalFees(20.0, 800.0, 10.0, 1.0, 15.0, 45) }
        coVerify {
            platformRepo.broadcastPlatformMessage(match {
                it.contains("Platform settings updated") &&
                    it.contains("Monthly fee: R20.0") &&
                    it.contains("Reg fee: R800.0")
            })
        }
        verify { platformConfigRepo.update(20.0, 800.0, 10.0, 1.0, 15.0, 45) }
    }

    @Test
    fun `saveGlobalFees does not broadcast when values are unchanged`() = runTest {
        ensureActive()
        // Values from setup() mock settings
        viewModel.updateMemberCharge("15.0")
        viewModel.updateRegistrationFee("750.0")
        viewModel.updatePayoutFee("5.0")
        viewModel.updateWhatsappFee("0.50")
        viewModel.updateLateFeePercent("10.0")
        viewModel.updateAutoSuspensionDays("30")

        coEvery { platformRepo.updateGlobalFees(15.0, 750.0, 5.0, 0.5, 10.0, 30) } returns Result.success(Unit)

        viewModel.saveGlobalFees()
        advanceUntilIdle()

        coVerify(exactly = 0) { platformRepo.broadcastPlatformMessage(any()) }
    }

    @Test
    fun `approvePayout triggers processPayoutUseCase`() = runTest {
        ensureActive()
        val payoutId = "payout_123"
        val groupId = "group_456"
        
        coEvery { processPayoutUseCase(payoutId, groupId, PayoutStatus.PROCESSING, "platform_admin") } returns Result.success(Unit)

        viewModel.approvePayout(payoutId, groupId)
        advanceUntilIdle()

        coVerify { processPayoutUseCase(payoutId, groupId, PayoutStatus.PROCESSING, "platform_admin") }
    }

    @Test
    fun `completePayout triggers processPayoutUseCase with COMPLETED status`() = runTest {
        ensureActive()
        val payoutId = "payout_123"
        val groupId = "group_456"

        coEvery { processPayoutUseCase(payoutId, groupId, PayoutStatus.COMPLETED, "platform_admin") } returns Result.success(Unit)

        viewModel.completePayout(payoutId, groupId)
        advanceUntilIdle()

        coVerify { processPayoutUseCase(payoutId, groupId, PayoutStatus.COMPLETED, "platform_admin") }
    }

    @Test
    fun `rejectPayout triggers processPayoutUseCase with FAILED status`() = runTest {
        ensureActive()
        val payoutId = "payout_123"
        val groupId = "group_456"

        coEvery { processPayoutUseCase(payoutId, groupId, PayoutStatus.FAILED, "platform_admin") } returns Result.success(Unit)

        viewModel.rejectPayout(payoutId, groupId)
        advanceUntilIdle()

        coVerify { processPayoutUseCase(payoutId, groupId, PayoutStatus.FAILED, "platform_admin") }
    }

    @Test
    fun `approveLoanRequest updates loan status and logs audit`() = runTest {
        ensureActive()
        val loan = Loan(id = "l1", memberId = "m1", groupId = "g1", amount = 500.0)
        coEvery { loanRepo.approveLoan("l1") } returns Result.success(Unit)
        coEvery { platformRepo.logAuditEvent(any()) } returns Result.success(Unit)

        viewModel.approveLoanRequest(loan)
        advanceUntilIdle()

        coVerify { loanRepo.approveLoan("l1") }
        coVerify { 
            platformRepo.logAuditEvent(match { 
                it.action == "PLATFORM_APPROVE_LOAN_REQUEST" && it.targetMemberId == "m1" 
            }) 
        }
        assertTrue(viewModel.state.value.saveSuccess)
    }

    @Test
    fun `rejectLoanRequest updates loan status with reason and logs audit`() = runTest {
        ensureActive()
        val loan = Loan(id = "l1", memberId = "m1", groupId = "g1", amount = 500.0)
        coEvery { loanRepo.rejectLoan("l1", "Credit check failed") } returns Result.success(Unit)
        coEvery { platformRepo.logAuditEvent(any()) } returns Result.success(Unit)

        viewModel.rejectLoanRequest(loan, "Credit check failed")
        advanceUntilIdle()

        coVerify { loanRepo.rejectLoan("l1", "Credit check failed") }
        coVerify { 
            platformRepo.logAuditEvent(match { 
                it.action == "PLATFORM_REJECT_LOAN_REQUEST" && it.details?.get("reason") == "Credit check failed"
            }) 
        }
    }

    @Test
    fun `suspendGroup updates group list status immediately`() = runTest {
        val groupId = "g1"
        coEvery { platformRepo.getAllGroups() } returns Result.success(listOf(
            Group(id = groupId, name = "Group 1", isPlatformSuspended = false, feeStatus = AdminFeeState.PAID)
        ))
        ensureActive()

        coEvery { platformRepo.suspendGroup(groupId, any()) } returns Result.success(Unit)

        viewModel.suspendGroup(groupId, "Rule violation")
        advanceUntilIdle()

        val updatedGroup = viewModel.state.value.groups.find { it.id == groupId }
        assertTrue(updatedGroup?.isPlatformSuspended ?: false)
        assertEquals(AdminFeeState.SUSPENDED, updatedGroup?.feeStatus)
    }

    @Test
    fun `unsuspendGroup restores paid status immediately`() = runTest {
        val groupId = "g1"
        coEvery { platformRepo.getAllGroups() } returns Result.success(listOf(
            Group(id = groupId, name = "Group 1", isPlatformSuspended = true, feeStatus = AdminFeeState.SUSPENDED)
        ))
        ensureActive()

        coEvery { platformRepo.unsuspendGroup(groupId) } returns Result.success(Unit)

        viewModel.unsuspendGroup(groupId)
        advanceUntilIdle()

        val updatedGroup = viewModel.state.value.groups.find { it.id == groupId }
        assertFalse(updatedGroup?.isPlatformSuspended ?: true)
        assertEquals(AdminFeeState.PAID, updatedGroup?.feeStatus)
    }

    @Test
    fun `selectImpersonationGroup loads members for the group`() = runTest {
        ensureActive()
        val groupId = "g1"
        val members = listOf(Member(id = "m1", fullName = "Member 1", groupId = groupId))

        coEvery { memberRepo.syncGroupMembers(groupId) } returns Result.success(members)

        viewModel.selectImpersonationGroup(groupId)
        advanceUntilIdle()

        assertEquals(groupId, viewModel.state.value.impersonationGroupId)
        assertEquals(members, viewModel.state.value.impersonationMembers)
        assertFalse(viewModel.state.value.isLoadingImpersonationMembers)
    }

    @Test
    fun `selectImpersonationGroup with empty id resets state`() = runTest {
        ensureActive()
        viewModel.selectImpersonationGroup("")
        advanceUntilIdle()

        assertNull(viewModel.state.value.impersonationGroupId)
        assertTrue(viewModel.state.value.impersonationMembers.isEmpty())
    }

    @Test
    fun `selectImpersonationGroup forceReload refetches same group`() = runTest {
        ensureActive()
        val groupId = "g1"
        val firstLoad = listOf(Member(id = "m1", fullName = "Member 1", groupId = groupId))
        val secondLoad = listOf(
            Member(id = "m1", fullName = "Member 1", groupId = groupId),
            Member(id = "m2", fullName = "Member 2", groupId = groupId)
        )

        coEvery { memberRepo.syncGroupMembers(groupId) } returnsMany listOf(
            Result.success(firstLoad),
            Result.success(secondLoad)
        )

        viewModel.selectImpersonationGroup(groupId)
        advanceUntilIdle()

        viewModel.selectImpersonationGroup(groupId, forceReload = true)
        advanceUntilIdle()

        assertEquals(secondLoad, viewModel.state.value.impersonationMembers)
        coVerify(exactly = 2) { memberRepo.syncGroupMembers(groupId) }
    }

    @Test
    fun `selectImpersonationGroup ignores stale member load after re-entry`() = runTest {
        ensureActive()
        val slowGroupId = "g_slow"
        val fastGroupId = "g_fast"
        val slowMembers = listOf(Member(id = "m_slow", fullName = "Slow Member", groupId = slowGroupId))
        val fastMembers = listOf(Member(id = "m_fast", fullName = "Fast Member", groupId = fastGroupId))

        coEvery { memberRepo.syncGroupMembers(slowGroupId) } coAnswers {
            kotlinx.coroutines.delay(1_000)
            Result.success(slowMembers)
        }
        coEvery { memberRepo.syncGroupMembers(fastGroupId) } returns Result.success(fastMembers)

        viewModel.selectImpersonationGroup(slowGroupId)
        runCurrent()
        assertEquals(slowGroupId, viewModel.state.value.impersonationGroupId)
        assertTrue(viewModel.state.value.isLoadingImpersonationMembers)

        viewModel.selectImpersonationGroup(fastGroupId)
        advanceUntilIdle()

        assertEquals(fastGroupId, viewModel.state.value.impersonationGroupId)
        assertEquals(fastMembers, viewModel.state.value.impersonationMembers)
        assertFalse(viewModel.state.value.impersonationMembers.any { it.groupId == slowGroupId })
        assertFalse(viewModel.state.value.isLoadingImpersonationMembers)
    }

    @Test
    fun `refreshMaintenanceData prevents stale impersonation load from repopulating members`() = runTest {
        ensureActive()
        val groupId = "g1"
        coEvery { memberRepo.syncGroupMembers(groupId) } coAnswers {
            kotlinx.coroutines.delay(1_000)
            Result.success(listOf(Member(id = "m1", fullName = "Member 1", groupId = groupId)))
        }
        coEvery { platformRepo.getAllGroups() } returns Result.success(emptyList())

        viewModel.selectImpersonationGroup(groupId)
        runCurrent()
        assertEquals(groupId, viewModel.state.value.impersonationGroupId)

        viewModel.refreshMaintenanceData()
        advanceUntilIdle()

        assertNull(viewModel.state.value.impersonationGroupId)
        assertTrue(viewModel.state.value.impersonationMembers.isEmpty())
        assertFalse(viewModel.state.value.isLoadingImpersonationMembers)
    }

    @Test
    fun `resetLocalData clears impersonation state and refreshes platform data`() = runTest {
        ensureActive()
        val groupId = "g1"
        val members = listOf(Member(id = "m1", fullName = "Member 1", groupId = groupId))
        val refreshedGroups = listOf(Group(id = groupId, name = "Reloaded Group"))

        coEvery { memberRepo.syncGroupMembers(groupId) } returns Result.success(members)
        coEvery { supabaseRepo.resetLocalCache() } just Runs
        coEvery { platformRepo.getAllGroups() } returns Result.success(refreshedGroups)

        viewModel.selectImpersonationGroup(groupId)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.impersonationMembers.size)

        viewModel.resetLocalData()
        advanceUntilIdle()

        assertNull(viewModel.state.value.impersonationGroupId)
        assertTrue(viewModel.state.value.impersonationMembers.isEmpty())
        assertEquals(refreshedGroups, viewModel.state.value.groups)
        coVerify(exactly = 1) { supabaseRepo.resetLocalCache() }
    }

    @Test
    fun `refreshMaintenanceData clears impersonation state and reloads platform data`() = runTest {
        ensureActive()
        val groupId = "g1"
        val members = listOf(Member(id = "m1", fullName = "Member 1", groupId = groupId))
        val refreshedGroups = listOf(Group(id = groupId, name = "Refreshed Group"))

        coEvery { memberRepo.syncGroupMembers(groupId) } returns Result.success(members)
        coEvery { platformRepo.getAllGroups() } returns Result.success(refreshedGroups)

        viewModel.selectImpersonationGroup(groupId)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.impersonationMembers.size)

        viewModel.refreshMaintenanceData()
        advanceUntilIdle()

        assertNull(viewModel.state.value.impersonationGroupId)
        assertTrue(viewModel.state.value.impersonationMembers.isEmpty())
        assertEquals(refreshedGroups, viewModel.state.value.groups)
    }

    @Test
    fun `logAudit surfaces repository failure`() = runTest {
        ensureActive()
        every { supabaseRepo.currentUserId } returns "platform_admin"
        coEvery { platformRepo.logAuditEvent(any()) } returns Result.failure(IllegalStateException("audit unavailable"))

        viewModel.logAudit(action = "IMPERSONATE_MEMBER", targetMemberId = "m1", targetGroupId = "g1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error?.contains("audit", ignoreCase = true) == true)
        coVerify(exactly = 1) { platformRepo.logAuditEvent(any()) }
    }

    @Test
    fun `approveBurialClaim without session shows auth error and does not call repository`() = runTest {
        ensureActive()
        every { supabaseRepo.currentUserId } returns null

        viewModel.approveBurialClaim("claim_1", "Approved")
        advanceUntilIdle()

        assertEquals("Your session has expired. Please log in again.", viewModel.state.value.error)
        coVerify(exactly = 0) {
            processBurialClaimUseCase(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `payBurialClaim updates claim status to PAID`() = runTest {
        ensureActive()
        val claimId = "claim_1"
        every { supabaseRepo.currentUserId } returns "admin_user"
        coEvery { processBurialClaimUseCase(claimId, BeneficiaryClaimStatus.PAID, "admin_user", any(), any()) } returns Result.success(Unit)

        viewModel.payBurialClaim(claimId, "Paid via EFT")
        advanceUntilIdle()

        coVerify { processBurialClaimUseCase(claimId, BeneficiaryClaimStatus.PAID, "admin_user", "Paid via EFT", null) }
        assertTrue(viewModel.state.value.saveSuccess)
    }

    @Test
    fun `rejectBurialClaim updates claim status to REJECTED with reason`() = runTest {
        ensureActive()
        val claimId = "claim_1"
        every { supabaseRepo.currentUserId } returns "admin_user"
        coEvery { processBurialClaimUseCase(claimId, BeneficiaryClaimStatus.REJECTED, "admin_user", any(), any()) } returns Result.success(Unit)

        viewModel.rejectBurialClaim(claimId, "Invalid documents")
        advanceUntilIdle()

        coVerify { processBurialClaimUseCase(claimId, BeneficiaryClaimStatus.REJECTED, "admin_user", null, "Invalid documents") }
        assertTrue(viewModel.state.value.saveSuccess)
    }

    @Test
    fun `broadcastMessage calls repository and logs audit`() = runTest {
        ensureActive()
        viewModel.updateBroadcastMessage("Hello groups!")
        coEvery { platformRepo.broadcastPlatformMessage("Hello groups!") } returns Result.success(Unit)
        coEvery { platformRepo.logAuditEvent(any()) } returns Result.success(Unit)

        viewModel.broadcastMessage()
        advanceUntilIdle()

        coVerify { platformRepo.broadcastPlatformMessage("Hello groups!") }
        coVerify { platformRepo.logAuditEvent(match { it.action == "PLATFORM_BROADCAST" }) }
        assertTrue(viewModel.state.value.broadcastSuccess)
        assertEquals("", viewModel.state.value.broadcastMessage)
    }

    @Test
    fun `sendWhatsAppTestToAdmin calls notification repository`() = runTest {
        ensureActive()
        val groupId = "g1"
        coEvery { notifRepo.sendNotification(any()) } returns Result.success(Unit)

        viewModel.sendWhatsAppTestToAdmin(groupId)
        advanceUntilIdle()

        coVerify { notifRepo.sendNotification(match { it.groupId == groupId && it.channel == NotifChannel.WHATSAPP }) }
        assertEquals("Test message sent to group admin.", viewModel.state.value.whatsAppTestResult)
    }

    @Test
    fun `sendDirectWhatsAppTest validates south african phone number before sending`() = runTest {
        ensureActive()
        viewModel.updateWhatsAppTestPhone("12345")

        viewModel.sendDirectWhatsAppTest()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error?.contains("valid South African WhatsApp number", ignoreCase = true) == true)
        coVerify(exactly = 0) { notifRepo.sendDirectWhatsAppMessage(any(), any()) }
    }

    @Test
    fun `fetchGroupMetrics updates state with actuarial metrics`() = runTest {
        ensureActive()
        val groupId = "g1"
        val metrics = ActuarialMetrics(
            solvencyMarginPct = 1.5,
            compositeRiskScore = 10
        )
        coEvery { platformRepo.getGroupMetrics(groupId) } returns Result.success(metrics)

        viewModel.fetchGroupMetrics(groupId)
        advanceUntilIdle()

        assertEquals(metrics, viewModel.state.value.selectedGroupMetrics)
    }

    @Test
    fun `setRiskFilter filters behavior insights correctly`() = runTest {
        ensureActive()
        val groups = listOf(Group(id = "g1", name = "Group 1"))
        coEvery { platformRepo.getAllGroups() } returns Result.success(groups)
        
        val insights = listOf(
            MemberBehaviorInsight(memberId = "1", riskBand = "High", memberName = "M1", groupId = "g1"),
            MemberBehaviorInsight(memberId = "2", riskBand = "Stable", memberName = "M2", groupId = "g1"),
            MemberBehaviorInsight(memberId = "3", riskBand = "High", memberName = "M3", groupId = "g1")
        )
        
        coEvery { platformRepo.getMemberBehaviorInsights() } returns Result.success(insights)
        
        // Reload data to populate groups first
        viewModel.loadData()
        advanceUntilIdle()
        
        viewModel.refreshLoanRequests()
        advanceUntilIdle()
        
        assertEquals(3, viewModel.state.value.memberBehaviorInsights.size)
        
        viewModel.setRiskFilter("High")
        assertEquals(2, viewModel.state.value.filteredMemberBehaviorInsights.size)
        assertTrue(viewModel.state.value.filteredMemberBehaviorInsights.all { it.riskBand == "High" })
        
        viewModel.setRiskFilter("All")
        assertEquals(3, viewModel.state.value.filteredMemberBehaviorInsights.size)
    }

    @Test
    fun `sendWhatsAppTestToAdmin handles failure gracefully`() = runTest {
        ensureActive()
        val groupId = "g1"
        coEvery { notifRepo.sendNotification(any()) } returns Result.failure(Exception("network error"))

        viewModel.sendWhatsAppTestToAdmin(groupId)
        advanceUntilIdle()

        assertEquals("network error", viewModel.state.value.whatsAppTestResult)
        assertFalse(viewModel.state.value.isSendingWhatsAppTest)
    }

    @Test
    fun `setTab updates selectedTab and resets flags`() = runTest {
        ensureActive()
        viewModel.setTab(2)
        assertEquals(2, viewModel.state.value.selectedTab)
        assertFalse(viewModel.state.value.saveSuccess)
        assertNull(viewModel.state.value.error)
    }
}
