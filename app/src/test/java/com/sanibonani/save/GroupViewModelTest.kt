package com.sanibonani.save

import android.util.Log
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.service.LocationAddress
import com.sanibonani.save.domain.service.LocationService
import com.sanibonani.save.domain.service.PlatformFeeService
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.usecase.group.CreateGroupUseCase
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import com.sanibonani.save.features.group.presentation.GroupFormEvent
import com.sanibonani.save.features.group.presentation.GroupRegistrationViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Rule
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.google.firebase.crashlytics.FirebaseCrashlytics

@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val createGroupUseCase = mockk<CreateGroupUseCase>(relaxed = true)
    private val activateGroupUseCase = mockk<ActivateGroupUseCase>(relaxed = true)
    private val feeService = mockk<PlatformFeeService>(relaxed = true)
    private val locationService = mockk<LocationService>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)

    private lateinit var viewModel: GroupRegistrationViewModel

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
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        // Mock Looper for JVM
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        Dispatchers.setMain(testDispatcher)

        every { feeService.getRegistrationFee() } returns Money(700.0)

        viewModel = GroupRegistrationViewModel(
            createGroupUseCase,
            activateGroupUseCase,
            feeService,
            locationService,
            supabaseRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `searchAddress updates suggestions when query is 3 or more characters`() = runTest {
        val mockSuggestions = listOf(
            LocationAddress(
                formatted = "Soweto, South Africa",
                city = "Johannesburg",
                province = "Gauteng",
                township = "Soweto",
                latitude = -26.237,
                longitude = 27.864
            )
        )
        coEvery { locationService.searchAddress("Sow") } returns Result.success(mockSuggestions)

        viewModel.onEvent(GroupFormEvent.AddressQueryChanged("Sow"))

        // Advance time to account for debounce (500ms)
        advanceTimeBy(600)

        assertEquals(1, viewModel.state.value.addressSuggestions.size)
        assertEquals("Johannesburg", viewModel.state.value.addressSuggestions[0].city)
    }

    @Test
    fun `searchAddress clears suggestions when query is less than 3 characters`() = runTest {
        viewModel.onEvent(GroupFormEvent.AddressQueryChanged("So"))

        assertEquals(0, viewModel.state.value.addressSuggestions.size)
    }

    @Test
    fun `onAddressSelected updates city and coordinates`() = runTest {
        val address = LocationAddress(
            formatted = "Pretoria, South Africa",
            city = "Pretoria",
            province = "Gauteng",
            township = null,
            latitude = -25.747,
            longitude = 28.229
        )

        viewModel.onAddressSelected(address)

        assertEquals("Pretoria", viewModel.state.value.city)
        assertEquals(-25.747, viewModel.state.value.latitude!!, 0.001)
        assertEquals(28.229, viewModel.state.value.longitude!!, 0.001)
    }

    @Test
    fun `manually editing location fields after selection clears coordinates`() = runTest {
        // 1. Select an address
        val address = LocationAddress(
            formatted = "Pretoria, South Africa",
            city = "Pretoria",
            province = "Gauteng",
            township = null,
            latitude = -25.747,
            longitude = 28.229
        )
        viewModel.onAddressSelected(address)

        // 2. Manually edit the city
        viewModel.onEvent(GroupFormEvent.CityChanged("Pretoria West"))

        // 3. Verify coordinates are cleared
        assertNull(viewModel.state.value.latitude)
        assertNull(viewModel.state.value.longitude)
        assertNull(viewModel.state.value.geohash)
    }

    @Test
    fun `finalizeRegistrationAfterPayment calls createGroup and activateGroup`() = runTest {
        // 1. Setup state
        viewModel.onEvent(GroupFormEvent.NameChanged("Test Group"))
        viewModel.onEvent(GroupFormEvent.TypeSelected(GroupType.BURIAL_SOCIETY))
        viewModel.onEvent(GroupFormEvent.ProvinceSelected("Gauteng"))
        viewModel.onEvent(GroupFormEvent.CityChanged("Soweto"))
        viewModel.onEvent(GroupFormEvent.AdminEmailChanged("admin@test.com"))
        viewModel.onEvent(GroupFormEvent.AdminPasswordChanged("password123"))
        viewModel.onEvent(GroupFormEvent.AdminFullNameChanged("Test Admin"))
        viewModel.onEvent(GroupFormEvent.AdminIdNumberChanged("9001015000081"))
        viewModel.onEvent(GroupFormEvent.AdminPhoneChanged("0712345678"))
        viewModel.onEvent(GroupFormEvent.TermsAcceptedToggled(true))

        coEvery { createGroupUseCase(any(), any(), any(), any(), any(), any()) } returns Result.success("group-123")
        coEvery { activateGroupUseCase(any(), any()) } returns Result.success(Unit)

        // 2. Submit group first
        viewModel.submitGroup()
        advanceUntilIdle()

        // 3. Finalize
        viewModel.finalizeRegistrationAfterPayment("tx_123")
        advanceUntilIdle()

        // 4. Verify
        assertTrue(viewModel.state.value.success)
        assertEquals("group-123", viewModel.state.value.createdGroupId)
        coVerify { createGroupUseCase(match { it.name == "Test Group" }, "admin@test.com", "password123", "Test Admin", any(), any()) }
        coVerify { activateGroupUseCase("group-123", "tx_123") }
    }
}
