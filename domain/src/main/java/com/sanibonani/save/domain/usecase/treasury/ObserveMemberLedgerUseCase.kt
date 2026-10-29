package com.sanibonani.save.domain.usecase.treasury

import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * A member's own side of the ledger, re-emitted on every cache or server change.
 */
class ObserveMemberLedgerUseCase @Inject constructor(
    private val ledgerRepository: LedgerRepository
) {
    operator fun invoke(memberId: String): Flow<Result<List<LedgerEntry>>> =
        ledgerRepository.observeMemberLedger(memberId)
}
