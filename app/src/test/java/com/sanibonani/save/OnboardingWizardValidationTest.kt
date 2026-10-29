package com.sanibonani.save

import android.os.Looper
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.service.LocationService
import com.sanibonani.save.domain.service.PlatformFeeService
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import com.sanibonani.save.domain.usecase.auth.*
import com.sanibonani.save.domain.usecase.group.CreateGroupUseCase
import com.sanibonani.save.features.group.presentation.GroupFormEvent
import com.sanibonani.save.features.group.presentation.GroupRegistrationViewModel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The wizard used to advance every step without looking at the inputs, while each step
 * already rendered `fieldErrors` that nothing ever wrote. Users only discovered a missing
 * field when the final submit failed. These tests pin the per-step gate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingWizardValidationTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val createGroupUseCase = mockk<CreateGroupUseCase>(relaxed = true)
    private val activateGroupUseCase = mockk<ActivateGroupUseCase>(relaxed = true)
    private val feeService = mockk<PlatformFeeService>(relaxed = true)
    private val locationService = mockk<LocationService>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)

        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)

        every { feeService.getRegistrationFee() } returns Money("700.0")
        every { supabaseRepo.isLoggedIn } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun viewModel() = GroupRegistrationViewModel(
        createGroupUseCase,
        activateGroupUseCase,
        feeService,
        locationService,
        IsLoggedInUseCase(supabaseRepo),
        GetCurrentSessionEmailUseCase(supabaseRepo),
        GetAttributableActorIdUseCase(supabaseRepo)
    )

    private fun GroupRegistrationViewModel.fillStepOne() {
        onEvent(GroupFormEvent.NameChanged("Sunrise Stokvel"))
        onEvent(GroupFormEvent.AdminEmailChanged("admin@sunrise.co.za"))
        onEvent(GroupFormEvent.AdminPhoneChanged("0712345678"))
    }

    private fun GroupRegistrationViewModel.fillStepTwo() {
        onEvent(GroupFormEvent.CityChanged("Soweto"))
        onEvent(GroupFormEvent.ProvinceSelected("Gauteng"))
    }

    @Test
    fun `next step is refused while step one is incomplete`() {
        val vm = viewModel()

        vm.onEvent(GroupFormEvent.NextStep)

        val state = vm.state.value
        assertEquals("The wizard must stay on the first step", 1, state.currentStep)
        assertTrue(state.fieldErrors.containsKey("name"))
        assertTrue(state.fieldErrors.containsKey("adminEmail"))
        assertTrue(state.fieldErrors.containsKey("adminPhone"))
        assertTrue("The user needs a visible reason why nothing happened", state.error != null)
    }

    @Test
    fun `next step advances once step one is valid and clears stale errors`() {
        val vm = viewModel()
        vm.onEvent(GroupFormEvent.NextStep)

        vm.fillStepOne()
        vm.onEvent(GroupFormEvent.NextStep)

        val state = vm.state.value
        assertEquals(2, state.currentStep)
        assertTrue(state.fieldErrors.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun `editing a field clears only that field's error`() {
        val vm = viewModel()
        vm.onEvent(GroupFormEvent.NextStep)

        vm.onEvent(GroupFormEvent.NameChanged("Sunrise Stokvel"))

        val errors = vm.state.value.fieldErrors
        assertFalse(errors.containsKey("name"))
        assertTrue("Untouched fields must keep their errors", errors.containsKey("adminEmail"))
    }

    @Test
    fun `location step requires a city and a province`() {
        val vm = viewModel()
        vm.fillStepOne()
        vm.onEvent(GroupFormEvent.NextStep)

        vm.onEvent(GroupFormEvent.NextStep)
        assertEquals(2, vm.state.value.currentStep)
        assertTrue(vm.state.value.fieldErrors.containsKey("city"))
        assertTrue(vm.state.value.fieldErrors.containsKey("province"))

        vm.fillStepTwo()
        vm.onEvent(GroupFormEvent.NextStep)
        assertEquals(3, vm.state.value.currentStep)
    }

    @Test
    fun `financial step requires accepting the group terms`() {
        val vm = viewModel()
        vm.fillStepOne()
        vm.onEvent(GroupFormEvent.NextStep)
        vm.fillStepTwo()
        vm.onEvent(GroupFormEvent.NextStep)

        vm.onEvent(GroupFormEvent.NextStep)

        assertEquals("Unaccepted terms must not be skipped", 3, vm.state.value.currentStep)
        assertTrue(vm.state.value.fieldErrors.containsKey("termsAccepted"))
    }

    @Test
    fun `a signed-out admin can leave the first step without a password`() {
        every { supabaseRepo.isLoggedIn } returns false
        val vm = viewModel()

        vm.fillStepOne()
        vm.onEvent(GroupFormEvent.NextStep)

        assertEquals("The password field only exists on the final step", 2, vm.state.value.currentStep)
        assertFalse(vm.state.value.fieldErrors.containsKey("adminPassword"))
    }

    @Test
    fun `the primary action reflects the fee and the final step`() {
        val vm = viewModel()
        assertEquals("Next", vm.state.value.summaryActionLabel)

        val paidAtEnd = vm.state.value.copy(currentStep = 6, platformRegistrationFee = Money("700.0"))
        assertEquals("Pay ${Money("700.0").format()}", paidAtEnd.summaryActionLabel)

        val freeAtEnd = paidAtEnd.copy(platformRegistrationFee = Money.ZERO)
        assertEquals("Create Group", freeAtEnd.summaryActionLabel)
    }

    @Test
    fun `submitting an invalid form returns to the first step with an error`() {
        val vm = viewModel()

        vm.submitGroup()

        val state = vm.state.value
        assertEquals(1, state.currentStep)
        assertTrue(state.fieldErrors.containsKey("name"))
        assertFalse(state.isSubmitting)
        assertNull(state.createdGroupId)
        coVerify(exactly = 0) {
            createGroupUseCase(
                group = any(),
                adminEmail = any(),
                adminPassword = any(),
                adminFullName = any(),
                adminPhone = any(),
                adminIdNumber = any()
            )
        }
    }
}
