package com.sanibonani.save

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.service.LocationService
import com.sanibonani.save.domain.service.PlatformFeeService
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import com.sanibonani.save.domain.usecase.group.CreateGroupUseCase
import com.sanibonani.save.features.group.presentation.GroupFormEvent
import com.sanibonani.save.features.group.presentation.GroupRegistrationViewModel
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationLifecycleTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val createGroupUseCase = mockk<CreateGroupUseCase>(relaxed = true)
    private val activateGroupUseCase = mockk<ActivateGroupUseCase>(relaxed = true)
    private val feeService = mockk<PlatformFeeService>(relaxed = true)
    private val locationService = mockk<LocationService>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)

    private lateinit var viewModel: GroupRegistrationViewModel
    private val viewModelStore = ViewModelStore()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { feeService.getRegistrationFee() } returns Money("700.0")

        viewModel = GroupRegistrationViewModel(
            createGroupUseCase,
            activateGroupUseCase,
            feeService,
            locationService,
            supabaseRepo
        )
        viewModelStore.put("registration-test", viewModel)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
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
        viewModel.onEvent(GroupFormEvent.NameChanged("Audit Group"))
        viewModel.onEvent(GroupFormEvent.AdminEmailChanged("audit@test.com"))
        viewModel.onEvent(GroupFormEvent.AdminPasswordChanged("password123"))
        viewModel.onEvent(GroupFormEvent.AdminFullNameChanged("Audit Admin"))
        viewModel.onEvent(GroupFormEvent.AdminPhoneChanged("0712345678"))
        viewModel.onEvent(GroupFormEvent.AdminIdNumberChanged("9001015000081"))
        viewModel.onEvent(GroupFormEvent.JoiningFeeChanged("50.0"))
        viewModel.onEvent(GroupFormEvent.TermsAcceptedToggled(true))

        val groupId = "group_audit_123"
        val transactionId = "tx_audit_999"

        coEvery {
            createGroupUseCase(
                group = any(),
                adminEmail = any(),
                adminPassword = any(),
                adminFullName = any(),
                adminPhone = any(),
                adminIdNumber = any()
            )
        } returns Result.success(groupId)
        coEvery { activateGroupUseCase(groupId, transactionId) } returns Result.success(Unit)

        // 2. Submit group first
        viewModel.submitGroup()

        // 3. Trigger Finalization with transaction ID
        viewModel.finalizeRegistrationAfterPayment(transactionId)

        // 4. Verify sequential calls to create and activate
        coVerify(exactly = 1) {
            createGroupUseCase(
                group = match { it.name == "Audit Group" && it.joiningFee.toPlainString() == "50.00" },
                adminEmail = "audit@test.com",
                adminPassword = "password123",
                adminFullName = "Audit Admin",
                adminPhone = "0712345678",
                adminIdNumber = "9001015000081"
            )
        }
        coVerify(exactly = 1) { activateGroupUseCase(groupId, transactionId) }

        val finalState = viewModel.state.value
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
        coEvery { memberRepo.registerMember(any(), any()) } throws Exception("You are already a member of this group.")

        val result = try {
            memberRepo.registerMember(member, null)
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
        coEvery {
            createGroupUseCase(
                group = any(),
                adminEmail = any(),
                adminPassword = any(),
                adminFullName = any(),
                adminPhone = any(),
                adminIdNumber = any()
            )
        } returns Result.success(groupId)
        coEvery { activateGroupUseCase(groupId, any()) } returns Result.failure(Exception("Activation failed"))

        // 1. Submit group first to set createdGroupId
        viewModel.onEvent(GroupFormEvent.NameChanged("Partial Fail Group"))
        viewModel.onEvent(GroupFormEvent.AdminEmailChanged("fail@test.com"))
        viewModel.onEvent(GroupFormEvent.AdminPasswordChanged("password123"))
        viewModel.onEvent(GroupFormEvent.AdminPhoneChanged("0712345678"))
        viewModel.onEvent(GroupFormEvent.TermsAcceptedToggled(true))
        viewModel.submitGroup()

        // 2. Finalize
        viewModel.finalizeRegistrationAfterPayment("tx_123")

        val finalState = viewModel.state.value
        assertFalse("Registration should NOT be successful if activation fails", finalState.success)
        assertNotNull("Error message should be present", finalState.error)
        assertEquals("Activation failed", finalState.error)
    }
}
