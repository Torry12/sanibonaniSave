package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.PlatformAuditReport
import com.sanibonani.save.domain.repository.admin.AuditRepository
import javax.inject.Inject

/**
 * The newest platform audit snapshot, or null when the platform has never been audited.
 *
 * Null is an empty state, not a failure: it is what lets the tab show its "Run Platform Audit"
 * prompt instead of an error. Use [GeneratePlatformAuditReportUseCase] to produce one, which
 * writes server-side.
 */
class ReadPlatformAuditReportUseCase @Inject constructor(
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(): Result<PlatformAuditReport?> =
        auditRepository.getPlatformAuditReport()
}
