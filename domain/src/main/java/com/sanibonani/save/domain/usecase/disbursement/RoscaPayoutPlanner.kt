package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberBehaviorTrack
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.usecase.disbursement.CalculateRoscaRotationUseCase.RoscaSubgroup

internal data class RoscaLoanDeduction(
    val loanId: String?,
    val amount: Money
)

internal data class RoscaPayoutPlan(
    val period: String,
    val totalPot: Money,
    val deductions: List<RoscaLoanDeduction>,
    val netPayout: Money
)

internal sealed interface RoscaPayoutDecision {
    data class Pay(val plan: RoscaPayoutPlan) : RoscaPayoutDecision
    data class Skip(val reason: String) : RoscaPayoutDecision
}

/**
 * The pure half of a ROSCA rotation: who may be paid this period and how much of the pot survives
 * the loan offsets. Repository reads, eligibility guards, persistence, audit and events stay in
 * [ExecuteRoscaPayoutUseCase] so every decision here can be tested without a treasury.
 */
internal object RoscaPayoutPlanner {

    /**
     * Checks the payoutee and the data the rotation depends on. Returns a skip reason, or null
     * when the member may proceed to the eligibility guard. A null [payoutQueue] means the queue
     * could not be read; an empty list is a readable empty queue.
     */
    fun preflight(
        groupId: String,
        period: String,
        member: Member,
        loanLedgerUnreadable: Boolean,
        payoutQueue: List<PayoutRequest>?
    ): String? {
        if (member.status != MemberStatus.ACTIVE) return "Not active"
        val bankName = member.bankName
        val accountNo = member.accountNo
        if (bankName.isNullOrBlank() || accountNo.isNullOrBlank()) return "No banking info"
        when (val check = validateBankDetails(bankName, accountNo, member.branchCode)) {
            is BankDetailsResult.Valid -> Unit
            is BankDetailsResult.Invalid -> return check.reason
        }
        // Paying the gross pot would hand over money already earmarked for loans this rotation
        // could not read. The next rotation retries with a readable ledger.
        if (loanLedgerUnreadable) return "Outstanding loans could not be read"
        if (payoutQueue == null) return "Outstanding payouts could not be read"
        // Deliberately not narrowed to the rotation types: any unsettled request addressed to
        // this member in this period holds the rotation. A held rotation retries on the next run;
        // a second payout is money out of the treasury.
        val alreadyRequested = payoutQueue.any { request ->
            request.groupId == groupId && request.memberId == member.id &&
                (request.createdAt == null || request.createdAt.startsWith(period))
        }
        if (alreadyRequested) return "A payout for $period is already outstanding"
        return null
    }

    /**
     * Values a payoutee the orchestrator has already screened. The pot is the paid contributions
     * of the member's subgroup for this period, and loans consume it in ledger order until it is
     * exhausted.
     */
    fun plan(
        period: String,
        member: Member,
        subgroup: RoscaSubgroup?,
        contributions: List<Contribution>,
        activeLoans: List<Loan>,
        behavior: MemberBehaviorTrack?
    ): RoscaPayoutDecision {
        if (behavior?.totalOutstandingAmount?.isPositive == true) {
            return RoscaPayoutDecision.Skip("Has arrears")
        }
        if (subgroup == null) {
            // Silently dropping a payoutee reads as a completed rotation on the admin screen.
            return RoscaPayoutDecision.Skip("Member is not assigned to a rotation subgroup")
        }
        val actualCollectedPot = contributions
            .filter {
                it.memberId in subgroup.memberIds &&
                    it.dueDate.startsWith(period) &&
                    it.status == ContributionStatus.PAID
            }
            .fold(Money.ZERO) { acc, contribution -> acc + contribution.amount }
        if (!actualCollectedPot.isPositive) {
            return RoscaPayoutDecision.Skip("No contributions collected")
        }
        var remainingPot = actualCollectedPot
        val deductions = buildList {
            for (loan in activeLoans) {
                if (!remainingPot.isPositive) break
                val payAmount = loan.balanceRemaining.min(remainingPot)
                add(RoscaLoanDeduction(loanId = loan.id, amount = payAmount))
                remainingPot -= payAmount
            }
        }
        return RoscaPayoutDecision.Pay(
            RoscaPayoutPlan(
                period = period,
                totalPot = actualCollectedPot,
                deductions = deductions,
                netPayout = remainingPot
            )
        )
    }
}
