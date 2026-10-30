package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.datasource.LocalAuditDataSource
import com.sanibonani.save.data.datasource.RemoteAuditDataSource
import com.sanibonani.save.data.local.AuditReportEntity
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.AuditRepository
import com.sanibonani.save.domain.service.AuditResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.firstOrNull
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class AuditRepositoryImpl @Inject constructor(
    private val remoteSource: RemoteAuditDataSource,
    private val localSource: LocalAuditDataSource,
    private val json: Json,
) : BaseRepository("AuditRepository"), AuditRepository {

    /**
     * Prefer the newest server snapshot so an old local zero cannot mask newer revenue. Keep the
     * last readable cache as an offline fallback. A successful empty server response is
     * authoritative and must not resurrect an obsolete cached report.
     */
    override suspend fun getPlatformAuditReport(): Result<PlatformAuditReport?> {
        val remoteResult = safeRunCatching("AuditPlatformReportRead") {
            remoteSource.getLatestPlatformAuditReport().getOrThrow()
        }

        return remoteResult.fold(
            onSuccess = { report ->
                if (report != null) {
                    cachePlatformReport(report)
                }
                Result.success(report)
            },
            onFailure = { remoteError ->
                val cached = safeRunCatching("AuditPlatformReportCacheRead") {
                    cachedPlatformReport()
                }.getOrNull()
                if (cached != null) Result.success(cached) else Result.failure(remoteError)
            }
        )
    }

    override suspend fun triggerMemberAudit(memberId: String): Result<MemberAuditReport> =
        generateReport("member_$memberId", "member", memberId) { remoteSource.generateMemberAudit(memberId) }

    override suspend fun triggerGroupAudit(groupId: String): Result<GroupAuditReport> =
        generateReport("group_$groupId", "group", groupId) { remoteSource.generateGroupAudit(groupId) }

    override suspend fun triggerPlatformAudit(): Result<PlatformAuditReport> =
        generateReport(PLATFORM_REPORT_ID, "platform", "global") { remoteSource.generatePlatformAudit() }

    private suspend fun cachedPlatformReport(): PlatformAuditReport? =
        localSource.observeReport(PLATFORM_REPORT_ID).firstOrNull()?.let { entity ->
            // A mirror the server never wrote, or one whose shape a later master changed, must not
            // be shown as if it were current: fall through to the server instead.
            safeRunCatching("AuditReportDecode") {
                json.decodeFromString<PlatformAuditReport>(entity.contentJson)
            }.getOrNull()
        }

    private suspend fun cachePlatformReport(report: PlatformAuditReport) {
        safeRunCatching("AuditReportCache") {
            localSource.saveReport(
                AuditReportEntity(
                    id = PLATFORM_REPORT_ID,
                    type = "platform",
                    targetId = "global",
                    contentJson = json.encodeToString(PlatformAuditReport.serializer(), report),
                    generatedAt = report.generatedAt,
                )
            )
        }
    }

    /**
     * Runs a server-side report, then mirrors it into the local cache. The report is already
     * authoritative once the server has produced it, so a failed cache write is logged and
     * ignored rather than turning a successful audit into an error the admin must re-run.
     *
     * [cacheId] is the key the server uses for the same report in `public.audit_reports`, so the
     * offline mirror and the remote row stay addressable by one identifier.
     */
    private suspend fun <T> generateReport(
        cacheId: String,
        type: String,
        targetId: String,
        generate: suspend () -> Result<AuditResult<T>>
    ): Result<T> = safeRunCatching {
        val result = generate().getOrThrow()
        safeRunCatching("AuditReportCache") {
            localSource.saveReport(
                AuditReportEntity(
                    id = cacheId,
                    type = type,
                    targetId = targetId,
                    contentJson = result.rawJson,
                    generatedAt = java.time.Instant.now().toString(),
                )
            )
        }
        result.data
    }

    override suspend fun auditAndFixGroupBalance(groupId: String, fix: Boolean): Result<GroupAuditResult> =
        if (fix) {
            authoritativeCall("auditAndFixGroupBalance") {
                remoteSource.auditAndFixGroupBalance(groupId, true).getOrThrow()
            }
        } else {
            remoteSource.auditAndFixGroupBalance(groupId, false)
        }

    override suspend fun getAuditLogs(limit: Int): Result<List<AuditLog>> =
        remoteSource.getAuditLogs(limit)

    override suspend fun getAuditLogsForMember(memberId: String, limit: Int): Result<List<AuditLog>> =
        remoteSource.getAuditLogsForMember(memberId, limit)

    override suspend fun getAuditLogsForGroup(groupId: String, limit: Int): Result<List<AuditLog>> =
        remoteSource.getAuditLogsForGroup(groupId, limit)

    override suspend fun logAuditEvent(auditLog: AuditLog): Result<Unit> = authoritativeCall("logAuditEvent") {
        remoteSource.insertAuditLog(auditLog).getOrThrow()
    }

    private companion object {
        /** `public.audit_reports.id` that `generate_platform_audit_report_v1()` upserts. */
        const val PLATFORM_REPORT_ID = "platform"
    }
}
