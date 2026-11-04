package com.sanibonani.save.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.sanibonani.save.domain.model.SyncStatus
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.system.SyncRepository
import com.sanibonani.save.domain.usecase.auth.IsLoggedInUseCase
import com.sanibonani.save.domain.usecase.system.CheckBackendReachabilityUseCase
import com.sanibonani.save.domain.usecase.system.ObserveSyncStatusUseCase
import com.sanibonani.save.domain.usecase.system.SyncAllDataUseCase
import com.sanibonani.save.features.common.presentation.SplashViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The bootloader renders `connectionError` as its retry screen and MainActivity keeps the app on that
 * screen while it is set - but the splash probe only ever logged a refusal, so a device with neither a
 * session nor a backend was dropped into an app that could not answer, with no retry offered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SplashConnectionVerdictTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val supabaseRepo = mockk<SupabaseRepository>(relaxed = true)
    private val syncRepo = mockk<SyncRepository>(relaxed = true)
    private val reachability = mockk<CheckBackendReachabilityUseCase>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { syncRepo.syncStatus } returns MutableStateFlow(SyncStatus.Idle)
        every { supabaseRepo.isLoggedIn } returns false
        coEvery { syncRepo.syncAllData() } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private fun newViewModel() = SplashViewModel(
        isLoggedIn = IsLoggedInUseCase(supabaseRepo),
        observeSyncStatus = ObserveSyncStatusUseCase(syncRepo),
        syncAllData = SyncAllDataUseCase(syncRepo),
        checkBackendReachability = reachability
    )

    @Test
    fun `a startup with no session and no backend reaches the retry screen`() = runTest {
        coEvery { reachability(any()) } returns false

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertNotNull(
            "the bootloader only shows its retry button while connectionError is set",
            viewModel.connectionError.value
        )
        assertTrue("and the branding wait still finishes", !viewModel.isConnecting.value)
    }

    @Test
    fun `the retry tap clears the warning once the backend answers`() = runTest {
        coEvery { reachability(any()) } returns false
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertNotNull(viewModel.connectionError.value)

        coEvery { reachability(any()) } returns true
        viewModel.checkConnection()
        advanceUntilIdle()

        assertNull(viewModel.connectionError.value)
        assertTrue(!viewModel.isConnecting.value)
    }

    @Test
    fun `a signed-in device with an unanswerable probe is not held on the splash`() = runTest {
        // The probe returns the fallback for a session it cannot test, so offline-first startup
        // continues on the cache and must not raise the retry screen.
        every { supabaseRepo.isLoggedIn } returns true
        coEvery { reachability(fallbackResult = true) } returns true

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertNull(viewModel.connectionError.value)
        assertTrue(!viewModel.isConnecting.value)
    }
}
