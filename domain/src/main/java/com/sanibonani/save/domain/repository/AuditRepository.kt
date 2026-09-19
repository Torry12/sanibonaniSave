package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.GroupAuditReport
import com.sanibonani.save.domain.model.MemberAuditReport
import com.sanibonani.save.domain.model.PlatformAuditReport

/**
 * Repository for generating audit reports for members, groups, and the platform.
 */
interface AuditRepository {
    suspend fun getMemberAuditReport(memberId: String): Result<MemberAuditReport>
    suspend fun getGroupAuditReport(groupId: String): Result<GroupAuditReport>
    suspend fun getPlatformAuditReport(): Result<PlatformAuditReport>
}
