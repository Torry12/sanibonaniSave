package com.sanibonani.save.data.logging

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A device that drops app-authored logcat lines turns every diagnostic into a guess: on the Huawei
 * under test, `logcat -b all` carried zero lines from the app's own pid, so a refresh that answered
 * and a refresh that was never attempted could not be told apart from outside. This sink is the route
 * that does not depend on the firmware letting anyone read the log - the file lands in the app's
 * private dir, which `run-as` reads on any debug build.
 */
class DiagnosticFileSinkTest {

    private lateinit var dir: File
    private lateinit var context: Context
    private lateinit var logcat: AndroidLogSink
    private lateinit var sink: DiagnosticFileSink

    @Before
    fun setup() {
        dir = Files.createTempDirectory("diagnostics").toFile()
        context = mockk(relaxed = true)
        every { context.filesDir } returns dir

        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.d(any(), any()) } returns 0

        logcat = mockk(relaxed = true)
        sink = DiagnosticFileSink(context, logcat)
    }

    @After
    fun tearDown() {
        unmockkAll()
        dir.deleteRecursively()
    }

    private fun recorded(): List<String> = File(dir, DiagnosticFileSink.FILE_NAME).readLines()

    @Test
    fun `an info verdict reaches the diagnostics file`() {
        sink.i("DiscoveryRead", "discovery read attempted")

        assertTrue(recorded().any { it.contains("DiscoveryRead") && it.contains("discovery read attempted") })
    }

    @Test
    fun `a refusal is recorded the same way as an answer`() {
        sink.i("DiscoveryRead", "discovery read attempted")
        sink.w("DiscoveryRead", "discovery read refused: no network", null)

        val lines = recorded()
        assertEquals(2, lines.size)
        assertTrue(lines.last().contains("discovery read refused"))
    }

    @Test
    fun `every recorded line still reaches the platform log`() {
        sink.i("DiscoveryRead", "discovery read answered with 19 groups")

        verify(exactly = 1) { logcat.i("DiscoveryRead", "discovery read answered with 19 groups") }
    }

    @Test
    fun `the file keeps the newest lines once it passes the cap`() {
        for (n in 1..(DiagnosticFileSink.MAX_LINES + 20)) {
            sink.i("DiscoveryRead", "line $n")
        }

        val lines = recorded()
        assertEquals(DiagnosticFileSink.MAX_LINES, lines.size)
        assertTrue(lines.last().contains("line ${DiagnosticFileSink.MAX_LINES + 20}"))
        assertFalse(lines.first().contains("line 1"))
    }

    @Test
    fun `a line carries the moment it was written`() {
        val before = System.currentTimeMillis()
        sink.i("DiscoveryRead", "discovery read attempted")

        val stamp = recorded().single().substringBefore(' ').toLong()
        assertTrue(stamp in before..System.currentTimeMillis())
    }

    @Test
    fun `a device that cannot write the file does not fail the read being logged`() {
        every { context.filesDir } throws IllegalStateException("no storage")

        sink.i("DiscoveryRead", "discovery read attempted")

        verify(exactly = 1) { logcat.i("DiscoveryRead", "discovery read attempted") }
    }
}
