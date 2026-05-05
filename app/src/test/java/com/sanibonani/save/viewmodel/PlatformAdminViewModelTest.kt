package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.ProcessPayoutUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val processPayoutUseCase = mockk<ProcessPayoutUseCase>()
    private val supabaseRepo = mockk<SupabaseRepository>()
    
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

        viewModel = PlatformAdminViewModel(platformRepo, payoutRepo, processPayoutUseCase, supabaseRepo)
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
    fun `saveGlobalFees updates persistence and global singleton`() = runTest {
        viewModel.updateMemberCharge("20.0")
        viewModel.updateRegistrationFee("800.0")
        
        coEvery { platformRepo.updateGlobalFees(20.0, 800.0) } returns Result.success(Unit)

        viewModel.saveGlobalFees()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.saveSuccess)
        assertEquals(20.0, PlatformFees.MONTHLY_PER_MEMBER, 0.01)
        assertEquals(800.0, PlatformFees.REGISTRATION, 0.01)
        coVerify { platformRepo.updateGlobalFees(20.0, 800.0) }
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

        val freshVm = PlatformAdminViewModel(platformRepo, payoutRepo, processPayoutUseCase, supabaseRepo)

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

        val freshVm = PlatformAdminViewModel(platformRepo, payoutRepo, processPayoutUseCase, supabaseRepo)
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
}
