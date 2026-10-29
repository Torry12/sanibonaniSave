package com.sanibonani.save.domain.usecase.treasury

import com.sanibonani.save.domain.model.ConsolidationReport
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import javax.inject.Inject

/**
 * Rebuilds a group's missing ledger rows from its sub-ledgers. This rewrites the audit trail, so
 * it is a deliberate admin action rather than something a cache refresh may trigger.
 */
class ConsolidateGroupLedgerUseCase @Inject constructor(
    private val ledgerRepository: LedgerRepository
) {
    suspend operator fun invoke(groupId: String): Result<ConsolidationReport> =
        ledgerRepository.consolidateGroupLedger(groupId)
}
