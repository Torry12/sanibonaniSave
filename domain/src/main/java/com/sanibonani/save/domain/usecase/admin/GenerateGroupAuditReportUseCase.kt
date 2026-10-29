package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.GroupAuditReport
import com.sanibonani.save.domain.repository.admin.AuditRepository
import javax.inject.Inject

/**
 * Generates a fresh audit report for one group.
 *
 * This writes server-side, so the admin asks for it explicitly rather than the screen refreshing a
 * report that may predate the transactions under review.
 */
class GenerateGroupAuditReportUseCase @Inject constructor(
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(groupId: String): Result<GroupAuditReport> =
        auditRepository.triggerGroupAudit(groupId)
}
