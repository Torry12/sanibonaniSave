package com.sanibonani.save.simulation

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.sanibonani.save.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Ignore
import org.junit.Assert.assertTrue
import org.junit.Before

class LiveSimulationTest {

    private val url = BuildConfig.SUPABASE_URL_ONLINE
    private val key = BuildConfig.SUPABASE_ANON_KEY_ONLINE
    private val adminEmail = "torrymsimango@gmail.com"
    private val adminPass = BuildConfig.PLATFORM_ADMIN_PASSWORD

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
