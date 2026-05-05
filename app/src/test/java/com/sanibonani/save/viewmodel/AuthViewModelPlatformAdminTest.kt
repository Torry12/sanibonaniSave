package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.CredentialsRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.utils.PlatformAdminAuthPolicy
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.service.UserProfileCacheService
import io.mockk.*
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelPlatformAdminTest {

    private companion object {
        const val TEST_PLATFORM_ADMIN_PASSWORD = "torry123M"
    }

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val credentialsRepo = mockk<CredentialsRepository>(relaxed = true)
    private val adminCache = mockk<AdminGroupContextCacheService>(relaxed = true)
    private val memberCache = mockk<MemberGroupContextCacheService>(relaxed = true)
    private val userProfileCacheService = mockk<UserProfileCacheService>(relaxed = true)

    private val sessionStatusFlow = MutableStateFlow<SessionStatus>(SessionStatus.NotAuthenticated(false))
    
    private lateinit var viewModel: AuthViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        
        Dispatchers.setMain(testDispatcher)

        every { supabaseRepo.sessionStatus } returns sessionStatusFlow

        viewModel = AuthViewModel(
            supabaseRepo,
            credentialsRepo,
            adminCache,
            memberCache,
            userProfileCacheService
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `prefillPlatformAdmin sets canonical email and clears password`() = runTest {
        viewModel.prefillPlatformAdmin()
        
        val state = viewModel.state.value
        assertEquals(PlatformAdminAuthPolicy.EMAIL, state.email)
        assertEquals("", state.password)
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
    fun `observeSession resolves PLATFORM_ADMIN role for admin email`() = runTest {
        every { supabaseRepo.currentUserId } returns "admin_uid"
        coEvery { supabaseRepo.getUserRole() } returns UserRole.PLATFORM_ADMIN

        sessionStatusFlow.value = SessionStatus.Authenticated(mockk())
        advanceUntilIdle()

        assertEquals(UserRole.PLATFORM_ADMIN, viewModel.state.value.userRole)
    }
}
