package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.auth.*
import com.sanibonani.save.domain.service.MfaService
import com.sanibonani.save.domain.service.RealtimeSyncService
import com.sanibonani.save.domain.usecase.auth.GetCurrentUserRoleUseCase
import com.sanibonani.save.domain.usecase.auth.VerifyMfaChallengeUseCase
import com.sanibonani.save.features.admin.presentation.PlatformAdminViewModel
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
class PlatformAdminViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val mfaService = mockk<MfaService>(relaxed = true)
    private val syncService = mockk<RealtimeSyncService>()
    private val syncMetrics = MutableStateFlow<Map<String, RealtimeSyncStatus>>(emptyMap())

    private lateinit var viewModel: PlatformAdminViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        Dispatchers.setMain(testDispatcher)

        val mockSession = mockk<com.sanibonani.save.domain.model.AuthSession>(relaxed = true)
        val sessionFlow = MutableStateFlow<com.sanibonani.save.domain.model.AuthSession?>(mockSession)
        every { supabaseRepo.sessionFlow } returns sessionFlow
        coEvery { supabaseRepo.getUserRole() } returns UserRole.PLATFORM_ADMIN
        every { syncService.syncMetrics } returns syncMetrics

        viewModel = PlatformAdminViewModel(
            GetCurrentUserRoleUseCase(supabaseRepo),
            VerifyMfaChallengeUseCase(mfaService),
            syncService
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `init authorizes platform admin`() = runTest {
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isAuthorized)
    }

    @Test
    fun `setTab updates selected tab`() = runTest {
        advanceUntilIdle()
        viewModel.setTab(3)
        assertEquals(3, viewModel.state.value.selectedTab)
    }

    // MaintenanceTab lists whatever this field holds, so it has to be the sync layer's own answer
    // and not a list the screen invents: an empty map reads as "nothing is subscribed".
    @Test
    fun `the maintenance tab reports the subscriptions the sync layer actually holds`() = runTest {
        advanceUntilIdle()
        assertEquals(emptyMap<String, RealtimeSyncStatus>(), viewModel.state.value.syncMetrics)

        syncMetrics.value = mapOf("group_ledger" to RealtimeSyncStatus(isSubscribed = true, lastUpdate = 123L))
        advanceUntilIdle()

        assertEquals(setOf("group_ledger"), viewModel.state.value.syncMetrics.keys)
        assertEquals(true, viewModel.state.value.syncMetrics["group_ledger"]?.isSubscribed)
    }

    @Test
    fun `onMfaRequired shows MFA challenge`() = runTest {
        advanceUntilIdle()
        viewModel.onMfaRequired()
        assertTrue(viewModel.state.value.showMfaChallenge)
    }

    @Test
    fun `dismissMfaChallenge hides MFA challenge`() = runTest {
        advanceUntilIdle()
        viewModel.onMfaRequired()
        viewModel.dismissMfaChallenge()
        assertFalse(viewModel.state.value.showMfaChallenge)
    }
}
