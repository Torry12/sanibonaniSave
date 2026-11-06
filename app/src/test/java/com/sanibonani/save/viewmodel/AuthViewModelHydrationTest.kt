package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.MainDispatcherRule
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
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.common.presentation.LoadStateQueue
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.service.UserProfileCacheService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    val mainDispatcherRule = MainDispatcherRule()

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
        testDispatcher = mainDispatcherRule.dispatcher

        every { connManager.currentClient } returns MutableStateFlow(null)
        every { supabaseRepo.sessionFlow } returns sessions
        coEvery { supabaseRepo.getUserRole() } returns UserRole.GROUP_ADMIN
        coEvery { userProfileRepo.getProfile(any()) } returns Result.success(null)

        viewModel = mainDispatcherRule.track(AuthViewModel(
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
        ))
    }

    @After
    fun tearDown() {
        // Drain while Main is still installed, otherwise a suspended collector resumes after
        // resetMain() and reports "Dispatchers.Main was accessed ...".
        testDispatcher.scheduler.advanceUntilIdle()
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
    fun `a member boot warms the member cache but not the admin cache`() = runTest {
        coEvery { supabaseRepo.getUserRole() } returns UserRole.MEMBER

        sessions.value = AuthSession(userId = "u1", email = "member@example.com", accessToken = "token")
        advanceUntilIdle()

        assertEquals(UserRole.MEMBER, viewModel.state.value.userRole)
        coVerify(exactly = 1) { memberCache.warmUpForUser("u1") }
        coVerify(exactly = 0) { adminCache.warmUpForUser(any()) }
    }

    @Test
    fun `an admin boot warms both context caches`() = runTest {
        sessions.value = AuthSession(userId = "u1", email = "admin@example.com", accessToken = "token")
        advanceUntilIdle()

        assertEquals(UserRole.GROUP_ADMIN, viewModel.state.value.userRole)
        coVerify(exactly = 1) { memberCache.warmUpForUser("u1") }
        coVerify(exactly = 1) { adminCache.warmUpForUser("u1") }
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

    @Test
    fun `a role refresh with no user id still resolves the role`() = runTest {
        every { supabaseRepo.currentUserId } returns null

        // The caller is the group-created callback. Refusing to run because the ViewModel never
        // captured an id left the creator on the role they held before creating the group.
        coEvery { supabaseRepo.getUserRole() } returns UserRole.GROUP_ADMIN
        viewModel.refreshUserRole()
        advanceUntilIdle()

        assertEquals(UserRole.GROUP_ADMIN, viewModel.state.value.userRole)
        assertNull("and the missing id is not invented", viewModel.state.value.userId)
    }

    @Test
    fun `a reset link the client refuses is reported and does not arm the password form`() = runTest {
        coEvery { supabaseRepo.handleDeepLink(any()) } returns
            Result.failure(IllegalStateException("Email link could not be verified"))

        viewModel.handleDeepLink("sanibonani://reset-password?token_hash=abc123&next=/update")
        advanceUntilIdle()

        // isResettingPassword also unlocks the auth-entry guards in NavUtils, so leaving it set on a
        // link that never verified would keep the app in a half-authenticated state indefinitely.
        assertTrue("a refused ticket cannot arm the guard", !viewModel.state.value.isResettingPassword)
        val event = withTimeoutOrNull(2_000) { viewModel.events.first() }
        assertTrue("emitted event: $event", event is UIEvent.ShowError)
    }

    @Test
    fun `a reset link the client accepts arms the password form with no error`() = runTest {
        coEvery { supabaseRepo.handleDeepLink(any()) } returns Result.success(Unit)

        viewModel.handleDeepLink("sanibonani://reset-password?token_hash=abc123&next=/update")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isResettingPassword)
        assertNull(withTimeoutOrNull(2_000) { viewModel.events.first() })
    }

    @Test
    fun `a callback link that fails is reported rather than only logged`() = runTest {
        coEvery { supabaseRepo.handleDeepLink(any()) } returns
            Result.failure(IllegalStateException("invalid flow state"))

        viewModel.handleDeepLink("sanibonani://auth/callback?code=xyz")
        advanceUntilIdle()

        val event = withTimeoutOrNull(2_000) { viewModel.events.first() }
        assertTrue("emitted event: $event", event is UIEvent.ShowError)
    }
}
