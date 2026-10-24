package com.sanibonani.save.simulation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in live integration scenario, excluded from ordinary JVM unit tests. */
class ManualLiveSimulationTest {

    @Test
    fun runConfiguredSimulation() = runBlocking {
        assumeTrue("Run only through :simulation:run", System.getProperty("sanibonani.simulation.manual") == "true")

        val url = requiredConfiguration("SUPABASE_URL")
        val key = requiredConfiguration("SUPABASE_ANON_KEY")
        val adminEmail = requiredConfiguration("PLATFORM_ADMIN_EMAIL")
        val adminPassword = requiredConfiguration("PLATFORM_ADMIN_PASS")
        val mode = System.getProperty("sanibonani.simulation.mode")?.lowercase() ?: "deep"

        val runner = LiveSimulationRunner(url, key)
        try {
            val result = when (mode) {
                "deep" -> runner.runDeepHistoricalActivitySimulation(adminEmail, adminPassword)
                "flow" -> runner.runLiveFinancialFlowSimulation(adminEmail, adminPassword)
                else -> error("Unknown simulation mode '$mode'. Available: deep, flow")
            }
            assertTrue("Simulation failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        } finally {
            runner.close()
        }
    }

    private fun requiredConfiguration(name: String): String =
        System.getProperty("sanibonani.simulation.$name")?.takeIf(String::isNotBlank)
            ?: error("$name is required to run the live simulation")
}
