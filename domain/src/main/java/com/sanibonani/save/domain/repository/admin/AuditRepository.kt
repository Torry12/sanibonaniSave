package com.sanibonani.save.domain.repository.admin

import com.sanibonani.save.domain.model.GroupAuditReport
import com.sanibonani.save.domain.model.MemberAuditReport
import com.sanibonani.save.domain.model.PlatformAuditReport

/**
 * Repository for generating audit reports for members, groups, and the platform.
 */
interface AuditRepository {
    suspend fun getPlatformAuditReport(): Result<PlatformAuditReport>
    suspend fun triggerMemberAudit(memberId: String): Result<MemberAuditReport>
    suspend fun triggerGroupAudit(groupId: String): Result<GroupAuditReport>
    suspend fun triggerPlatformAudit(): Result<PlatformAuditReport>
    suspend fun auditAndFixGroupBalance(groupId: String, fix: Boolean): Result<com.sanibonani.save.domain.model.GroupAuditResult>
    suspend fun getAuditLogs(limit: Int = 50): Result<List<com.sanibonani.save.domain.model.AuditLog>>
    suspend fun getAuditLogsForMember(memberId: String, limit: Int = 20): Result<List<com.sanibonani.save.domain.model.AuditLog>>
    suspend fun getAuditLogsForGroup(groupId: String, limit: Int = 50): Result<List<com.sanibonani.save.domain.model.AuditLog>>
}

