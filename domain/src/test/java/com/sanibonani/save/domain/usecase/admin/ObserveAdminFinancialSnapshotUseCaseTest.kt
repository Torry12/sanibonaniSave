package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.BankTransaction
import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.payment.BankRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins how the admin dashboard's five financial streams are joined: each one lands in
 * its own slot, and one broken stream empties only its own section.
 */
class ObserveAdminFinancialSnapshotUseCaseTest {

    private val ledgerRepository: LedgerRepository = mockk()
    private val bankRepository: BankRepository = mockk()
    private val payoutRepository: PayoutRepository = mockk()
    private val loanRepository: LoanRepository = mockk()
    private val claimRepository: BeneficiaryClaimRepository = mockk()
    private val useCase = ObserveAdminFinancialSnapshotUseCase(
        ledgerRepository, bankRepository, payoutRepository, loanRepository, claimRepository
    )

    private val groupId = "group-1"

    private val ledger = listOf(LedgerEntry(id = "ledger-1"))
    private val bankTx = listOf(
        BankTransaction(
            groupId = groupId,
            bankReference = "REF-1",
            amount = Money("450.00"),
            transactionDate = "2026-10-25"
        )
    )
    private val payouts = listOf(
        PayoutRequest(groupId = groupId, bankName = "FNB", accountNo = "123456789", branchCode = "250655")
    )
    private val loans = listOf(Loan(id = "loan-1", groupId = groupId))
    private val claims = listOf(BeneficiaryPayoutClaim(id = "claim-1", groupId = groupId))

    private fun stubAll() {
        every { ledgerRepository.observeConsolidatedAuditTrail(groupId) } returns flowOf(Result.success(ledger))
        every { bankRepository.observeUnreconciledTransactions(groupId) } returns flowOf(Result.success(bankTx))
        every { payoutRepository.observePayouts(groupId) } returns flowOf(Result.success(payouts))
        every { loanRepository.getGroupLoans(groupId) } returns flowOf(Result.success(loans))
        every { claimRepository.observeClaimsForGroup(groupId) } returns flowOf(Result.success(claims))
    }

    @Test
    fun `each stream lands in its own section of the snapshot`() = runTest {
        stubAll()

        val snapshot = useCase(groupId).first()

        assertEquals(ledger, snapshot.ledger)
        assertEquals(bankTx, snapshot.unreconciledBankTx)
        assertEquals(payouts, snapshot.payouts)
        assertEquals(loans, snapshot.loans)
        assertEquals(claims, snapshot.claims)
    }

    @Test
    fun `a failed stream empties only its own section`() = runTest {
        stubAll()
        every { ledgerRepository.observeConsolidatedAuditTrail(groupId) } returns
            flowOf(Result.failure(IllegalStateException("ledger offline")))

        val snapshot = useCase(groupId).first()

        assertEquals(emptyList<LedgerEntry>(), snapshot.ledger)
        assertEquals(bankTx, snapshot.unreconciledBankTx)
        assertEquals(payouts, snapshot.payouts)
        assertEquals(loans, snapshot.loans)
        assertEquals(claims, snapshot.claims)
    }
}
