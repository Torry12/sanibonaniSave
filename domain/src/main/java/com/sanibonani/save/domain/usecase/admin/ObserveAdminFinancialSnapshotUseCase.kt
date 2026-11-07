package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.BankTransaction
import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.payment.BankRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.scan

/**
 * Joins the five live streams behind the admin financial dashboard into one snapshot,
 * so the screen observes a single flow instead of holding five repositories.
 */
class ObserveAdminFinancialSnapshotUseCase @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val bankRepository: BankRepository,
    private val payoutRepository: PayoutRepository,
    private val loanRepository: LoanRepository,
    private val claimRepository: BeneficiaryClaimRepository
) {
    data class Snapshot(
        val ledger: List<LedgerEntry>,
        val unreconciledBankTx: List<BankTransaction>,
        val payouts: List<PayoutRequest>,
        val loans: List<Loan>,
        val claims: List<BeneficiaryPayoutClaim>
    )

    operator fun invoke(groupId: String): Flow<Snapshot> = combine(
        ledgerRepository.observeConsolidatedAuditTrail(groupId).orLastKnownList(),
        bankRepository.observeUnreconciledTransactions(groupId).orLastKnownList(),
        payoutRepository.observePayouts(groupId).orLastKnownList(),
        loanRepository.getGroupLoans(groupId).orLastKnownList(),
        claimRepository.observeClaimsForGroup(groupId).orLastKnownList()
    ) { ledger, bankTx, payouts, loans, claims ->
        Snapshot(ledger, bankTx, payouts, loans, claims)
    }

    /**
     * A stream that never answered has nothing to show, so it degrades to an empty section rather than
     * tearing the dashboard down. A stream that answered and then had its refresh refused keeps the rows
     * it already delivered: an unread refresh is not evidence that the book went empty, and the
     * repository now reports one as a failure on the same flow.
     */
    private fun <T> Flow<Result<List<T>>>.orLastKnownList(): Flow<List<T>> =
        scan(emptyList<T>()) { lastKnown, result -> result.getOrNull() ?: lastKnown }.drop(1)
}
