package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
import com.sanibonani.save.features.common.presentation.UIEvent
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The login and sign-up forms carry two error surfaces: the banner under the fields, which also hosts
 * the "resend the confirmation link" affordance, and the per-field supporting text. Both were unread
 * by the ViewModel, so every rejection landed as a toast only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthLoginFailureSurfaceTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val userProfileRepo = mockk<UserProfileRepository>(relaxed = true)
    private val credentialsRepo = mockk<CredentialsRepository>(relaxed = true)
    private val adminCache = mockk<AdminGroupContextCacheService>(relaxed = true)
    private val memberCache = mockk<MemberGroupContextCacheService>(relaxed = true)
    private val userProfileCacheService = mockk<UserProfileCacheService>(relaxed = true)
    private val connManager = mockk<com.sanibonani.save.data.remote.SupabaseConnectionManager>(relaxed = true)
    private val realtimeSyncManager = mockk<com.sanibonani.save.data.sync.RealtimeSyncManager>(relaxed = true)
    private val loadQueue = mockk<LoadStateQueue>(relaxed = true)

    private val sessions = MutableStateFlow<com.sanibonani.save.domain.model.AuthSession?>(null)
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
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        every { connManager.currentClient } returns MutableStateFlow(null)
        every { supabaseRepo.sessionFlow } returns sessions
        coEvery { supabaseRepo.getUserRole() } returns com.sanibonani.save.domain.model.UserRole.MEMBER
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
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `a sign-in the server refused for an unconfirmed email offers the resend link`() = runTest {
        coEvery { supabaseRepo.signIn(any(), any()) } returns
            Result.failure(Exception("Email not confirmed"))
        viewModel.updateEmail("member@example.com")
        viewModel.updatePasswordState("Pass123!")

        viewModel.signIn()
        advanceUntilIdle()

        assertEquals("unverified", viewModel.state.value.errorType)
        assertEquals(
            "Please confirm your email address before signing in. Check your inbox for the confirmation link.",
            viewModel.state.value.error
        )
    }

    @Test
    fun `a sign-in refused for the wrong credentials does not offer the resend link`() = runTest {
        coEvery { supabaseRepo.signIn(any(), any()) } returns
            Result.failure(Exception("Invalid login credentials"))
        viewModel.updateEmail("member@example.com")
        viewModel.updatePasswordState("wrong-password")

        viewModel.signIn()
        advanceUntilIdle()

        assertNull(viewModel.state.value.errorType)
        assertNull(viewModel.state.value.error)
        val event = withTimeoutOrNull(2_000) { viewModel.events.first() }
        assertTrue("emitted event: $event", event is UIEvent.ShowError)
    }

    @Test
    fun `an email the form itself rejects is blamed on the email field`() = runTest {
        viewModel.updateEmail("not-an-email")

        viewModel.signIn()
        advanceUntilIdle()

        assertEquals(
            "Please enter a valid email address",
            viewModel.state.value.fieldErrors["email"]
        )
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `typing in a field clears the message it was blamed for`() = runTest {
        viewModel.updateEmail("not-an-email")
        viewModel.signIn()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.fieldErrors.containsKey("email"))

        viewModel.updateEmail("member@example.com")

        assertNull(viewModel.state.value.fieldErrors["email"])
    }

    @Test
    fun `each sign-up rejection is blamed on the field that caused it`() = runTest {
        viewModel.updateFullName("Bo")
        viewModel.signUp()
        advanceUntilIdle()
        assertEquals("Full Name must be at least 3 characters", viewModel.state.value.fieldErrors["fullName"])

        viewModel.updateFullName("Sipho Dlamini")
        viewModel.updateEmail("not-an-email")
        viewModel.signUp()
        advanceUntilIdle()
        assertEquals("Please enter a valid email address", viewModel.state.value.fieldErrors["email"])

        viewModel.updateEmail("sipho@example.com")
        viewModel.updatePasswordState("123")
        viewModel.updateConfirmPw("123")
        viewModel.signUp()
        advanceUntilIdle()
        assertTrue(
            "the password rule has to land under the password field: ${viewModel.state.value.fieldErrors}",
            viewModel.state.value.fieldErrors.containsKey("password")
        )

        viewModel.updatePasswordState("Passw0rd!2026")
        viewModel.updateConfirmPw("Passw0rd!2025")
        viewModel.signUp()
        advanceUntilIdle()
        assertEquals("Passwords do not match", viewModel.state.value.fieldErrors["confirmPw"])
    }

    @Test
    fun `a sign-up the server refused reports the failure without blaming a field`() = runTest {
        coEvery { supabaseRepo.signUp(any(), any(), any()) } returns
            Result.failure(Exception("Email already registered"))
        viewModel.updateFullName("Sipho Dlamini")
        viewModel.updateEmail("sipho@example.com")
        viewModel.updatePasswordState("Passw0rd!2026")
        viewModel.updateConfirmPw("Passw0rd!2026")

        viewModel.signUp()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.fieldErrors.isEmpty())
        val event = withTimeoutOrNull(2_000) { viewModel.events.first() }
        assertTrue("emitted event: $event", event is UIEvent.ShowError)
    }
}
