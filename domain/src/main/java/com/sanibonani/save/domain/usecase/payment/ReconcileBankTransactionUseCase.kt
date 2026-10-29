package com.sanibonani.save.domain.usecase.payment

import com.sanibonani.save.domain.repository.payment.BankRepository
import javax.inject.Inject

/**
 * Links a bank row to a ledger row by hand.
 *
 * This is the fallback for money the auto-matcher could not pair, so it writes the pairing the
 * reconciliation would otherwise derive.
 */
class ReconcileBankTransactionUseCase @Inject constructor(
    private val bankRepository: BankRepository
) {
    suspend operator fun invoke(bankTxId: String, ledgerId: String): Result<Unit> =
        bankRepository.reconcileManual(bankTxId, ledgerId)
}
