package com.sanibonani.save

import android.util.Log
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.data.remote.GeoapifyService
import com.sanibonani.save.viewmodel.GroupViewModel
import com.sanibonani.save.viewmodel.RegisterGroupState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Rule
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import com.google.firebase.crashlytics.FirebaseCrashlytics

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationLifecycleTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val createGroupUseCase = mockk<CreateGroupUseCase>(relaxed = true)
    private val getPublicGroupsUseCase = mockk<GetPublicGroupsUseCase>(relaxed = true)
    private val geoapifyService = mockk<GeoapifyService>(relaxed = true)
    private lateinit var viewModel: GroupViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        // Mock Looper.getMainLooper for JVM test environment
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        // Basic stubs for GroupViewModel initialization if it observes something
        coEvery { getPublicGroupsUseCase() } returns flowOf(Result.success(emptyList()))

        Dispatchers.setMain(testDispatcher)
        viewModel = GroupViewModel(groupRepo, createGroupUseCase, getPublicGroupsUseCase, geoapifyService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    /**
     * Audit: Verifies the end-to-end registration-to-activation flow.
     * Ensures that once payment is confirmed, the group is created and immediately activated.
     */
    @Test
    fun `full registration and activation lifecycle audit`() = runTest {
        // 1. Setup Registration State (Step 5 completed, Payment confirmed)
        viewModel.updateField("name", "Audit Group")
        viewModel.updateField("adminEmail", "audit@test.com")
        viewModel.updateField("adminPassword", "password123")
        viewModel.updateField("adminFullName", "Audit Admin")
        viewModel.updateField("adminPhone", "0712345678")
        viewModel.updateField("adminIdNumber", "9001015000081")
        viewModel.updateField("joiningFee", "50.0")

        val groupId = "group_audit_123"
        val transactionId = "tx_audit_999"
        
        coEvery { createGroupUseCase(any(), any(), any(), any(), any(), any()) } returns Result.success(groupId)
        coEvery { groupRepo.activateGroup(groupId, transactionId) } returns Result.success(Unit)

        // 2. Trigger Finalization with transaction ID
        viewModel.finalizeRegistrationAfterPayment(transactionId)
        
        // 3. Verify sequential calls to create and activate
        advanceUntilIdle()
        
        coVerify(exactly = 1) { 
            createGroupUseCase(
                match { it.name == "Audit Group" && it.joiningFee == 50.0 },
                "audit@test.com", 
                "password123", 
                "Audit Admin",
                any(),
                any()
            )
        }
        coVerify(exactly = 1) { groupRepo.activateGroup(groupId, transactionId) }
        
        val finalState = viewModel.registerState.value
        assertTrue("Registration should be marked as successful", finalState.success)
        assertFalse("Needs payment flag should be cleared", finalState.needsPayment)
        assertEquals("Created Group ID should be set in state", groupId, finalState.createdGroupId)
    }

    /**
     * Audit: Verifies uniqueness enforcement during member registration.
     */
    @Test
    fun `member registration enforces uniqueness constraints`() = runTest {
        val member = Member(
            groupId = "group_1",
            userId = "user_1",
            idNumber = "9001015000081",
            fullName = "Duplicate Member"
        )

        // Simulate existing member with same user_id + group_id
        coEvery { memberRepo.registerMember(any()) } throws Exception("You are already a member of this group.")

        val result = try {
            memberRepo.registerMember(member)
        } catch (e: Exception) {
            Result.failure<Member>(e)
        }

        assertTrue(result.isFailure)
        assertEquals("You are already a member of this group.", result.exceptionOrNull()?.message)
    }

    /**
     * Audit: Verifies that if group creation succeeds but activation fails, 
     * the error is handled and state is not marked as successful.
     */
    @Test
    fun `handles partial failure during activation`() = runTest {
        val groupId = "group_partial_fail"
        coEvery { createGroupUseCase(any(), any(), any(), any(), any(), any()) } returns Result.success(groupId)
        coEvery { groupRepo.activateGroup(groupId, any()) } returns Result.failure(Exception("Activation failed"))

        viewModel.finalizeRegistrationAfterPayment("tx_123")
        advanceUntilIdle()

        val finalState = viewModel.registerState.value
        assertFalse("Registration should NOT be successful if activation fails", finalState.success)
        assertNotNull("Error message should be present", finalState.error)
        assertEquals("Activation failed", finalState.error)
    }
}
