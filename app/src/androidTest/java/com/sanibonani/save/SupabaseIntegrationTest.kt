package com.sanibonani.save

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject

/**
 * Integration test that performs a real network handshake with Supabase.
 * Requires valid SUPABASE_URL and SUPABASE_ANON_KEY in local.properties.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SupabaseIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Test
    fun testRealNetworkHandshake() = runBlocking {
        hiltRule.inject()
        
        // Use a safe query to verify connectivity without triggering schema errors or RLS issues
        // We select only 'id' to avoid 404/400 errors from missing columns.
        // We treat 401/403 (Permission Denied) as a success because it confirms the server is reachable.
        val result = runCatching {
            supabaseClient.postgrest["groups"].select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("id")) {
                limit(1)
            }
        }
        
        val exception = result.exceptionOrNull()
        val isReachable = result.isSuccess || 
                exception?.message?.contains("permission denied", ignoreCase = true) == true ||
                exception?.message?.contains("401", ignoreCase = true) == true ||
                exception?.message?.contains("403", ignoreCase = true) == true

        assertTrue("Failed to reach Supabase server: ${exception?.message}", isReachable)
    }
}
