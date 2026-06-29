package com.sanibonani.save

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Live credential verification utility.
 *
 * Connects to the real Supabase project and signs in with every platform
 * account.  Run manually from Android Studio (right-click → Run) whenever
 * you need to verify that credentials are working end-to-end.
 *
 * NOT an automated unit-test — annotated with @Ignore so CI skips it.
 *
 * ┌────────────────────────────────────┬──────────────────┬──────────────────┐
 * │ Account                            │ Email            │ Password         │
 * ├────────────────────────────────────┼──────────────────┼──────────────────┤
 * │ Platform Admin (torrymsimango)     │ torrymsimango@…  │ torry123M        │
 * │ Group Admin 1                      │ admin1@test.com  │ password123      │
 * │ Group Admin 2                      │ admin2@test.com  │ password123      │
 * │ Member 1 – Active                  │ member1@test.com │ password123      │
 * │ Member 2 – Probation               │ member2@test.com │ password123      │
 * │ Member 3 – Active Senior           │ member3@test.com │ password123      │
 * │ Member 4 – Suspended/Pending       │ member4@test.com │ password123      │
 * └────────────────────────────────────┴──────────────────┴──────────────────┘
 */
@Ignore("Manual live-credential test — not for automated CI")
class CredentialLoginTest {

    // ── Supabase configuration ──────────────────────────────────────────────
    private val supabaseUrl = "https://prosbbknupoexgzjwrwr.supabase.co"
    private val anonKey =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
        ".eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InByb3NiYmtudXBvZXhnemp3cndyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM1NDE4MDUsImV4cCI6MjA4OTExNzgwNX0" +
        ".CJs8oWt0Q3quK8FkyaldwyOg-sXiU25rFaBCh5Mi2tE"

    // ── All test credentials ────────────────────────────────────────────────
    private data class Credential(
        val label: String,
        val email: String,
        val password: String,
        val expectedRole: String
    )

    private val allCredentials = listOf(
        Credential("Platform Admin",      "torrymsimango@gmail.com", "torry123M",   "platform_admin"),
        Credential("Group Admin 1",       "admin1@test.com",         "password123", "group_admin"),
        Credential("Group Admin 2",       "admin2@test.com",         "password123", "group_admin"),
        Credential("Member 1 – Active",   "member1@test.com",        "password123", "member"),
        Credential("Member 2 – Probation","member2@test.com",        "password123", "member"),
        Credential("Member 3 – Senior",   "member3@test.com",        "password123", "member"),
        Credential("Member 4 – Pending",  "member4@test.com",        "password123", "member")
    )

    // ── In-memory session stubs (no Android storage needed in JVM tests) ────
    private val memorySessionManager = object : SessionManager {
        private var stored: UserSession? = null
        override suspend fun saveSession(session: UserSession) { stored = session }
        override suspend fun loadSession(): UserSession = stored ?: error("No session stored")
        override suspend fun deleteSession() { stored = null }
    }

    private val noOpCodeVerifierCache = object : CodeVerifierCache {
        override suspend fun saveCodeVerifier(codeVerifier: String) {}
        override suspend fun loadCodeVerifier(): String? = null
        override suspend fun deleteCodeVerifier() {}
    }

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    // ── Individual credential tests ─────────────────────────────────────────

    @Test
    @Ignore("Manual — run individually to verify Platform Admin credentials")
    fun testPlatformAdminLogin() = runBlocking {
        verifyCredential(allCredentials[0])
    }

    @Test
    @Ignore("Manual — run individually to verify Group Admin 1 credentials")
    fun testGroupAdmin1Login() = runBlocking {
        verifyCredential(allCredentials[1])
    }

    @Test
    @Ignore("Manual — run individually to verify Group Admin 2 credentials")
    fun testGroupAdmin2Login() = runBlocking {
        verifyCredential(allCredentials[2])
    }

    @Test
    @Ignore("Manual — run individually to verify Member 1 credentials")
    fun testMember1Login() = runBlocking {
        verifyCredential(allCredentials[3])
    }

    @Test
    @Ignore("Manual — run individually to verify Member 2 credentials")
    fun testMember2Login() = runBlocking {
        verifyCredential(allCredentials[4])
    }

    @Test
    @Ignore("Manual — run individually to verify Member 3 credentials")
    fun testMember3Login() = runBlocking {
        verifyCredential(allCredentials[5])
    }

    @Test
    @Ignore("Manual — run individually to verify Member 4 credentials")
    fun testMember4Login() = runBlocking {
        verifyCredential(allCredentials[6])
    }

    // ── Bulk test — runs ALL accounts and prints a summary ─────────────────

