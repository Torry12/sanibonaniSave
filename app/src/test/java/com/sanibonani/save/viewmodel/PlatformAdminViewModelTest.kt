package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.ProcessPayoutUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val claimRepo = mockk<BeneficiaryClaimRepository>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>()
    private val notifRepo = mockk<NotificationRepository>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>()
    private val platformConfigRepo = mockk<PlatformConfigRepository>(relaxed = true)

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
            "registration_fee" to 750.0
        ))
        coEvery { platformRepo.getPlatformAnalytics() } returns Result.success(PlatformAnalytics())
        coEvery { platformRepo.getAllGroups() } returns Result.success(emptyList())
        coEvery { platformRepo.getPlatformPayments() } returns Result.success(emptyList())
        coEvery { payoutRepo.getPendingPayouts() } returns Result.success(emptyList())
        coEvery { platformRepo.getAuditLogs(any()) } returns Result.success(emptyList())
        coEvery { platformRepo.getPlatformLedger() } returns Result.success(emptyList())
        coEvery { platformRepo.getMemberBehaviorInsights() } returns Result.success(emptyList())
        every { loanRepo.getGroupLoans(any()) } returns flowOf(Result.success(emptyList()))

        every { platformConfigRepo.current() } returns PlatformConfig(
            monthlyMemberFee = PlatformFees.MONTHLY_PER_MEMBER,
            registrationFee = PlatformFees.REGISTRATION
        )

        viewModel = PlatformAdminViewModel(
            platformRepo,
            payoutRepo,
            loanRepo,
            processPayoutUseCase,
            claimRepo,
            memberRepo,
            notifRepo,
            supabaseRepo,
            platformConfigRepo
        )
        // NOTE: Do NOT call advanceUntilIdle() here — doing so outside runTest can leak
        // uncaught coroutine exceptions into subsequent test classes.
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `loadData updates state with analytics, groups and payouts`() = runTest {
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
        viewModel.updateMemberCharge("20.0")
        viewModel.updateRegistrationFee("800.0")
        
        coEvery { platformRepo.updateGlobalFees(20.0, 800.0) } returns Result.success(Unit)
        coEvery { platformRepo.broadcastPlatformMessage(any()) } returns Result.success(Unit)

        viewModel.saveGlobalFees()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.saveSuccess)
        coVerify { platformRepo.updateGlobalFees(20.0, 800.0) }
        coVerify {
            platformRepo.broadcastPlatformMessage(match {
                it.contains("Platform fee settings updated") &&
                    it.contains("Monthly member fee") &&
                    it.contains("Registration fee")
            })
        }
        verify { platformConfigRepo.update(20.0, 800.0) }
    }

    @Test
    fun `saveGlobalFees does not broadcast when values are unchanged`() = runTest {
        viewModel.updateMemberCharge("15.0")
        viewModel.updateRegistrationFee("750.0")

        coEvery { platformRepo.updateGlobalFees(15.0, 750.0) } returns Result.success(Unit)

        viewModel.saveGlobalFees()
        advanceUntilIdle()

        coVerify(exactly = 0) { platformRepo.broadcastPlatformMessage(any()) }
    }

    @Test
    fun `approvePayout triggers processPayoutUseCase`() = runTest {
        val payoutId = "payout_123"
        val groupId = "group_456"
        
        coEvery { processPayoutUseCase(payoutId, groupId, PayoutStatus.PROCESSING) } returns Result.success(Unit)

        viewModel.approvePayout(payoutId, groupId)
        advanceUntilIdle()

        coVerify { processPayoutUseCase(payoutId, groupId, PayoutStatus.PROCESSING) }
    }

    @Test
    fun `suspendGroup updates group list status immediately`() = runTest {
        val groupId = "g1"
        val groups = listOf(
            Group(
                id = groupId,
                name = "Group 1",
                isPlatformSuspended = false,
                feeStatus = AdminFeeState.PAID
            )
        )

        // Override ALL init-block mocks BEFORE creating the VM so the init coroutines
        // always execute with the correct data regardless of test ordering.
        coEvery { platformRepo.getAllGroups() } returns Result.success(groups)
        coEvery { platformRepo.getPlatformAnalytics() } returns Result.success(PlatformAnalytics())
        coEvery { platformRepo.getPlatformPayments() } returns Result.success(emptyList())
        coEvery { payoutRepo.getPendingPayouts() } returns Result.success(emptyList())
        coEvery { platformRepo.getPlatformSettings() } returns Result.success(
            mapOf("monthly_per_member" to 15.0, "registration_fee" to 750.0)
        )

        val freshVm = PlatformAdminViewModel(
            platformRepo,
            payoutRepo,
            loanRepo,
            processPayoutUseCase,
            claimRepo,
            memberRepo,
            notifRepo,
            supabaseRepo,
            platformConfigRepo
        )

        // Drain init { loadData(); loadSettings() } so groups are populated
        advanceUntilIdle()

        assertFalse("Precondition: groups must be loaded before testing suspend",
            freshVm.state.value.groups.isEmpty())

        coEvery { platformRepo.suspendGroup(groupId, any()) } returns Result.success(Unit)

        freshVm.suspendGroup(groupId, "Rule violation")
        advanceUntilIdle()

        val updatedGroup = freshVm.state.value.groups.find { it.id == groupId }
        assertTrue("Group should be marked as suspended after suspendGroup()",
            updatedGroup?.isPlatformSuspended ?: false)
        assertEquals(
            "Group fee status should reflect suspension after suspendGroup()",
            AdminFeeState.SUSPENDED,
            updatedGroup?.feeStatus
        )
    }

    @Test
    fun `unsuspendGroup restores paid status immediately`() = runTest {
        val groupId = "g1"
        val groups = listOf(
            Group(
                id = groupId,
                name = "Group 1",
                isPlatformSuspended = true,
                feeStatus = AdminFeeState.SUSPENDED
            )
        )

        coEvery { platformRepo.getAllGroups() } returns Result.success(groups)
        coEvery { platformRepo.getPlatformAnalytics() } returns Result.success(PlatformAnalytics())
        coEvery { platformRepo.getPlatformPayments() } returns Result.success(emptyList())
        coEvery { payoutRepo.getPendingPayouts() } returns Result.success(emptyList())
        coEvery { platformRepo.getPlatformSettings() } returns Result.success(
            mapOf("monthly_per_member" to 15.0, "registration_fee" to 750.0)
        )

        val freshVm = PlatformAdminViewModel(
            platformRepo,
            payoutRepo,
            loanRepo,
            processPayoutUseCase,
            claimRepo,
            memberRepo,
            notifRepo,
            supabaseRepo,
            platformConfigRepo
        )
        advanceUntilIdle()

        coEvery { platformRepo.unsuspendGroup(groupId) } returns Result.success(Unit)

        freshVm.unsuspendGroup(groupId)
        advanceUntilIdle()

        val updatedGroup = freshVm.state.value.groups.find { it.id == groupId }
        assertFalse(
            "Group should no longer be marked as suspended after unsuspendGroup()",
            updatedGroup?.isPlatformSuspended ?: true
        )
        assertEquals(
            "Group fee status should return to paid after unsuspendGroup()",
            AdminFeeState.PAID,
            updatedGroup?.feeStatus
        )
    }

    @Test
    fun `selectImpersonationGroup loads members for the group`() = runTest {
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
        viewModel.selectImpersonationGroup("")
        advanceUntilIdle()

        assertNull(viewModel.state.value.impersonationGroupId)
        assertTrue(viewModel.state.value.impersonationMembers.isEmpty())
    }

    @Test
    fun `selectImpersonationGroup forceReload refetches same group`() = runTest {
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
    fun `resetLocalData clears impersonation state and refreshes platform data`() = runTest {
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
        every { supabaseRepo.currentUserId } returns "platform_admin"
        coEvery { platformRepo.logAuditEvent(any()) } returns Result.failure(IllegalStateException("audit unavailable"))

        viewModel.logAudit(action = "IMPERSONATE_MEMBER", targetMemberId = "m1", targetGroupId = "g1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error?.contains("audit", ignoreCase = true) == true)
        coVerify(exactly = 1) { platformRepo.logAuditEvent(any()) }
    }

    @Test
    fun `approveBurialClaim without session shows auth error and does not call repository`() = runTest {
        every { supabaseRepo.currentUserId } returns null

        viewModel.approveBurialClaim("claim_1", "Approved")
        advanceUntilIdle()

        assertEquals("Your session has expired. Please log in again.", viewModel.state.value.error)
        coVerify(exactly = 0) {
            claimRepo.updateClaimStatus(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `sendDirectWhatsAppTest validates south african phone number before sending`() = runTest {
        viewModel.updateWhatsAppTestPhone("12345")

        viewModel.sendDirectWhatsAppTest()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error?.contains("valid South African WhatsApp number", ignoreCase = true) == true)
        coVerify(exactly = 0) { notifRepo.sendDirectWhatsAppMessage(any(), any()) }
    }

    @Test
    fun `sendDirectWhatsAppTest sends trimmed digits to notification repository`() = runTest {
        coEvery {
            notifRepo.sendDirectWhatsAppMessage(
                "0713459563",
                "Edge function smoke test"
            )
        } returns Result.success(Unit)

        viewModel.updateWhatsAppTestPhone("071 345 9563")
        viewModel.updateWhatsAppTestMessage("Edge function smoke test")

        viewModel.sendDirectWhatsAppTest()
        advanceUntilIdle()

        assertEquals("WhatsApp test sent to 0713459563.", viewModel.state.value.whatsAppTestResult)
        assertFalse(viewModel.state.value.isSendingWhatsAppTest)
        coVerify(exactly = 1) {
            notifRepo.sendDirectWhatsAppMessage("0713459563", "Edge function smoke test")
        }
    }
}
