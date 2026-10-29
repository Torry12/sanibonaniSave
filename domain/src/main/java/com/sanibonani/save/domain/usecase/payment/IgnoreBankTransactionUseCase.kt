package com.sanibonani.save.domain.usecase.payment

import com.sanibonani.save.domain.repository.payment.BankRepository
import javax.inject.Inject

/**
 * Marks a bank row as not belonging to this group.
 *
 * The reason is kept because an unmatched row that simply disappears looks like a sync bug; the
 * note is what tells the next reviewer it was a decision.
 */
class IgnoreBankTransactionUseCase @Inject constructor(
    private val bankRepository: BankRepository
) {
    suspend operator fun invoke(bankTxId: String, reason: String): Result<Unit> =
        bankRepository.ignoreTransaction(bankTxId, reason)
}
