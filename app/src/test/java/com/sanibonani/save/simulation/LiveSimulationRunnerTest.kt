package com.sanibonani.save.simulation

import android.util.Log
import com.sanibonani.save.BuildConfig
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSimulationRunnerTest {

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val url = BuildConfig.SUPABASE_URL_ONLINE
    private val key = BuildConfig.SUPABASE_ANON_KEY_ONLINE
    private val adminEmail = "torrymsimango@gmail.com"
    private val adminPass = BuildConfig.PLATFORM_ADMIN_PASSWORD

    @org.junit.Ignore("Live network simulation test requiring remote Supabase server connection and valid admin credentials")
    @Test
    fun runLiveSimulationOnJVM() = runBlocking {
        // Mock Log
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        val runner = LiveSimulationRunner(url, key)
        try {
            val result = runner.runDeepHistoricalActivitySimulation(adminEmail, adminPass)
            assertTrue("Simulation should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        } finally {
            runner.close()
        }
    }

    @org.junit.Ignore("Live network simulation test requiring remote Supabase server connection and valid admin credentials")
    @Test
    fun runLiveFinancialFlowSimulationOnJVM() = runBlocking {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        val runner = LiveSimulationRunner(url, key)
        try {
            val result = runner.runLiveFinancialFlowSimulation(adminEmail, adminPass)
            assertTrue("Live Financial Flow simulation should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        } finally {
            runner.close()
        }
    }
}
