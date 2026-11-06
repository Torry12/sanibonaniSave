package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.MainDispatcherRule
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The device log for a cold start carried three `landing_refresh_started` inside three seconds and one
 * `landing_refresh_success`: the screen appears, the session publishes and the connectivity watcher all
 * ask for the same four reads, and every ask cancelled the refresh already running. The torn-down reads
 * never answer, so the landing hero waited on the one pass that survived.
 *
 * Two guards answer that, and each gets its own case here: a refresh is keyed to the actor it reads for,
 * so a second caller while one is in flight is served by it; and the session observer only answers a
 * publication that changes whether someone is signed in or as what. A caller that demands fresh data -
 * the retry, or a dev database switch - still starts its own pass.
 *
 * The read legs run on `Dispatchers.IO` inside a pass that arms its own 30-second timeout, so nothing
 * here waits on the wall clock for a leg to appear: each leg announces itself through a deferred and
 * stays suspended until the test releases it. `advanceUntilIdle()` cannot be used for the same reason -
 * it would jump that timeout and cancel the legs before any of them started.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LandingRefreshDeduplicationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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

    private val testDispatcher = mainDispatcherRule.dispatcher

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
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        unmockkAll()
    }

    /**
     * The session source is a parameter because the case that exercises the observer's guard needs a flow
     * that finishes: a never-completing one leaves `debounce`'s timer re-arming after every advance, and
     * runTest then spends its whole minute looking for a scheduler that will never go idle.
     */
    private fun buildViewModel(sessionSource: Flow<AuthSession?>): LandingViewModel {
        every { observeAuthSession() } returns sessionSource
        return mainDispatcherRule.track(LandingViewModel(
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
        ))
    }

    /** Two publications 400 ms apart for the same member, then the stream closes. */
    private fun twoSessionsForOneMember(): Flow<AuthSession?> = flow {
        emit(AuthSession(userId = "u-1", accessToken = "token-a"))
        delay(400)
        emit(AuthSession(userId = "u-1", accessToken = "token-b"))
    }

    @Test
    fun `a session republished for the same actor does not restart the refresh`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val firstRead = CompletableDeferred<Unit>()
        val reads = java.util.concurrent.atomic.AtomicInteger()
        coEvery { getPlatformAnalytics() } coAnswers {
            if (reads.incrementAndGet() == 1) firstRead.complete(Unit)
            gate.await()
            Result.success(PlatformAnalytics())
        }

        viewModel = buildViewModel(twoSessionsForOneMember())

        // The construction pass is the one reading; hold it there so the first publication arrives while
        // it is still in flight.
        runCurrent()
        firstRead.await()

        // token-a clears the debounce at +300ms and asks for the landing the pass already covers.
        advanceTimeBy(350)
        runCurrent()
        assertEquals(
            "a publication while a refresh is running is served by that refresh",
            1,
            reads.get()
        )

        gate.complete(Unit)
        settle()

        // token-b is a renewed token for the member already on screen: distinctUntilChanged lets it
        // through, and it used to trip a whole refresh because the role guard only ran for admins.
        advanceTimeBy(400)
        runCurrent()
        settle()

        assertEquals(
            "a renewed token for the member already on screen is not a new landing screen",
            1,
            reads.get()
        )
        assertTrue(
            "and the observer did see that session, so the guard is what kept the count down",
            viewModel.state.value.isLoggedIn
        )
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `a refresh already running for this actor answers the next caller`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val reads = java.util.concurrent.atomic.AtomicInteger()
        coEvery { getPlatformAnalytics() } coAnswers {
            reads.incrementAndGet()
            gate.await()
            Result.success(PlatformAnalytics())
        }

        viewModel = buildViewModel(flowOf(null))

        viewModel.refreshData()
        runCurrent()
        // The screen's connectivity watcher asks while that pass is still reading.
        viewModel.refreshData()
        runCurrent()

        gate.complete(Unit)
        settle()

        assertEquals(
            "the second caller has to be served by the pass already running",
            1,
            reads.get()
        )
        assertTrue(
            "and the pass that was left alone is the one that paints the hero",
            viewModel.state.value.analytics.getOrNull() != null
        )
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `a retry the admin asks for starts its own refresh`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val firstRead = CompletableDeferred<Unit>()
        val secondRead = CompletableDeferred<Unit>()
        val reads = java.util.concurrent.atomic.AtomicInteger()
        coEvery { getPlatformAnalytics() } coAnswers {
            when (reads.incrementAndGet()) {
                1 -> firstRead.complete(Unit)
                else -> secondRead.complete(Unit)
            }
            gate.await()
            Result.success(PlatformAnalytics())
        }

        viewModel = buildViewModel(flowOf(null))

        viewModel.refreshData()
        firstRead.await()
        assertEquals(
            "the pass already running is the one the first caller is served by",
            1,
            reads.get()
        )

        gate.complete(Unit)
        settle()

        // A forced pass replaces one that already finished, because the caller wants an answer that
        // post-dates its ask - the retry on the hero, or the database switch.
        viewModel.onIntent(LandingIntent.Refresh)
        secondRead.await()

        assertEquals(
            "a caller that asked for fresh data must not be quietened by a pass that already finished",
            2,
            reads.get()
        )
        gate.complete(Unit)
        settle()
        viewModel.viewModelScope.cancel()
    }

    /**
     * Runs what is queued, gives the IO threads a turn, then runs what their answers queued. Virtual time
     * is never advanced here, because that would fire the pass's own `withTimeoutOrNull(30_000)`.
     */
    private suspend fun TestScope.settle() {
        runCurrent()
        Thread.sleep(200)
        runCurrent()
    }
}
