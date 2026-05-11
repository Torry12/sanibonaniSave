package com.sanibonani.save

import android.util.Log
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.data.remote.Feature
import com.sanibonani.save.data.remote.GeoapifyResponse
import com.sanibonani.save.data.remote.GeoapifyService
import com.sanibonani.save.data.remote.Geometry
import com.sanibonani.save.data.remote.Properties
import com.sanibonani.save.domain.repository.ExportRepository
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.usecase.CreateGroupUseCase
import com.sanibonani.save.domain.usecase.GetPublicGroupsUseCase
import com.sanibonani.save.viewmodel.GroupViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Rule
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.jan.supabase.auth.user.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val exportRepo = mockk<ExportRepository>(relaxed = true)
    private val createGroupUseCase = mockk<CreateGroupUseCase>(relaxed = true)
    private val getPublicGroupsUseCase = mockk<GetPublicGroupsUseCase>(relaxed = true)
    private val geoapifyService = mockk<GeoapifyService>()
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

        Dispatchers.setMain(testDispatcher)
        viewModel = GroupViewModel(
            groupRepo,
            exportRepo,
            createGroupUseCase,
            getPublicGroupsUseCase,
            geoapifyService
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `searchAddress updates suggestions when query is 3 or more characters`() = runTest {
        val mockResponse = GeoapifyResponse(
            features = listOf(
                Feature(
                    properties = Properties(formatted = "Soweto, South Africa", city = "Johannesburg"),
                    geometry = Geometry(coordinates = listOf(27.864, -26.237))
                )
            )
        )
        coEvery { geoapifyService.autocomplete(any(), any(), any(), any()) } returns mockResponse

        viewModel.updateField("city", "Sow")
        
        // Advance time to account for debounce (500ms)
        advanceTimeBy(600)
        
        assertEquals(1, viewModel.registerState.value.addressSuggestions.size)
        assertEquals("Johannesburg", viewModel.registerState.value.addressSuggestions[0].properties.city)
    }

    @Test
    fun `searchAddress clears suggestions when query is less than 3 characters`() = runTest {
        viewModel.updateField("city", "So")
        
        assertEquals(0, viewModel.registerState.value.addressSuggestions.size)
    }

    @Test
    fun `onAddressSelected updates city and coordinates`() = runTest {
        val feature = Feature(
            properties = Properties(city = "Pretoria", state = "Gauteng"),
            geometry = Geometry(coordinates = listOf(28.229, -25.747))
        )

        viewModel.onAddressSelected(feature)

        assertEquals("Pretoria", viewModel.registerState.value.city)
        assertEquals(-25.747, viewModel.registerState.value.latitude)
        assertEquals(28.229, viewModel.registerState.value.longitude)
    }

    @Test
    fun `manually editing location fields after selection clears coordinates`() = runTest {
        // 1. Select an address
        val feature = Feature(
            properties = Properties(city = "Pretoria", state = "Gauteng"),
            geometry = Geometry(coordinates = listOf(28.229, -25.747))
        )
        viewModel.onAddressSelected(feature)
        
        // 2. Manually edit the city
        viewModel.updateField("city", "Pretoria West")
        
        // 3. Verify coordinates are cleared
        assertNull(viewModel.registerState.value.latitude)
        assertNull(viewModel.registerState.value.longitude)
        assertNull(viewModel.registerState.value.geohash)
    }

    @Test
    fun `finalizeRegistrationAfterPayment calls createGroup and activateGroup`() = runTest {
        // 1. Setup state
        viewModel.updateField("name", "Test Group")
        viewModel.updateField("type", GroupType.BURIAL_SOCIETY)
        viewModel.updateField("province", "Gauteng")
        viewModel.updateField("city", "Soweto")
        viewModel.updateField("adminEmail", "admin@test.com")
        viewModel.updateField("adminPassword", "password123")
        viewModel.updateField("adminFullName", "Test Admin")
        viewModel.updateField("adminIdNumber", "9001015000081")

        coEvery { createGroupUseCase(any(), any(), any(), any(), any(), any()) } returns Result.success("group-123")
        coEvery { groupRepo.activateGroup(any(), any()) } returns Result.success(Unit)

        // 2. Execute
        viewModel.finalizeRegistrationAfterPayment()
        
        // 3. Verify
        advanceUntilIdle()
        
        assertTrue(viewModel.registerState.value.success)
        assertEquals("group-123", viewModel.registerState.value.createdGroupId)
        coVerify { createGroupUseCase(match { it.name == "Test Group" }, "admin@test.com", "password123", "Test Admin", any(), any()) }
        coVerify { groupRepo.activateGroup("group-123", any()) }
    }
}
