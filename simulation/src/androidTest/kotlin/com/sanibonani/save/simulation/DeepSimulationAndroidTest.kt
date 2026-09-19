package com.sanibonani.save.simulation

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeepSimulationAndroidTest {

    private val url = "https://aszmodkqpafwtnfufmow.supabase.co"
    private val key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFzem1vZGtxcGFmd3RuZnVmbW93Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc2NDA2MzksImV4cCI6MjA5MzIxNjYzOX0.xmjdSHDGO8sm1cq_574yUxADfCWljtBKf94U-ReTxy8"
    private val adminEmail = "torrymsimango@gmail.com"
    private val adminPass = "torry123M"

    @Test
    fun testDeepSimulation() = runBlocking {
        val runner = LiveSimulationRunner(url, key)
        try {
            val result = runner.runDeepHistoricalActivitySimulation(adminEmail, adminPass)
            assertTrue("Deep Simulation should succeed", result.isSuccess)
        } finally {
            runner.close()
        }
    }

    @Test
    fun testFlowSimulation() = runBlocking {
        val runner = LiveSimulationRunner(url, key)
        try {
            val result = runner.runLiveFinancialFlowSimulation(adminEmail, adminPass)
            assertTrue("Flow Simulation should succeed", result.isSuccess)
        } finally {
            runner.close()
        }
    }
}
