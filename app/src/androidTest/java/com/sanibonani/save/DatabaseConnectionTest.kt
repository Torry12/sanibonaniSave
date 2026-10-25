package com.sanibonani.save

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.data.local.SanibonaniDatabase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DatabaseConnectionTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var database: SanibonaniDatabase

    @Test
    fun testRoomDatabaseConnection() {
        hiltRule.inject()
        assertNotNull("Room database should be injected", database)

        // Simple query to verify database is open and readable
        val cursor = database.query("SELECT 1", null)
        assertTrue("Should be able to execute simple query on Room", cursor.moveToFirst())
        cursor.close()
    }

    @Test
    fun testSupabaseConnection() = runBlocking {
        hiltRule.inject()
        assertNotNull("Supabase client should be injected", supabaseClient)
        // More lenient check for mock URL
        assertTrue("URL should contain 127.0.0.1", supabaseClient.supabaseUrl.contains("127.0.0.1"))

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
