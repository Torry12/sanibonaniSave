package com.sanibonani.save.simulation

import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    println("--- SanibonaniSave Financial Simulation Tool ---")

    // Config - Defaults to local stack
    val url = System.getenv("SUPABASE_URL") ?: "https://aszmodkqpafwtnfufmow.supabase.co"
    val key = System.getenv("SUPABASE_ANON_KEY") ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFzem1vZGtxcGFmd3RuZnVmbW93Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc2NDA2MzksImV4cCI6MjA5MzIxNjYzOX0.xmjdSHDGO8sm1cq_574yUxADfCWljtBKf94U-ReTxy8"
    val adminEmail = System.getenv("PLATFORM_ADMIN_EMAIL") ?: "torrymsimango@gmail.com"
    val adminPass = System.getenv("PLATFORM_ADMIN_PASS") ?: "torry123M"

    val runner = LiveSimulationRunner(url, key)

    try {
        println("Target URL: $url")
        println("Target Admin: $adminEmail")

        val result = runner.runDeepHistoricalActivitySimulation(adminEmail, adminPass)

        result.onSuccess { groupId ->
            println("\nSUCCESS! Simulation completed for group: $groupId")
            println("You can now view this group in the app dashboard.")
        }.onFailure { error ->
            println("\nERROR: Simulation failed!")
            error.printStackTrace()
        }
    } finally {
        runner.close()
    }
}
