package com.sanibonani.save.domain.repository.admin

import com.sanibonani.save.domain.model.*

/**
 * Repository for generating audit reports for members, groups, and the platform.
 */
interface AuditRepository {
    /**
     * Reads the newest platform snapshot without generating one. Null means the platform has never
     * been audited — an empty state the caller shows as a prompt, not a failure. Use
     * [triggerPlatformAudit] to regenerate, which writes two rows server-side.
     */
    suspend fun getPlatformAuditReport(): Result<PlatformAuditReport?>
    suspend fun triggerMemberAudit(memberId: String): Result<MemberAuditReport>
    suspend fun triggerGroupAudit(groupId: String): Result<GroupAuditReport>
    suspend fun triggerPlatformAudit(): Result<PlatformAuditReport>
    suspend fun auditAndFixGroupBalance(groupId: String, fix: Boolean): Result<com.sanibonani.save.domain.model.GroupAuditResult>
    suspend fun getAuditLogs(limit: Int = 50): Result<List<AuditLog>>
    suspend fun getAuditLogsForMember(memberId: String, limit: Int = 20): Result<List<AuditLog>>
    suspend fun getAuditLogsForGroup(groupId: String, limit: Int = 50): Result<List<AuditLog>>
    suspend fun logAuditEvent(auditLog: AuditLog): Result<Unit>
}
