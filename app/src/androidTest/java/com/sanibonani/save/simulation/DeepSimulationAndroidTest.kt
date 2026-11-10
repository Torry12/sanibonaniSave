package com.sanibonani.save.simulation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.BuildConfig
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DeepSimulationAndroidTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private val url = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_ANON_KEY
    private val adminEmail = "torrymsimango@gmail.com"
    private val adminPass = BuildConfig.PLATFORM_ADMIN_PASSWORD

    // Real server walks on a shared device: alone testDeepSimulation took 35.8s, in a full suite it
    // overran a 60s latch. The success assertion below, not this budget, decides the result.
    private val simulationBudgetSeconds = 180L

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun testDeepSimulation() {
        val latch = CountDownLatch(1)
        var testResult: Result<String>? = null
        val runner = LiveSimulationRunner(url, key)

        val job = CoroutineScope(Dispatchers.IO).launch {
            println("SIM_TRACE: Android Deep Simulation Started")
            try {
                testResult = runner.runDeepHistoricalActivitySimulation(adminEmail, adminPass)
                println("SIM_TRACE: Result: $testResult")
            } finally {
                runner.close()
                latch.countDown()
            }
        }

        val completed = latch.await(simulationBudgetSeconds, TimeUnit.SECONDS)
        // Left running, this coroutine's Hilt EntryPoint access is charged to whichever class is
        // active next, which read as InternetTest failing without ever making its request.
        if (!completed) job.cancel()
        assertTrue("Test timed out waiting for simulation completion", completed)
        val result = testResult ?: error("No result returned")
        assertTrue("Deep Simulation should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
    }

    @Test
    fun testFlowSimulation() {
        val latch = CountDownLatch(1)
        var testResult: Result<String>? = null
        val runner = LiveSimulationRunner(url, key)

        val job = CoroutineScope(Dispatchers.IO).launch {
            println("SIM_TRACE: Android Live Financial Flow Started")
            try {
                testResult = runner.runLiveFinancialFlowSimulation(adminEmail, adminPass)
                println("SIM_TRACE: Result: $testResult")
            } finally {
                runner.close()
                latch.countDown()
            }
        }

        val completed = latch.await(simulationBudgetSeconds, TimeUnit.SECONDS)
        if (!completed) job.cancel()
        assertTrue("Test timed out waiting for flow simulation completion", completed)
        val result = testResult ?: error("No result returned")
        assertTrue("Flow Simulation should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
    }
}
