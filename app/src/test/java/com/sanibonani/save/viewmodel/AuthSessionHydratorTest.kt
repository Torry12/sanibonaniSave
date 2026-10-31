package com.sanibonani.save.viewmodel

import android.util.Log
import com.sanibonani.save.domain.model.AuthSession
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.auth.UserProfileRepository
import com.sanibonani.save.domain.service.RealtimeSyncService
import com.sanibonani.save.domain.usecase.auth.GetCurrentUserRoleUseCase
import com.sanibonani.save.domain.usecase.auth.GetUserProfileUseCase
import com.sanibonani.save.domain.usecase.auth.ObserveAuthSessionUseCase
import com.sanibonani.save.features.auth.presentation.AuthSessionHydrator
import com.sanibonani.save.features.common.presentation.LoadStateQueue
import com.sanibonani.save.features.common.presentation.StepStatus
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.service.UserProfileCacheService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Every hydration step the bootloader renders has to end in a terminal state. A hydration cut
 * short between startStep and its terminal call used to leave the step Loading, which the
 * splash screen shows as an initialisation that never finishes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionHydratorTest {

    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val userProfileRepo = mockk<UserProfileRepository>(relaxed = true)
    private val adminCache = mockk<AdminGroupContextCacheService>(relaxed = true)
    private val memberCache = mockk<MemberGroupContextCacheService>(relaxed = true)
    private val userProfileCache = mockk<UserProfileCacheService>(relaxed = true)
    private val realtimeSync = mockk<RealtimeSyncService>(relaxed = true)

    private val sessions = MutableStateFlow<AuthSession?>(null)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)

        every { supabaseRepo.sessionFlow } returns sessions
        coEvery { supabaseRepo.getUserRole() } returns UserRole.GROUP_ADMIN
        coEvery { userProfileRepo.getProfile(any()) } returns Result.success(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun hydrator(loadQueue: LoadStateQueue) = AuthSessionHydrator(
        ObserveAuthSessionUseCase(supabaseRepo),
        GetUserProfileUseCase(userProfileRepo),
        GetCurrentUserRoleUseCase(supabaseRepo),
        adminCache,
        memberCache,
        userProfileCache,
        realtimeSync,
        loadQueue,
    )

    @Test
    fun `a hydration cut short leaves no step loading`() = runTest {
        val loadQueue = LoadStateQueue()
        // The warm-up never returns, so the run is still inside "Domain Context" when it is cancelled.
        coEvery { memberCache.warmUpForUser(any()) } coAnswers { awaitCancellation() }
        sessions.value = AuthSession(userId = "u1", email = "admin@example.com", accessToken = "token")

        val collecting = launch { hydrator(loadQueue).accountStatus().collect { } }
        advanceUntilIdle()

        val steps = loadQueue.steps.value
        assertTrue("the completed step must stay terminal", steps["User Profile"] is StepStatus.Success)
        assertTrue("the in-flight step is loading while cancelled", steps["Domain Context"] is StepStatus.Loading)

        collecting.cancelAndJoin()

        assertTrue(
            "a cancelled step must not stay Loading: ${loadQueue.steps.value}",
            loadQueue.steps.value["Domain Context"] is StepStatus.Error
        )
    }
}
