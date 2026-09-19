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
}

interface LocalAuditDataSource {
    fun observeReport(id: String): Flow<com.sanibonani.save.data.local.AuditReportEntity?>
    suspend fun saveReport(entity: com.sanibonani.save.data.local.AuditReportEntity)
    suspend fun clearAll()
}
