package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.service.MemberActuarialService
import com.sanibonani.save.service.MemberGroupContextCacheService
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.google.firebase.crashlytics.FirebaseCrashlytics

@OptIn(ExperimentalCoroutinesApi::class)
class MemberOverviewViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val memberContextCacheService = mockk<MemberGroupContextCacheService>(relaxed = true)
    private val memberRepo = mockk<MemberRepository>(relaxed = true)
    private val groupRepo = mockk<GroupRepository>(relaxed = true)
    private val exportRepo = mockk<ExportRepository>(relaxed = true)
    private val actuarialService = mockk<MemberActuarialService>(relaxed = true)
    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)

    private lateinit var viewModel: MemberOverviewViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        Dispatchers.setMain(testDispatcher)

        every { supabaseRepo.currentUserId } returns "user_123"
        every { memberContextCacheService.contexts } returns MutableStateFlow(emptyMap())

        viewModel = MemberOverviewViewModel(
            memberContextCacheService,
            memberRepo,
            groupRepo,
            exportRepo,
            actuarialService,
            supabaseRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `selectGroup updates state and warms up cache`() = runTest {
        val groupId = "test_group"

        viewModel.selectGroup(groupId)

        assertEquals(groupId, viewModel.state.value.selectedGroupId)
        coVerify { memberContextCacheService.warmUpForUser("user_123") }
    }

    @Test
    fun `loadMemberships automatically selects first group if none selected`() = runTest {
        val memberships = listOf(Member(id = "m1", groupId = "g1"))
        coEvery { memberRepo.getMemberships("user_123") } returns Result.success(memberships)

        // We need to trigger loadMemberships which happens in init.
        // But since we mocked memberRepo AFTER viewModel creation in setup, we might need to recreate or trigger manually if it was possible.
        // Actually setup creates it. Let's just test that memberships are loaded.

        // Re-creating to capture the coEvery stub
        viewModel = MemberOverviewViewModel(
            memberContextCacheService,
            memberRepo,
            groupRepo,
            exportRepo,
            actuarialService,
            supabaseRepo
        )
        advanceUntilIdle()

        assertEquals(memberships, viewModel.state.value.memberships)
        assertEquals("g1", viewModel.state.value.selectedGroupId)
    }
}
