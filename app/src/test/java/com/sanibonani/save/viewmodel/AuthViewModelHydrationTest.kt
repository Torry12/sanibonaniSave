package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.data.remote.SupabaseConnectionManager
import com.sanibonani.save.data.sync.RealtimeSyncManager
import com.sanibonani.save.domain.model.AuthSession
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.auth.CredentialsRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.auth.UserProfileRepository
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.auth.GetCurrentUserRoleUseCase
import com.sanibonani.save.domain.usecase.auth.GetSavedLoginCredentialsUseCase
import com.sanibonani.save.domain.usecase.auth.GetUserProfileUseCase
import com.sanibonani.save.domain.usecase.auth.HandleAuthDeepLinkUseCase
import com.sanibonani.save.domain.usecase.auth.ObserveAuthSessionUseCase
import com.sanibonani.save.domain.usecase.auth.RebuildConnectionsUseCase
import com.sanibonani.save.domain.usecase.auth.RegisterAccountUseCase
import com.sanibonani.save.domain.usecase.auth.RequestMagicLinkUseCase
import com.sanibonani.save.domain.usecase.auth.ResendVerificationEmailUseCase
import com.sanibonani.save.domain.usecase.auth.SetBiometricEnabledUseCase
import com.sanibonani.save.domain.usecase.auth.SignInUseCase
import com.sanibonani.save.domain.usecase.auth.UpdatePasswordUseCase
import com.sanibonani.save.features.auth.presentation.AuthViewModel
import com.sanibonani.save.features.common.presentation.LoadStateQueue
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Post-login routing must not fire on a role that has not been resolved yet: routing on the
 * pre-hydration default sends group admins to the member dashboard and then rebuilds the
 * back stack when the real role lands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelHydrationTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val userProfileRepo = mockk<UserProfileRepository>(relaxed = true)
    private val credentialsRepo = mockk<CredentialsRepository>(relaxed = true)
    private val adminCache = mockk<AdminGroupContextCacheService>(relaxed = true)
    private val memberCache = mockk<MemberGroupContextCacheService>(relaxed = true)
    private val userProfileCacheService = mockk<UserProfileCacheService>(relaxed = true)
    private val connManager = mockk<SupabaseConnectionManager>(relaxed = true)
    private val realtimeSyncManager = mockk<RealtimeSyncManager>(relaxed = true)
    private val loadQueue = mockk<LoadStateQueue>(relaxed = true)

    private val sessions = MutableStateFlow<AuthSession?>(null)
    private lateinit var testDispatcher: TestDispatcher

    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        // A fresh scheduler per test: the ViewModel's collectors outlive the test body, and a
        // shared scheduler lets one test's leaked coroutine fail the next one.
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        every { connManager.currentClient } returns MutableStateFlow(null)
        every { supabaseRepo.sessionFlow } returns sessions
        coEvery { supabaseRepo.getUserRole() } returns UserRole.GROUP_ADMIN
        coEvery { userProfileRepo.getProfile(any()) } returns Result.success(null)

        viewModel = AuthViewModel(
            ObserveAuthSessionUseCase(supabaseRepo),
            GetUserProfileUseCase(userProfileRepo),
            GetCurrentUserRoleUseCase(supabaseRepo),
            GetAttributableActorIdUseCase(supabaseRepo),
            GetSavedLoginCredentialsUseCase(credentialsRepo),
            SignInUseCase(supabaseRepo, credentialsRepo),
            RegisterAccountUseCase(supabaseRepo),
            ResendVerificationEmailUseCase(supabaseRepo),
            RequestMagicLinkUseCase(supabaseRepo),
            HandleAuthDeepLinkUseCase(supabaseRepo),
            UpdatePasswordUseCase(supabaseRepo),
            SetBiometricEnabledUseCase(credentialsRepo),
            RebuildConnectionsUseCase(connManager),
            adminCache,
            memberCache,
            userProfileCacheService,
            realtimeSyncManager,
            loadQueue,
            mockk(relaxed = true),
            mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        // Drain while Main is still installed, otherwise a suspended collector resumes after
        // resetMain() and reports "Dispatchers.Main was accessed ...".
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `a successful sign-in is not reported as logged in until the role is resolved`() = runTest {
        coEvery { supabaseRepo.signIn(any(), any()) } returns Result.success(Unit)
        viewModel.updateEmail("admin@example.com")
        viewModel.updatePasswordState("Pass123!")

        viewModel.signIn()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoggedIn)
        assertTrue(viewModel.state.value.isLoading)

        sessions.value = AuthSession(userId = "u1", email = "admin@example.com", accessToken = "token")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isLoggedIn)
        assertEquals(UserRole.GROUP_ADMIN, viewModel.state.value.userRole)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `hydrating a restored session routes on the resolved role`() = runTest {
        sessions.value = AuthSession(userId = "u1", email = "admin@example.com", accessToken = "token")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isLoggedIn)
        assertEquals(UserRole.GROUP_ADMIN, viewModel.state.value.userRole)
    }

    @Test
    fun `a context warmup failure still completes hydration with the resolved role`() = runTest {
        coEvery { memberCache.warmUpForUser(any()) } throws IllegalStateException("cache unavailable")

        sessions.value = AuthSession(userId = "u1", email = "admin@example.com", accessToken = "token")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isLoggedIn)
        assertEquals(UserRole.GROUP_ADMIN, viewModel.state.value.userRole)
    }

    @Test
    fun `a role lookup failure falls back to the least privileged role`() = runTest {
        coEvery { supabaseRepo.getUserRole() } throws IllegalStateException("metadata unavailable")

        sessions.value = AuthSession(userId = "u1", email = "member@example.com", accessToken = "token")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isLoggedIn)
        assertEquals(UserRole.MEMBER, viewModel.state.value.userRole)
    }

    @Test
    fun `refreshUserRole picks up an out-of-band elevation without a new session event`() = runTest {
        sessions.value = AuthSession(userId = "u1", email = "creator@example.com", accessToken = "token")
        coEvery { supabaseRepo.getUserRole() } returns UserRole.MEMBER
        advanceUntilIdle()
        assertEquals(UserRole.MEMBER, viewModel.state.value.userRole)

        // Creating a group elevates the creator server-side; the session object itself does
        // not change, so routing used to stay on the pre-creation MEMBER role.
        coEvery { supabaseRepo.getUserRole() } returns UserRole.GROUP_ADMIN
        viewModel.refreshUserRole()
        advanceUntilIdle()

        assertEquals(UserRole.GROUP_ADMIN, viewModel.state.value.userRole)
        assertTrue(viewModel.state.value.isLoggedIn)
    }
}
