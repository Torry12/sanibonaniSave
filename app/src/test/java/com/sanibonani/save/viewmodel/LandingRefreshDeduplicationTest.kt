package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.AuthSession
import com.sanibonani.save.domain.model.PlatformAnalytics
import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.service.DevSettingsService
import com.sanibonani.save.domain.service.NetworkConnectivityService
import com.sanibonani.save.domain.usecase.admin.GetPlatformAnalyticsUseCase
import com.sanibonani.save.domain.usecase.admin.RefreshPlatformConfigUseCase
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.auth.GetCurrentUserRoleUseCase
import com.sanibonani.save.domain.usecase.auth.IsLoggedInUseCase
import com.sanibonani.save.domain.usecase.auth.ObserveAuthSessionUseCase
import com.sanibonani.save.domain.usecase.group.GetManagedGroupsUseCase
import com.sanibonani.save.domain.usecase.member.GetMembershipsUseCase
import com.sanibonani.save.features.landing.presentation.LandingIntent
import com.sanibonani.save.features.landing.presentation.LandingViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The device log for a cold start carried three `landing_refresh_started` inside three seconds and
 * one `landing_refresh_success`: the screen appears, the session publishes and the connectivity
 * watcher all ask for the same four reads, and every ask cancelled the refresh already running. The
 * torn-down reads never answer, so the landing hero waited on the one pass that survived.
 *
 * A caller that wants the same answer as the pass already in flight is served by that pass; a
 * different actor, or an explicit retry, still starts its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LandingRefreshDeduplicationTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val getPlatformAnalytics = mockk<GetPlatformAnalyticsUseCase>()
    private val refreshPlatformConfig = mockk<RefreshPlatformConfigUseCase>()
    private val getMemberships = mockk<GetMembershipsUseCase>()
    private val getManagedGroups = mockk<GetManagedGroupsUseCase>()
    private val observeAuthSession = mockk<ObserveAuthSessionUseCase>()
    private val getCurrentUserRole = mockk<GetCurrentUserRoleUseCase>()
    private val isLoggedIn = mockk<IsLoggedInUseCase>()
    private val getAttributableActorId = mockk<GetAttributableActorIdUseCase>()
    private val networkService = mockk<NetworkConnectivityService>(relaxed = true)
    private val devSettings = mockk<DevSettingsService>(relaxed = true)

    private val sessionFlow = MutableStateFlow<AuthSession?>(null)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: LandingViewModel

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)

        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        Dispatchers.setMain(testDispatcher)

        every { observeAuthSession() } returns sessionFlow
        every { devSettings.settingsChanged } returns MutableSharedFlow()
        every { networkService.isOnline } returns flowOf(true)
        every { networkService.isAirplaneModeActive } returns flowOf(false)
        every { isLoggedIn() } returns true
        every { getAttributableActorId() } returns "u-1"
        coEvery { getCurrentUserRole() } returns UserRole.MEMBER
        coEvery { refreshPlatformConfig() } returns Result.success(PlatformConfig())
        coEvery { getMemberships(any()) } returns Result.success(emptyList())
        coEvery { getManagedGroups(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { getPlatformAnalytics() } returns Result.success(PlatformAnalytics())

        viewModel = LandingViewModel(
            getPlatformAnalytics,
            refreshPlatformConfig,
            getMemberships,
            getManagedGroups,
            observeAuthSession,
            getCurrentUserRole,
            isLoggedIn,
            getAttributableActorId,
            networkService,
            devSettings
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `a session republished for the same actor does not restart the refresh`() = runTest {
        var reads = 0
        coEvery { getPlatformAnalytics() } answers {
            reads++
            Result.success(PlatformAnalytics())
        }

        // The init pass and the first session publication want the same landing screen, so one
        // refresh has to answer both.
        sessionFlow.value = AuthSession(userId = "u-1", accessToken = "token-a")
        drain()
        val afterSignIn = reads
        assertTrue("the landing has to have read at least once", afterSignIn >= 1)

        // Same member, renewed token: distinctUntilChanged on the token lets this through, and the
        // role guard used to trip only for admins, so a member re-refreshed on every publication.
        sessionFlow.value = AuthSession(userId = "u-1", accessToken = "token-b")
        drain()

        assertEquals(
            "a renewed token for the member already on screen is not a new landing screen",
            afterSignIn,
            reads
        )
    }

    @Test
    fun `a refresh already running for this actor answers the next caller`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        coEvery { getPlatformAnalytics() } coAnswers {
            reads++
            gate.await()
            Result.success(PlatformAnalytics())
        }

        viewModel.refreshData()
        runCurrent()
        // The screen's connectivity watcher asks while that pass is still reading.
        viewModel.refreshData()
        runCurrent()

        gate.complete(Unit)
        drain()

        assertEquals(
            "the second caller has to be served by the pass already running",
            1,
            reads
        )
        assertTrue(
            "and the pass that was left alone is the one that paints the hero",
            viewModel.state.value.analytics.getOrNull() != null
        )
    }

    @Test
    fun `a retry the admin asks for starts its own refresh`() = runTest {
        var reads = 0
        coEvery { getPlatformAnalytics() } answers {
            reads++
            Result.success(PlatformAnalytics())
        }

        viewModel.refreshData()
        drain()
        assertEquals(1, reads)

        viewModel.onIntent(LandingIntent.Refresh)
        drain()

        assertEquals(
            "a caller that asked for fresh data must not be quietened by a pass that already finished",
            2,
            reads
        )
    }

    /**
     * The refresh's legs run on IO, so the scheduler alone cannot prove a leg was reached; give the
     * real threads a turn and drain again.
     */
    private suspend fun TestScope.drain() {
        advanceUntilIdle()
        Thread.sleep(120)
        advanceUntilIdle()
    }
}
