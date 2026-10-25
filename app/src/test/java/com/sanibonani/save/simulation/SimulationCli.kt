package com.sanibonani.save.simulation

import org.junit.runner.JUnitCore

fun main(args: Array<String>) {
    val configuration = listOf(
        "SUPABASE_URL",
        "SUPABASE_ANON_KEY",
        "PLATFORM_ADMIN_EMAIL",
        "PLATFORM_ADMIN_PASS"
    ).associateWith { name ->
        System.getenv(name)?.takeIf(String::isNotBlank)
            ?: error("$name is required to run the live simulation")
    }

    configuration.forEach { (name, value) -> System.setProperty("sanibonani.simulation.$name", value) }
    args.firstOrNull()?.let { System.setProperty("sanibonani.simulation.mode", it) }
    System.setProperty("sanibonani.simulation.manual", "true")

    val result = JUnitCore.runClasses(ManualLiveSimulationTest::class.java)
    result.failures.forEach { failure -> System.err.println(failure.trace) }
    check(result.wasSuccessful()) { "Live simulation failed with ${result.failureCount} failure(s)" }
}
