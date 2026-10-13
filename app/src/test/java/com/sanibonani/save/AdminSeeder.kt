package com.sanibonani.save

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.CodeVerifierCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Ignore
import org.junit.Test

/**
 * Run this test to seed the Platform Admin account.
 */
class AdminSeeder {

    @Ignore("Manual utility, not a unit test")
    @Test
    fun seedPlatformAdmin() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            val supabaseUrl = "https://prosbbknupoexgzjwrwr.supabase.co"
            val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InByb3NiYmtudXBvZXhnemp3cndyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM1NDE4MDUsImV4cCI6MjA4OTExNzgwNX0.CJs8oWt0Q3quK8FkyaldwyOg-sXiU25rFaBCh5Mi2tE"

            val client = createSupabaseClient(supabaseUrl, supabaseKey) {
                install(Auth) {
                    sessionManager = object : SessionManager {
                        override suspend fun saveSession(session: io.github.jan.supabase.auth.user.UserSession) {}
                        override suspend fun loadSession(): io.github.jan.supabase.auth.user.UserSession = error("No session")
                        override suspend fun deleteSession() {}
                    }
                    codeVerifierCache = object : CodeVerifierCache {
                        override suspend fun saveCodeVerifier(codeVerifier: String) {}
                        override suspend fun loadCodeVerifier(): String? = null
                        override suspend fun deleteCodeVerifier() {}
                    }
                }
            }

            val email = "torrymsimango@gmail.com"
            val password = "torry123M"
            val fullName = "torry123"

            println("Attempting to register Platform Admin: $email")

            try {
                client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                    data = buildJsonObject {
                        put("full_name", fullName)
                        put("role", "platform_admin")
                    }
                }
                println("Successfully created Platform Admin!")
            } catch (e: Exception) {
                println("Error (might already exist): ${e.message}")
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
