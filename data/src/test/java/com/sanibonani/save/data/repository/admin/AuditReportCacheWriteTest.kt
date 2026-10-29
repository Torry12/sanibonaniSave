package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.datasource.LocalAuditDataSource
import com.sanibonani.save.data.datasource.RemoteAuditDataSource
import com.sanibonani.save.data.local.AuditReportEntity
import com.sanibonani.save.domain.model.GroupAuditReport
import com.sanibonani.save.domain.model.MemberAuditReport
import com.sanibonani.save.domain.model.PlatformAuditReport
import com.sanibonani.save.domain.service.AuditResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An audit report is produced by the server and only mirrored into Room for offline reading. The
 * admin's decision depends on the report itself, so a cache write that fails must never be
 * reported as an audit that failed.
 */
class AuditReportCacheWriteTest {

    private val remoteSource: RemoteAuditDataSource = mockk()
    private val localSource: LocalAuditDataSource = mockk(relaxed = true)

    private fun repository() = AuditRepositoryImpl(
        remoteSource,
        localSource,
        Json { ignoreUnknownKeys = true; isLenient = true },
    )

    private val groupReport = GroupAuditReport(groupId = "g1")
    private val memberReport = MemberAuditReport(memberId = "m1")
    private val platformReport = PlatformAuditReport()

    @Test
    fun `a generated group report is cached under its server id`() = runBlocking {
        coEvery { remoteSource.generateGroupAudit("g1") } returns
            Result.success(AuditResult(groupReport, """{"group_id":"g1"}"""))
        val cached = slot<AuditReportEntity>()

        val result = repository().triggerGroupAudit("g1")

        assertTrue(result.isSuccess)
        coVerify { localSource.saveReport(capture(cached)) }
        assertEquals("group_g1", cached.captured.id)
        assertEquals("group", cached.captured.type)
        assertEquals("g1", cached.captured.targetId)
        assertEquals("""{"group_id":"g1"}""", cached.captured.contentJson)
    }

    @Test
    fun `a member report still reaches the admin when the cache write fails`() = runBlocking {
        coEvery { remoteSource.generateMemberAudit("m1") } returns
            Result.success(AuditResult(memberReport, """{"member_id":"m1"}"""))
        coEvery { localSource.saveReport(any()) } throws IllegalStateException("disk full")

        val result = repository().triggerMemberAudit("m1")

        assertEquals(memberReport, result.getOrNull())
    }

    @Test
    fun `a report the server refused is not cached as if it had run`() = runBlocking {
        coEvery { remoteSource.generatePlatformAudit() } returns
            Result.failure(IllegalStateException("HTTP 503 Service Unavailable"))

        val result = repository().triggerPlatformAudit()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { localSource.saveReport(any()) }
    }

    @Test
    fun `the platform report is cached against the global target`() = runBlocking {
        coEvery { remoteSource.generatePlatformAudit() } returns
            Result.success(AuditResult(platformReport, """{"ok":true}"""))
        val cached = slot<AuditReportEntity>()

        repository().triggerPlatformAudit()

        coVerify { localSource.saveReport(capture(cached)) }
        // 'platform' is the id the server itself upserts into public.audit_reports.
        assertEquals("platform", cached.captured.id)
        assertEquals("global", cached.captured.targetId)
    }
}
