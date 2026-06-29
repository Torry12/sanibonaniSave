package com.sanibonani.save.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.di.TestAppModule
import com.sanibonani.save.di.TestAuthSessionController
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.usecase.CreateGroupUseCase
import com.sanibonani.save.domain.usecase.UpdateGroupSettingsUseCase
import com.sanibonani.save.domain.usecase.groups.GenerateStandardConstitutionUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.viewmodel.AdminSettingsViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AdminSettingsViewModelTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var groupRepo: GroupRepository

    @Inject
    lateinit var supabaseRepo: SupabaseRepository

    @Inject
    lateinit var createGroupUseCase: CreateGroupUseCase

    @Inject
    lateinit var updateGroupSettingsUseCase: UpdateGroupSettingsUseCase

    @Inject
    lateinit var generateStandardConstitutionUseCase: GenerateStandardConstitutionUseCase

    @Inject
    lateinit var adminContextCacheService: AdminGroupContextCacheService

    lateinit var viewModel: AdminSettingsViewModel

    @Before
    fun init() {
        TestAppModule.resetMockState()
        TestAuthSessionController.reset()
        hiltRule.inject()
        viewModel = AdminSettingsViewModel(
            adminContextCacheService = adminContextCacheService,
            updateGroupSettingsUseCase = updateGroupSettingsUseCase,
            generateStandardConstitutionUseCase = generateStandardConstitutionUseCase,
            db = (groupRepo as com.sanibonani.save.data.repository.GroupRepositoryImpl).getDb() // Hack to get DB or just use @Inject if possible
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
    fun testSettingsUpdateFlow() = runBlocking {
        val adminId = ensureAuthenticated()
        val groupId = createGroupUseCase(
            group = Group(name = "Settings Test", adminUserId = adminId, joiningFee = 100.0),
            adminEmail = "test@example.com",
            adminFullName = "Admin",
            adminPhone = "0111111111"
        ).getOrThrow()

        adminContextCacheService.selectGroup(groupId)
        adminContextCacheService.refreshContext()

        // Verify initial state
        val initialState = viewModel.state.first { it.group?.id == groupId }
        assertEquals("100.0", initialState.settings.joiningFee)

        // Update setting
        viewModel.updateSetting("joiningFee", "250.0")
        assertEquals("250.0", viewModel.state.value.settings.joiningFee)

        // Save
        viewModel.saveSettings()
        
        // Verify success
        val savedState = viewModel.state.first { it.saveSuccess }
        assertTrue(savedState.saveSuccess)

        // Verify cache updated
        val updatedGroup = groupRepo.getGroupById(groupId).getOrThrow()
        assertEquals(250.0, updatedGroup.joiningFee, 0.01)
    }
}
