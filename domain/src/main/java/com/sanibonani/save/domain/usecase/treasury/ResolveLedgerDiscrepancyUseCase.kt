package com.sanibonani.save.domain.usecase.treasury

import com.sanibonani.save.domain.repository.treasury.ReconciliationRepository
import javax.inject.Inject

/**
 * Closes a detected discrepancy with a corrective adjustment entry.
 *
 * The notes are part of the write, not commentary: an adjustment without a stated reason is the
 * kind of ledger row an audit cannot explain afterwards.
 */
class ResolveLedgerDiscrepancyUseCase @Inject constructor(
    private val reconciliationRepository: ReconciliationRepository
) {
    suspend operator fun invoke(reconciliationId: String, notes: String): Result<Unit> =
        reconciliationRepository.resolveDiscrepancy(reconciliationId, notes)
}
