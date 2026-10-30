package com.sanibonani.save.data.repository.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `functions.invoke` returns the raw HTTP response and never throws on a non-2xx status, so a
 * failed backup or recompute would be reported as "Cloud backup triggered". These cases pin the
 * status rule and the message the console ends up showing.
 */
class PlatformFunctionFailureTest {

    @Test
    fun `a 2xx response is not a failure`() {
        assertNull(functionFailureMessage("trigger-backup", 200, "{\"ok\":true}"))
        assertNull(functionFailureMessage("trigger-backup", 204, ""))
    }

    @Test
    fun `a function error body survives to the message`() {
        val message = functionFailureMessage(
            "trigger-backup",
            500,
            "{\"error\":\"Upload to backups/backup_x.json.gz failed: new row violates row-level security policy\"}"
        )

        assertEquals(
            "The 'trigger-backup' function failed (HTTP 500): " +
                "Upload to backups/backup_x.json.gz failed: new row violates row-level security policy",
            message
        )
    }

    @Test
    fun `the gateway message key is read when there is no error key`() {
        assertEquals(
            "The 'compute-platform-metrics' function failed (HTTP 401): Invalid JWT",
            functionFailureMessage("compute-platform-metrics", 401, "{\"code\":401,\"message\":\"Invalid JWT\"}")
        )
    }

    @Test
    fun `a missing function is reported with the gateway's message`() {
        assertEquals(
            "The 'trigger-backup' function failed (HTTP 404): Requested function was not found",
            functionFailureMessage("trigger-backup", 404, "{\"code\":404,\"message\":\"Requested function was not found\"}")
        )
    }

    @Test
    fun `a non-json body is shown as it came back`() {
        assertEquals(
            "The 'trigger-backup' function failed (HTTP 502): Bad gateway",
            functionFailureMessage("trigger-backup", 502, "  Bad gateway  ")
        )
    }

    @Test
    fun `an empty body still names the status`() {
        assertEquals(
            "The 'trigger-backup' function failed (HTTP 500): no response body",
            functionFailureMessage("trigger-backup", 500, "")
        )
    }

    @Test
    fun `an unexpectedly shaped error value falls back to the raw body`() {
        assertEquals(
            "The 'trigger-backup' function failed (HTTP 500): {\"error\":{\"nested\":true}}",
            functionFailureMessage("trigger-backup", 500, "{\"error\":{\"nested\":true}}")
        )
    }

    @Test
    fun `a long body is truncated so it cannot flood the console`() {
        val message = functionFailureMessage("trigger-backup", 500, "x".repeat(5000)).orEmpty()

        assertTrue(message.endsWith("x".repeat(300)))
        assertEquals("The 'trigger-backup' function failed (HTTP 500): ".length + 300, message.length)
    }
}
