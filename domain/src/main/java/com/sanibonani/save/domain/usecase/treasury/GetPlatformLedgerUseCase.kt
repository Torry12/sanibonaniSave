package com.sanibonani.save.domain.usecase.treasury

import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import javax.inject.Inject

/**
 * The platform-wide ledger rows behind the admin ledger tab, read rather than observed so the
 * screen can choose when to pay for the round trip.
 */
class GetPlatformLedgerUseCase @Inject constructor(
    private val ledgerRepository: LedgerRepository
) {
    suspend operator fun invoke(): Result<List<LedgerEntry>> = ledgerRepository.getPlatformLedger()
}
