package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.GroupAuditReport
import com.sanibonani.save.domain.model.MemberAuditReport
import com.sanibonani.save.domain.model.PlatformAuditReport
import com.sanibonani.save.domain.service.AuditResult
import kotlinx.coroutines.flow.Flow

interface RemoteAuditDataSource {
    suspend fun generateMemberAudit(memberId: String): Result<AuditResult<MemberAuditReport>>
    suspend fun generateGroupAudit(groupId: String): Result<AuditResult<GroupAuditReport>>
    suspend fun generatePlatformAudit(): Result<AuditResult<PlatformAuditReport>>
    suspend fun getLatestPlatformAuditReport(): Result<PlatformAuditReport?>
    suspend fun auditAndFixGroupBalance(groupId: String, fix: Boolean): Result<com.sanibonani.save.domain.model.GroupAuditResult>

    suspend fun getAuditLogs(limit: Int = 50): Result<List<com.sanibonani.save.domain.model.AuditLog>>
    suspend fun getAuditLogsForMember(memberId: String, limit: Int = 20): Result<List<com.sanibonani.save.domain.model.AuditLog>>
    suspend fun getAuditLogsForGroup(groupId: String, limit: Int = 50): Result<List<com.sanibonani.save.domain.model.AuditLog>>
    suspend fun insertAuditLog(log: com.sanibonani.save.domain.model.AuditLog): Result<Unit>
}




interface LocalAuditDataSource {
    fun observeReport(id: String): Flow<com.sanibonani.save.data.local.AuditReportEntity?>
    suspend fun saveReport(entity: com.sanibonani.save.data.local.AuditReportEntity)
    suspend fun clearAll()
}
