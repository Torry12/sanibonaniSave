package com.sanibonani.save.domain.usecase.treasury

import com.sanibonani.save.domain.model.ReconciliationRecord
import com.sanibonani.save.domain.repository.treasury.ReconciliationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Reconciliation history for one group, streaming from the local cache as the server writes rows.
 */
class ObserveGroupReconciliationsUseCase @Inject constructor(
    private val reconciliationRepository: ReconciliationRepository
) {
    operator fun invoke(groupId: String): Flow<Result<List<ReconciliationRecord>>> =
        reconciliationRepository.observeReconciliations(groupId)
}
