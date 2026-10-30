package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.datasource.LocalAuditDataSource
import com.sanibonani.save.data.datasource.RemoteAuditDataSource
import com.sanibonani.save.data.local.AuditReportEntity
import com.sanibonani.save.domain.model.PlatformAuditReport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The platform audit screen reads the latest snapshot without regenerating it (generation writes
 * server-side rows). Server snapshots refresh the local mirror; a readable mirror is only used
 * when the server read fails.
 */
class AuditPlatformCacheReadTest {

    private val remoteSource: RemoteAuditDataSource = mockk()
    private val localSource: LocalAuditDataSource = mockk(relaxed = true)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun repository() = AuditRepositoryImpl(remoteSource, localSource, json)

    private val report = PlatformAuditReport(
        generatedAt = "2026-10-26 11:04:52+02",
        liquidity = com.sanibonani.save.domain.model.Money("48200.00"),
        revenue = com.sanibonani.save.domain.model.Money("1910.50")
    )

    private val newerReport = report.copy(
        generatedAt = "2026-10-27 11:04:52+02",
        revenue = com.sanibonani.save.domain.model.Money("2410.50")
    )

    private fun cachedEntity(cachedReport: PlatformAuditReport = report): AuditReportEntity = AuditReportEntity(
        id = "platform",
        type = "platform",
        targetId = "global",
        contentJson = json.encodeToString(PlatformAuditReport.serializer(), cachedReport),
        generatedAt = "2026-10-26T09:04:52Z"
    )

    @Test
    fun `latest server report replaces stale cached zero report`() = runBlocking {
        val staleZeroReport = report.copy(revenue = com.sanibonani.save.domain.model.Money.ZERO)
        every { localSource.observeReport("platform") } returns flowOf(cachedEntity(staleZeroReport))
        coEvery { remoteSource.getLatestPlatformAuditReport() } returns Result.success(newerReport)

        val result = repository().getPlatformAuditReport()

        assertEquals(newerReport, result.getOrNull())
        coVerify(exactly = 1) { remoteSource.getLatestPlatformAuditReport() }
        coVerify(exactly = 1) {
            localSource.saveReport(match {
                it.contentJson == json.encodeToString(PlatformAuditReport.serializer(), newerReport) &&
                    it.generatedAt == newerReport.generatedAt
            })
        }
        coVerify(exactly = 0) { remoteSource.generatePlatformAudit() }
    }

    @Test
    fun `server failure falls back to readable cached report`() = runBlocking {
        every { localSource.observeReport("platform") } returns flowOf(cachedEntity())
        coEvery { remoteSource.getLatestPlatformAuditReport() } returns Result.failure(
            IllegalStateException("offline")
        )

        val result = repository().getPlatformAuditReport()

        assertEquals(report, result.getOrNull())
        coVerify(exactly = 1) { localSource.observeReport("platform") }
    }

    @Test
    fun `successful empty server response does not return stale cached report`() = runBlocking {
        coEvery { remoteSource.getLatestPlatformAuditReport() } returns Result.success(null)

        val result = repository().getPlatformAuditReport()

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
        coVerify(exactly = 0) { localSource.observeReport("platform") }
    }

    @Test
    fun `failed cache refresh does not discard the latest server report`() = runBlocking {
        coEvery { remoteSource.getLatestPlatformAuditReport() } returns Result.success(newerReport)
        coEvery { localSource.saveReport(any()) } throws IllegalStateException("cache unavailable")

        val result = repository().getPlatformAuditReport()

        assertEquals(newerReport, result.getOrNull())
    }

    @Test
    fun `server failure is returned when cached report cannot be decoded`() = runBlocking {
        every { localSource.observeReport("platform") } returns
            flowOf(cachedEntity().copy(contentJson = "{not json"))
        val serverError = IllegalStateException("offline")
        coEvery { remoteSource.getLatestPlatformAuditReport() } returns Result.failure(serverError)

        val result = repository().getPlatformAuditReport()

        assertSame(serverError, result.exceptionOrNull())
    }

    @Test
    fun `with no cache and no server report caller is told there is no report yet`() = runBlocking {
        every { localSource.observeReport("platform") } returns flowOf(null)
        coEvery { remoteSource.getLatestPlatformAuditReport() } returns Result.success(null)

        // A platform that has never been audited is a legitimate empty state, not a failure: the
        // ViewModel maps it to the tab's "Run Platform Audit" prompt instead of an error screen.
        val result = repository().getPlatformAuditReport()

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }
}
