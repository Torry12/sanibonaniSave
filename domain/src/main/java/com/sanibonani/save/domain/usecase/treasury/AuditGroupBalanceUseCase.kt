package com.sanibonani.save.domain.usecase.treasury

import com.sanibonani.save.domain.model.GroupAuditResult
import com.sanibonani.save.domain.repository.admin.AuditRepository
import javax.inject.Inject

/**
 * Compares a group's derived ledger against its stored balance.
 *
 * [repair] decides whether the server is allowed to write: false reports the discrepancy only,
 * true posts the corrective entry. The two are the same read on the server, so the difference is
 * kept in one place rather than at each call site.
 */
class AuditGroupBalanceUseCase @Inject constructor(
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(groupId: String, repair: Boolean): Result<GroupAuditResult> =
        auditRepository.auditAndFixGroupBalance(groupId, fix = repair)
}
