package com.sanibonani.save.simulation

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.sanibonani.save.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import org.junit.Ignore
import org.junit.Assert.assertTrue
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSimulationTest {

    private val url = BuildConfig.SUPABASE_URL_ONLINE
    private val key = BuildConfig.SUPABASE_ANON_KEY_ONLINE
    private val adminEmail = "torrymsimango@gmail.com"
    private val adminPass = BuildConfig.PLATFORM_ADMIN_PASSWORD

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Ignore("Manual simulation utility; requires live Supabase backend")
    @Test
    fun testDeepSimulation() = runBlocking {
        val runner = LiveSimulationRunner(url, key)
        try {
            val result = runner.runDeepHistoricalActivitySimulation(adminEmail, adminPass)
            assertTrue("Simulation should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        } finally {
            runner.close()
        }
    }
}
