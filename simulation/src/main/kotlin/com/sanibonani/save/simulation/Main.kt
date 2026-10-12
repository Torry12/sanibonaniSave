package com.sanibonani.save.simulation

import kotlinx.coroutines.runBlocking
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic

fun main(args: Array<String>) {
    // ── JVM MOCKING SETUP (to prevent Stub! errors from android.jar) ──────────
    try {
        mockkStatic("android.util.Log")
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0

        mockkStatic("android.os.Looper")
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        // Mock Build.VERSION if needed
        mockkStatic("android.os.Build\$VERSION")
        every { android.os.Build.VERSION.SDK_INT } returns 33

        mockkStatic("android.text.TextUtils")
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val s = it.invocation.args[0] as CharSequence?
            s == null || s.length == 0
        }

        mockkStatic("android.net.Uri")
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        every { android.net.Uri.parse(any()) } returns mockUri

        mockkStatic("android.util.Base64")
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(it.invocation.args[0] as ByteArray)
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(it.invocation.args[0] as String)
        }

        mockkStatic("android.util.Log")
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
    } catch (e: Throwable) {
        println("Warning: JVM Mocking failed (this is expected if not running on desktop JVM): ${e.message}")
    }

    runBlocking {
        println("--- SanibonaniSave Financial Simulation Tool ---")

        val mode = args.firstOrNull() ?: "deep"

        // Config - required from the environment. This tool authenticates as a real
        // platform administrator, so credentials must never be baked into source.
        val url = System.getenv("SUPABASE_URL")
            ?: error("SUPABASE_URL is required (e.g. https://<project>.supabase.co)")
        val key = System.getenv("SUPABASE_ANON_KEY")
            ?: error("SUPABASE_ANON_KEY is required")
        val adminEmail = System.getenv("PLATFORM_ADMIN_EMAIL")
            ?: error("PLATFORM_ADMIN_EMAIL is required")
        val adminPass = System.getenv("PLATFORM_ADMIN_PASS")
            ?: error("PLATFORM_ADMIN_PASS is required")

        val runner = LiveSimulationRunner(url, key)

        try {
            println("Target URL: $url")
            println("Target Admin: $adminEmail")
            println("Simulation Mode: ${mode.uppercase()}")

            val result = when(mode.lowercase()) {
                "deep" -> runner.runDeepHistoricalActivitySimulation(adminEmail, adminPass)
                "flow" -> runner.runLiveFinancialFlowSimulation(adminEmail, adminPass)
                else -> {
                    println("Unknown mode '$mode'. Available: deep, flow")
                    return@runBlocking
                }
            }

            result.onSuccess { groupId ->
                println("\nSUCCESS! Simulation completed for group: $groupId")
            }.onFailure { error ->
                println("\nERROR: Simulation failed!")
                error.printStackTrace()
            }
        } finally {
            runner.close()
        }
    }
}
