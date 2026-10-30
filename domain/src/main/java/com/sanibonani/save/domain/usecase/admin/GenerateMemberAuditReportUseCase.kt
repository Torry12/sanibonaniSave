package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.MemberAuditReport
import com.sanibonani.save.domain.repository.admin.AuditRepository
import javax.inject.Inject

/**
 * Generates a fresh audit report for one member.
 *
 * This writes server-side, which is why the member screen asks for it explicitly instead of
 * reading a report that may predate the transactions in question.
 */
class GenerateMemberAuditReportUseCase @Inject constructor(
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(memberId: String): Result<MemberAuditReport> =
        auditRepository.triggerMemberAudit(memberId)
}