    @Test
    @Ignore("Manual — run to test ALL platform credentials in one go")
    fun testAllCredentials() = runBlocking {
        val sep = "═".repeat(72)
        println(sep)
        println("  SanibonaniSave — Platform Credential Verification")
        println("  Date: ${java.time.LocalDate.now()}")
        println(sep)

        val results = mutableListOf<TestResult>()

        for (cred in allCredentials) {
            val result = runCatching { signInAndExtractInfo(cred) }
            results += result.fold(
                onSuccess = { TestResult(cred, passed = true, info = it) },
                onFailure = { TestResult(cred, passed = false, error = it.message) }
            )
        }

        // ── Print results table ───────────────────────────────────────────
        println("\n  %-28s %-30s %-18s %-10s".format("Account", "Email", "Auth Role", "Status"))
        println("  " + "─".repeat(90))
        for (r in results) {
            val status = if (r.passed) "✅ PASS" else "❌ FAIL"
            val role   = r.info?.role ?: r.error?.take(30) ?: "—"
            println("  %-28s %-30s %-18s %-10s".format(r.cred.label, r.cred.email, role, status))
        }

        println("\n  " + "─".repeat(90))
        val passed = results.count { it.passed }
        val failed = results.count { !it.passed }
        println("  Total: ${results.size}  |  ✅ Passed: $passed  |  ❌ Failed: $failed")
        println(sep)

        // ── Print failures with details ───────────────────────────────────
        if (failed > 0) {
            println("\n  ⚠️  FAILURES DETAIL")
            println("  " + "─".repeat(90))
            results.filter { !it.passed }.forEach { r ->
                println()
                println("  Account  : ${r.cred.label}")
                println("  Email    : ${r.cred.email}")
                println("  Password : ${r.cred.password}")
                println("  Error    : ${r.error}")
                println()
                println("  ► Fix: Run supabase/create_all_platform_credentials.sql in Supabase SQL Editor")
            }
            println(sep)
        }

        // Fail the test if any credential is broken
        assert(failed == 0) {
            "$failed credential(s) failed — see console output above for details."
        }
    }

    // ── Wrong-password negative test (should fail gracefully) ──────────────

    @Test
    @Ignore("Manual — confirm wrong password is rejected")
    fun testWrongPasswordRejected() = runBlocking {
        val client = buildClient()
        var errorMessage: String? = null

        try {
            client.auth.signInWith(Email) {
                email    = "torrymsimango@gmail.com"
                password = "WRONG_PASSWORD_XYZ"
            }
            println("❌ UNEXPECTED SUCCESS — wrong password was accepted!")
            assert(false) { "Login should have failed with wrong password" }
        } catch (e: Exception) {
            errorMessage = e.message
            println("✅ Correctly rejected wrong password")
            println("   Error: $errorMessage")
            assert(errorMessage != null) { "Expected a non-null error message" }
        } finally {
            runCatching { client.auth.signOut() }
            client.close()
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private data class UserInfo(val userId: String, val email: String, val role: String)
    private data class TestResult(
        val cred: Credential,
        val passed: Boolean,
        val info: UserInfo? = null,
        val error: String? = null
    )

    private suspend fun verifyCredential(cred: Credential) {
        println("\n─────────────────────────────────────────────────────────────────────")
        println("Testing: ${cred.label}")
        println("  Email   : ${cred.email}")
        println("  Password: ${cred.password}")

        val info = signInAndExtractInfo(cred)

        println("  ✅ Login successful!")
        println("  User ID : ${info.userId}")
        println("  Email   : ${info.email}")
        println("  Role    : ${info.role}")

        // Verify role matches expectation
        assert(info.role == cred.expectedRole) {
            "Expected role '${cred.expectedRole}' but got '${info.role}'"
        }
        println("  Role check : ✅ '${info.role}' matches expected '${cred.expectedRole}'")
    }

    private suspend fun signInAndExtractInfo(cred: Credential): UserInfo {
        val client = buildClient()
        try {
            client.auth.signInWith(Email) {
                email    = cred.email
                password = cred.password
            }

            val user: io.github.jan.supabase.auth.user.UserInfo? = client.auth.currentUserOrNull()
            requireNotNull(user) { "Sign-in succeeded but currentUser is null" }

            val role = user.userMetadata
                ?.get("role")
                ?.toString()
                ?.trim('"')
                ?: "unknown"

            return UserInfo(
                userId = user.id,
                email  = user.email ?: cred.email,
                role   = role
            )
        } finally {
            runCatching { client.auth.signOut() }
            client.close()
        }
    }

    private fun buildClient() = createSupabaseClient(supabaseUrl, anonKey) {
        install(Auth) {
            sessionManager      = memorySessionManager
            codeVerifierCache   = noOpCodeVerifierCache
        }
    }
}

