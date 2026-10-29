package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.PlatformAuditReport
import com.sanibonani.save.domain.repository.admin.AuditRepository
import javax.inject.Inject

/**
 * Regenerates the platform snapshot. `generate_platform_audit_report_v1()` writes a history row and
 * an `audit_reports` row on every call, so this stays behind an explicit button.
 */
class GeneratePlatformAuditReportUseCase @Inject constructor(
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(): Result<PlatformAuditReport> =
        auditRepository.triggerPlatformAudit()
}
