package com.sanibonani.save.viewmodel

import com.sanibonani.save.features.landing.presentation.landingSyncErrorMessages
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The analytics card and the settings card render their own failure, so a leg that answered must
 * not be described by the other leg's cause. Both used to collapse onto one string, which meant a
 * refused settings read reported the analytics server's complaint instead of its own.
 */
class LandingSyncErrorAttributionTest {

    @Test
    fun `a refused analytics read is described only on the analytics leg`() {
        val (analytics, settings) = landingSyncErrorMessages(
            IOException("IO Error: HTTP request to https://api.example/rest/v1/groups failed"),
            null
        )

        assertEquals("Unable to sync with remote server. Using cached data.", analytics)
        assertNull(settings)
    }

    @Test
    fun `a refused settings read keeps its own cause`() {
        val (analytics, settings) = landingSyncErrorMessages(null, SecurityException("HTTP 403 Forbidden"))

        assertNull(analytics)
        assertEquals("You don’t have permission to perform this action.", settings)
    }

    @Test
    fun `both legs refusing report two different causes`() {
        val (analytics, settings) = landingSyncErrorMessages(
            IllegalStateException("HTTP 401 Unauthorized"),
            IOException("Socket closed while reading the response")
        )

        assertEquals("Your session has expired. Please log in again.", analytics)
        assertEquals("IO error: Socket closed while reading the response", settings)
        assertNotEquals(analytics, settings)
    }

    @Test
    fun `answered legs get no message`() {
        val (analytics, settings) = landingSyncErrorMessages(null, null)

        assertNull(analytics)
        assertNull(settings)
    }

    @Test
    fun `a cause with no message still yields text rather than nothing`() {
        val (analytics, settings) = landingSyncErrorMessages(IllegalStateException(""), null)

        assertEquals("Invalid operation", analytics)
        assertNull(settings)
    }
}
