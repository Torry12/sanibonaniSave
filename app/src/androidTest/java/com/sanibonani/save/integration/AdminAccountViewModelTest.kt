package com.sanibonani.save.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.di.TestAppModule
import com.sanibonani.save.di.TestAuthSessionController
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.usecase.CreateGroupUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.viewmodel.AdminAccountViewModel
import com.sanibonani.save.viewmodel.AuthViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AdminAccountViewModelTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var supabaseRepo: SupabaseRepository

    @Inject
    lateinit var createGroupUseCase: CreateGroupUseCase

    @Inject
    lateinit var adminContextCacheService: AdminGroupContextCacheService

    @Inject
    lateinit var authViewModel: AuthViewModel

    lateinit var viewModel: AdminAccountViewModel

    @Before
    fun init() {
        TestAppModule.resetMockState()
        TestAuthSessionController.reset()
        hiltRule.inject()
        viewModel = AdminAccountViewModel(
            adminContextCacheService = adminContextCacheService,
            authViewModel = authViewModel
        )
    }

    private suspend fun ensureAuthenticated(): String {
        val email = "test-admin-${UUID.randomUUID()}@example.com"
        val password = "Password123!"
        supabaseRepo.adminSignUp(email, password, emptyMap(), confirm = true).getOrThrow()
        supabaseRepo.signIn(email, password).getOrThrow()
        return supabaseRepo.currentUserId ?: throw IllegalStateException("Failed to sign in")
    }

    @Test
    fun testAccountDataLoading() = runBlocking {
        val adminId = ensureAuthenticated()
        val groupId = createGroupUseCase(
            group = Group(name = "Account Test", adminUserId = adminId),
            adminEmail = "test@example.com",
            adminFullName = "Admin",
            adminPhone = "0111111111"
        ).getOrThrow()

        adminContextCacheService.selectGroup(groupId)
        adminContextCacheService.refreshContext()

        // Verify state has the group
        val state = viewModel.state.first { it.group?.id == groupId }
        assertEquals("Account Test", state.group?.name)
    }

    @Test
    fun testLogoutFlow() = runBlocking {
        ensureAuthenticated()
        
        viewModel.logout()
        
        // AuthViewModel should reflect logged out state
        val authState = authViewModel.state.first { !it.isLoggedIn }
        assertFalse(authState.isLoggedIn)
    }
}
