package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.auth.*
import com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy
import com.sanibonani.save.service.*
import com.sanibonani.save.data.remote.SupabaseConnectionManager
import com.sanibonani.save.data.sync.RealtimeSyncManager
import com.sanibonani.save.features.common.presentation.LoadStateQueue
import com.sanibonani.save.features.auth.presentation.AuthViewModel
import io.mockk.*
import com.sanibonani.save.domain.model.AuthSessionStatus
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
class AuthViewModelPlatformAdminTest {

    private companion object {
        const val TEST_PLATFORM_ADMIN_PASSWORD = "TestAdminPass1"
    }

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

    private val sessionStatusFlow = MutableStateFlow<com.sanibonani.save.domain.model.AuthSessionStatus>(com.sanibonani.save.domain.model.AuthSessionStatus.NotAuthenticated(false))

    private lateinit var viewModel: AuthViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0

        mockkStatic(FirebaseCrashlytics::class)
        val mockCrashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns mockCrashlytics

        Dispatchers.setMain(testDispatcher)

        every { connManager.currentClient } returns MutableStateFlow(null)
        every { supabaseRepo.sessionStatus } returns sessionStatusFlow
        every { supabaseRepo.sessionFlow } returns MutableStateFlow(null)

        viewModel = AuthViewModel(
            supabaseRepo,
            userProfileRepo,
            credentialsRepo,
            adminCache,
            memberCache,
            userProfileCacheService,
            connManager,
            realtimeSyncManager,
            loadQueue,
            mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }


    @Test
    fun `signIn as Platform Admin uses provided password directly`() = runTest {
        val adminEmail = PlatformAdminAuthPolicy.EMAIL
        val password = TEST_PLATFORM_ADMIN_PASSWORD

        viewModel.updateEmail(adminEmail)
        viewModel.updatePasswordState(password)

        coEvery { supabaseRepo.signIn(adminEmail, password) } returns Result.success(Unit)

        viewModel.signIn()
        advanceUntilIdle()

        coVerify { supabaseRepo.signIn(adminEmail, password) }
    }

    @Test
    fun `signIn normalizes email but preserves exact password`() = runTest {
        val rawEmail = "  Admin@Example.com  "
        val normalizedEmail = "admin@example.com"
        val exactPassword = "  P@ssword With Spaces  "

        viewModel.updateEmail(rawEmail)
        viewModel.updatePasswordState(exactPassword)
        coEvery { supabaseRepo.signIn(normalizedEmail, exactPassword) } returns Result.success(Unit)

        viewModel.signIn()
        advanceUntilIdle()

        coVerify(exactly = 1) { supabaseRepo.signIn(normalizedEmail, exactPassword) }
    }

    @Test
    fun `signIn with invalid email does not call repository`() = runTest {
        viewModel.updateEmail("invalid_email")
        viewModel.updatePasswordState("validPassword123")

        viewModel.signIn()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error?.contains("valid email", ignoreCase = true) == true)
        coVerify(exactly = 0) { supabaseRepo.signIn(any(), any()) }
    }
}
