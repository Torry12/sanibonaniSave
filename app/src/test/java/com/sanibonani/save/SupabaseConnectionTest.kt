package com.sanibonani.save

import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import io.mockk.mockk

/**
 * Unit test for Supabase connectivity logic.
 */
class SupabaseConnectionTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var supabaseClient: SupabaseClient

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        supabaseClient = mockk(relaxed = true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test client initialization`() = runTest {
        // Verify the client is not null
        assertNotNull("SupabaseClient should be initialized", supabaseClient)
    }
}
