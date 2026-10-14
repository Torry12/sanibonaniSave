package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.datasource.LocalAuditDataSource
import com.sanibonani.save.data.datasource.RemoteAuditDataSource
import com.sanibonani.save.data.local.AuditReportEntity
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.AuditRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class AuditRepositoryImpl @Inject constructor(
    private val remoteSource: RemoteAuditDataSource,
    private val localSource: LocalAuditDataSource,
    private val json: Json,
) : BaseRepository("AuditRepository"), AuditRepository {

    override suspend fun getMemberAuditReport(memberId: String): Result<MemberAuditReport> =
        remoteSource.generateMemberAudit(memberId).map { it.data }

    override suspend fun getGroupAuditReport(groupId: String): Result<GroupAuditReport> =
        remoteSource.generateGroupAudit(groupId).map { it.data }

    override fun observeGroupAuditReport(groupId: String): Flow<Result<GroupAuditReport>> = observeAndSync(
        dbFlow = localSource.observeReport("group_$groupId").map { it?.let { listOf(it) } ?: emptyList() },
        mapper = { entity -> json.decodeFromString<GroupAuditReport>(entity.contentJson) },
        toEntity = { report ->
            AuditReportEntity(
                id = "group_$groupId",
                type = "group",
                targetId = groupId,
                contentJson = json.encodeToString(GroupAuditReport.serializer(), report),
                generatedAt = java.time.Instant.now().toString(),
            )
        },
        networkFetch = {
            val res = remoteSource.generateGroupAudit(groupId).getOrThrow()
            listOf(res.data)
        },
        cacheSync = { list ->
            list.firstOrNull()?.let { localSource.saveReport(it) }
        }
    ).map { res -> res.map { it.first() } }

    override fun observeMemberAuditReport(memberId: String): Flow<Result<MemberAuditReport>> = observeAndSync(
        dbFlow = localSource.observeReport("member_$memberId").map { it?.let { listOf(it) } ?: emptyList() },
        mapper = { entity -> json.decodeFromString<MemberAuditReport>(entity.contentJson) },
        toEntity = { report ->
            AuditReportEntity(
                id = "member_$memberId",
                type = "member",
                targetId = memberId,
                contentJson = json.encodeToString(MemberAuditReport.serializer(), report),
                generatedAt = java.time.Instant.now().toString(),
            )
        },
        networkFetch = {
            val res = remoteSource.generateMemberAudit(memberId).getOrThrow()
            listOf(res.data)
        },
        cacheSync = { list ->
            list.firstOrNull()?.let { localSource.saveReport(it) }
        }
    ).map { res -> res.map { it.first() } }

    override suspend fun getPlatformAuditReport(): Result<PlatformAuditReport> =
        remoteSource.generatePlatformAudit().map { it.data }

    override suspend fun triggerMemberAudit(memberId: String): Result<MemberAuditReport> = safeRunCatching {
        val result = remoteSource.generateMemberAudit(memberId).getOrThrow()
        localSource.saveReport(
            AuditReportEntity(
                id = "member_$memberId",
                type = "member",
                targetId = memberId,
                contentJson = result.rawJson,
                generatedAt = java.time.Instant.now().toString(),
            )
        )

        // Propagate verified totals back to the core member record.
        // (No longer doing this automatically from here to avoid circular logic)

        result.data
    }

    override suspend fun triggerGroupAudit(groupId: String): Result<GroupAuditReport> = safeRunCatching {
        val result = remoteSource.generateGroupAudit(groupId).getOrThrow()
        localSource.saveReport(
            AuditReportEntity(
                id = "group_$groupId",
                type = "group",
                targetId = groupId,
                contentJson = result.rawJson,
                generatedAt = java.time.Instant.now().toString(),
            )
        )

        // Propagate verified balance back to the core group record.
        // (No longer doing this automatically from here to avoid circular logic)

        result.data
    }

    override suspend fun triggerPlatformAudit(): Result<PlatformAuditReport> = safeRunCatching {
        val result = remoteSource.generatePlatformAudit().getOrThrow()
        localSource.saveReport(
            AuditReportEntity(
                id = "platform_global",
                type = "platform",
                targetId = "global",
                contentJson = result.rawJson,
                generatedAt = java.time.Instant.now().toString(),
            )
        )
        result.data
    }

    override suspend fun auditAndFixGroupBalance(groupId: String, fix: Boolean): Result<GroupAuditResult> =
        remoteSource.auditAndFixGroupBalance(groupId, fix)

    override suspend fun getAuditLogs(limit: Int): Result<List<AuditLog>> =
        remoteSource.getAuditLogs(limit)

    override suspend fun getAuditLogsForMember(memberId: String, limit: Int): Result<List<AuditLog>> =
        remoteSource.getAuditLogsForMember(memberId, limit)

    override suspend fun getAuditLogsForGroup(groupId: String, limit: Int): Result<List<AuditLog>> =
        remoteSource.getAuditLogsForGroup(groupId, limit)
}

