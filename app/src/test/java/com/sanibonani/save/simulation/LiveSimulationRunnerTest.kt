package com.sanibonani.save.simulation

import android.util.Log
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

    private val url = "https://aszmodkqpafwtnfufmow.supabase.co"
    private val key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFzem1vZGtxcGFmd3RuZnVmbW93Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc2NDA2MzksImV4cCI6MjA5MzIxNjYzOX0.xmjdSHDGO8sm1cq_574yUxADfCWljtBKf94U-ReTxy8"
    private val adminEmail = "torrymsimango@gmail.com"
    private val adminPass = "torry123M"

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
