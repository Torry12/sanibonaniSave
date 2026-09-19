package com.sanibonani.save.simulation

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertTrue

class LiveSimulationTest {

    private val url = "https://aszmodkqpafwtnfufmow.supabase.co"
    private val key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFzem1vZGtxcGFmd3RuZnVmbW93Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc2NDA2MzksImV4cCI6MjA5MzIxNjYzOX0.xmjdSHDGO8sm1cq_574yUxADfCWljtBKf94U-ReTxy8"
    private val adminEmail = "torrymsimango@gmail.com"
    private val adminPass = "torry123M"

    @Test
    fun testDeepSimulation() = runBlocking {
        val runner = LiveSimulationRunner(url, key)
        try {
            val result = runner.runDeepHistoricalActivitySimulation(adminEmail, adminPass)
            assertTrue("Simulation should succeed", result.isSuccess)
        } finally {
            runner.close()
        }
    }
}
