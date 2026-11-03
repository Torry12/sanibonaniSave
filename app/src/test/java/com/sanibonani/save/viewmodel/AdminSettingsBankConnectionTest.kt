package com.sanibonani.save.viewmodel

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.BankConnection
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.payment.BankRepository
import com.sanibonani.save.domain.repository.system.SyncRepository
import com.sanibonani.save.domain.usecase.group.UpdateGroupSettingsUseCase
import com.sanibonani.save.domain.usecase.payment.LinkBankAccountUseCase
import com.sanibonani.save.features.admin.presentation.AdminSettingsViewModel
import com.sanibonani.save.service.AdminGroupContextCacheService
import com.sanibonani.save.service.CachedAdminGroupContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The Settings tab renders "no bank account linked" from `bankConnection == null`, and the
 * observation wrote `res.getOrNull()` for every emission - so a stream that failed answered the same
 * way as a group that genuinely has no link. An account that IS linked gets presented as missing,
 * which invites the admin to start a second linking flow against live banking.
 *
 * An emission that succeeds with null is still the honest "not linked" answer, and that path is
 * pinned here too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminSettingsBankConnectionTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val bankRepo = mockk<BankRepository>()
    private val cacheService = mockk<AdminGroupContextCacheService>(relaxed = true)
    private val contextsFlow = MutableStateFlow<Map<String, CachedAdminGroupContext>>(emptyMap())
    private val selectedGroupIdFlow = MutableStateFlow<String?>("g-1")
    private val connectionFlow = MutableStateFlow<Result<BankConnection?>>(Result.success(null))

    private val linked = BankConnection(
        id = "bc-1",
        groupId = "g-1",
        providerId = "flywave",
        linkedAt = "2026-01-04T09:00:00Z"
    )

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AdminSettingsViewModel

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
        every { FirebaseCrashlytics.getInstance() } returns mockk<FirebaseCrashlytics>(relaxed = true)

        Dispatchers.setMain(testDispatcher)
        every { cacheService.contexts } returns contextsFlow
        every { cacheService.selectedGroupId } returns selectedGroupIdFlow
        every { bankRepo.observeBankConnection("g-1") } returns connectionFlow

        viewModel = AdminSettingsViewModel(
            cacheService,
            bankRepo,
            mockk<LinkBankAccountUseCase>(relaxed = true),
            mockk<UpdateGroupSettingsUseCase>(relaxed = true),
            mockk<SyncRepository>(relaxed = true),
            mockk<SupabaseRepository>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun hydrateGroup() {
        contextsFlow.value = mapOf(
            "g-1" to CachedAdminGroupContext(
                group = Group(id = "g-1", name = "Test Group"),
                members = emptyList(),
                feeStatus = AdminFeeState.DUE,
                isFullyHydrated = true
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `a linked account is shown while the read answers`() = runTest {
        connectionFlow.value = Result.success(linked)
        hydrateGroup()

        assertEquals(linked, viewModel.state.value.bankConnection)
    }

    @Test
    fun `a failed read cannot present a linked account as unlinked`() = runTest {
        connectionFlow.value = Result.success(linked)
        hydrateGroup()
        assertEquals(linked, viewModel.state.value.bankConnection)

        connectionFlow.value = Result.failure(IOException("bank connection read failed"))
        advanceUntilIdle()

        assertEquals(
            "an unread stream is not evidence that the account is unlinked",
            linked,
            viewModel.state.value.bankConnection
        )
    }

    @Test
    fun `an answered absence of a link is still recorded`() = runTest {
        connectionFlow.value = Result.success(linked)
        hydrateGroup()
        assertEquals(linked, viewModel.state.value.bankConnection)

        connectionFlow.value = Result.success(null)
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.bankConnection)
    }
}
