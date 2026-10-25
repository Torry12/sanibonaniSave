package com.sanibonani.save.viewmodel

import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.auth.*
import com.sanibonani.save.features.admin.presentation.PlatformAdminViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityAccessTest {

    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { supabaseRepo.sessionFlow } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `PlatformAdminViewModel blocks access for non-admin user`() = runTest {
        coEvery { supabaseRepo.getUserRole() } returns UserRole.MEMBER

        val viewModel = PlatformAdminViewModel(supabaseRepo)

        advanceUntilIdle()

        assertFalse("ViewModel should block access for non-admin roles", viewModel.state.value.isAuthorized)
    }

    @Test
    fun `PlatformAdminViewModel allows access for admin user`() = runTest {
        coEvery { supabaseRepo.getUserRole() } returns UserRole.PLATFORM_ADMIN

        val viewModel = PlatformAdminViewModel(supabaseRepo)

        advanceUntilIdle()

        assertTrue("ViewModel should allow access for Platform Admin role", viewModel.state.value.isAuthorized)
    }
}
