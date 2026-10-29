package com.sanibonani.save.data.datasource

import com.sanibonani.save.data.local.AuditReportEntity
import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.model.GroupAuditReport
import com.sanibonani.save.domain.model.GroupAuditResult
import com.sanibonani.save.domain.model.MemberAuditReport
import com.sanibonani.save.domain.model.PlatformAuditReport
import com.sanibonani.save.domain.service.AuditResult
import kotlinx.coroutines.flow.Flow

interface RemoteAuditDataSource {
    suspend fun generateMemberAudit(memberId: String): Result<AuditResult<MemberAuditReport>>
    suspend fun generateGroupAudit(groupId: String): Result<AuditResult<GroupAuditReport>>
    suspend fun generatePlatformAudit(): Result<AuditResult<PlatformAuditReport>>
    suspend fun getLatestPlatformAuditReport(): Result<PlatformAuditReport?>
    suspend fun auditAndFixGroupBalance(groupId: String, fix: Boolean): Result<GroupAuditResult>

    suspend fun getAuditLogs(limit: Int = 50): Result<List<AuditLog>>
    suspend fun getAuditLogsForMember(memberId: String, limit: Int = 20): Result<List<AuditLog>>
    suspend fun getAuditLogsForGroup(groupId: String, limit: Int = 50): Result<List<AuditLog>>
    suspend fun insertAuditLog(log: AuditLog): Result<Unit>
}

interface LocalAuditDataSource {
    fun observeReport(id: String): Flow<AuditReportEntity?>
    suspend fun saveReport(entity: AuditReportEntity)
    suspend fun clearAll()
}
