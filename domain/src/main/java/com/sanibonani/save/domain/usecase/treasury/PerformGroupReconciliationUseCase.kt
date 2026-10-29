package com.sanibonani.save.domain.usecase.treasury

import com.sanibonani.save.domain.model.ReconciliationRecord
import com.sanibonani.save.domain.repository.treasury.ReconciliationRepository
import javax.inject.Inject

/**
 * Runs a server-side reconciliation pass for one group and returns the record it wrote.
 *
 * The returned status is what the admin is shown, so a pass that found nothing still has to come
 * back as a record rather than a silent success.
 */
class PerformGroupReconciliationUseCase @Inject constructor(
    private val reconciliationRepository: ReconciliationRepository
) {
    suspend operator fun invoke(groupId: String): Result<ReconciliationRecord> =
        reconciliationRepository.performReconciliation(groupId)
}
