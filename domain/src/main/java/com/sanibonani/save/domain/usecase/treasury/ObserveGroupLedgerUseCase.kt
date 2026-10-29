package com.sanibonani.save.domain.usecase.treasury

import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The whole group ledger, re-emitted whenever a row is written.
 */
class ObserveGroupLedgerUseCase @Inject constructor(
    private val ledgerRepository: LedgerRepository
) {
    operator fun invoke(groupId: String): Flow<Result<List<LedgerEntry>>> =
        ledgerRepository.observeGroupLedger(groupId)
}
