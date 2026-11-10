package com.sanibonani.save.viewmodel

import com.sanibonani.save.MainDispatcherRule
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.usecase.group.WarmPublicGroupsCacheUseCase
import com.sanibonani.save.features.common.presentation.StartupDataViewModel
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * The startup map warmup is a cache-only read with a seven second backstop, and both the timeout and
 * the failure were discarded: a discovery map that later came up empty could not be told apart from
 * a server defect. Nothing renders this path, so the record has to be the log.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupDataWarmupTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val warmPublicGroupsCache = mockk<WarmPublicGroupsCacheUseCase>()
    private val testDispatcher = mainDispatcherRule.dispatcher

    @After
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `a warmup that runs past its budget is recorded`() = runTest {
        coEvery { warmPublicGroupsCache() } coAnswers { delay(20_000L) }

        mainDispatcherRule.track(StartupDataViewModel(warmPublicGroupsCache)).preloadMapData()
        advanceUntilIdle()

        assertTrue(
            "the abandoned warmup has to leave a trace",
            AppLogger.getBreadcrumbs().any { it.contains("map warmup did not finish") }
        )
    }

    @Test
    fun `a warmup the cache refuses is recorded`() = runTest {
        coEvery { warmPublicGroupsCache() } throws IOException("room unavailable")

        mainDispatcherRule.track(StartupDataViewModel(warmPublicGroupsCache)).preloadMapData()
        advanceUntilIdle()

        assertTrue(
            "safeRunCatching already logs a refusal, and the warmup must not look like a success",
            AppLogger.getBreadcrumbs().any { it.contains("room unavailable") }
        )
    }
}
