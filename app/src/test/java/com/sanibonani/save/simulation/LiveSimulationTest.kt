package com.sanibonani.save.simulation

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Ignore
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LiveSimulationTest {

    private val url = "https://aszmodkqpafwtnfufmow.supabase.co"
    private val key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFzem1vZGtxcGFmd3RuZnVmbW93Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc2NDA2MzksImV4cCI6MjA5MzIxNjYzOX0.xmjdSHDGO8sm1cq_574yUxADfCWljtBKf94U-ReTxy8"
    private val adminEmail = "torrymsimango@gmail.com"
    private val adminPass = "torry123M"

    @Before
    fun setup() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
    }

    @Ignore("Manual simulation utility; requires live Supabase backend")
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
