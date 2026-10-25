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
import kotlinx.coroutines.flow.map

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
        ledgerRepository.observeConsolidatedAuditTrail(groupId).orEmptyList(),
        bankRepository.observeUnreconciledTransactions(groupId).orEmptyList(),
        payoutRepository.observePayouts(groupId).orEmptyList(),
        loanRepository.getGroupLoans(groupId).orEmptyList(),
        claimRepository.observeClaimsForGroup(groupId).orEmptyList()
    ) { ledger, bankTx, payouts, loans, claims ->
        Snapshot(ledger, bankTx, payouts, loans, claims)
    }

    /** A failed stream degrades to empty data instead of tearing down the dashboard. */
    private fun <T> Flow<Result<List<T>>>.orEmptyList(): Flow<List<T>> =
        map { result -> result.getOrDefault(emptyList()) }
}
