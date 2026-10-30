package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberBehaviorTrack
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.model.PayoutType
import com.sanibonani.save.domain.usecase.disbursement.CalculateRoscaRotationUseCase.RoscaSubgroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rotation planner is the pure half of the ROSCA process: it decides who is paid this period
 * and how much of the pot survives the loan offsets. Keeping those decisions out of the
 * repository/orchestration code makes every skip reason and every deduction testable on its own.
 */
class RoscaPayoutPlannerTest {

    private val member = Member(
        id = "m1",
        groupId = "g1",
        fullName = "Nomsa",
        status = MemberStatus.ACTIVE,
        bankName = "FNB",
        accountNo = "123456789",
        branchCode = "250611"
    )
    private val subgroup = RoscaSubgroup(
        id = "s1",
        memberIds = listOf("m1", "m2"),
        items = emptyList()
    )
    private val paidContribution = Contribution(
        memberId = "m1",
        groupId = "g1",
        amount = Money("300.0"),
        dueDate = "2026-03-15",
        status = ContributionStatus.PAID
    )

    private fun preflight(
        member: Member = this.member,
        loanLedgerUnreadable: Boolean = false,
        payoutQueue: List<PayoutRequest>? = emptyList()
    ) = RoscaPayoutPlanner.preflight(
        groupId = "g1",
        period = "2026-03",
        member = member,
        loanLedgerUnreadable = loanLedgerUnreadable,
        payoutQueue = payoutQueue
    )

    private fun plan(
        member: Member = this.member,
        subgroup: RoscaSubgroup? = this.subgroup,
        contributions: List<Contribution> = listOf(paidContribution),
        activeLoans: List<Loan> = emptyList(),
        behavior: MemberBehaviorTrack? = null
    ) = RoscaPayoutPlanner.plan(
        period = "2026-03",
        member = member,
        subgroup = subgroup,
        contributions = contributions,
        activeLoans = activeLoans,
        behavior = behavior
    )

    private fun outstandingRequest(createdAt: String?) = PayoutRequest(
        id = "p-existing",
        groupId = "g1",
        memberId = "m1",
        amount = Money("300.0"),
        bankName = "FNB",
        accountNo = "123456789",
        branchCode = "250611",
        status = PayoutStatus.PENDING,
        type = PayoutType.ROTATION,
        createdAt = createdAt
    )

    @Test
    fun `an inactive member is skipped before any money is valued`() {
        val reason = preflight(member = member.copy(status = MemberStatus.SUSPENDED))

        assertEquals("Not active", reason)
    }

    @Test
    fun `a member without banking details is skipped`() {
        val reason = preflight(member = member.copy(bankName = null))

        assertEquals("No banking info", reason)
    }

    @Test
    fun `an account number the validator rejects is skipped with its reason`() {
        val reason = preflight(member = member.copy(accountNo = "12ab456789"))

        assertTrue(reason?.contains("digits") == true)
    }

    @Test
    fun `a branch code the validator rejects is skipped with its reason`() {
        val reason = preflight(member = member.copy(branchCode = "2506"))

        assertTrue(reason?.contains("Branch code") == true)
    }

    @Test
    fun `an unreadable loan ledger holds the payoutee`() {
        val reason = preflight(loanLedgerUnreadable = true)

        assertEquals("Outstanding loans could not be read", reason)
    }

    @Test
    fun `an unreadable payout queue holds the rotation`() {
        val reason = preflight(payoutQueue = null)

        assertEquals("Outstanding payouts could not be read", reason)
    }

    @Test
    fun `an unsettled payout for this period holds the rotation`() {
        val reason = preflight(
            payoutQueue = listOf(outstandingRequest("2026-03-02T09:15:00+00:00"))
        )

        assertEquals("A payout for 2026-03 is already outstanding", reason)
    }

    @Test
    fun `an unsettled payout from an earlier period does not hold this rotation`() {
        val reason = preflight(
            payoutQueue = listOf(outstandingRequest("2026-02-27T09:15:00+00:00"))
        )

        assertNull(reason)
    }

    @Test
    fun `a member in arrears is skipped`() {
        val decision = plan(
            behavior = MemberBehaviorTrack(memberId = "m1", totalOutstandingAmount = Money("50.0"))
        )

        assertEquals(RoscaPayoutDecision.Skip("Has arrears"), decision)
    }

    @Test
    fun `a member who is in no rotation subgroup is reported instead of vanishing`() {
        val decision = plan(subgroup = null)

        assertTrue((decision as RoscaPayoutDecision.Skip).reason.contains("subgroup"))
    }

    @Test
    fun `a pot with no paid contributions is skipped`() {
        val decision = plan(
            contributions = listOf(
                paidContribution.copy(status = ContributionStatus.DUE),
                paidContribution.copy(dueDate = "2026-02-15")
            )
        )

        assertEquals(RoscaPayoutDecision.Skip("No contributions collected"), decision)
    }

    @Test
    fun `only paid contributions from the subgroup and the current period fund the pot`() {
        val decision = plan(
            contributions = listOf(
                paidContribution,
                paidContribution.copy(memberId = "m2", amount = Money("500.0")),
                paidContribution.copy(status = ContributionStatus.DUE, amount = Money("100.0")),
                paidContribution.copy(dueDate = "2026-02-15", amount = Money("50.0")),
                paidContribution.copy(memberId = "outsider", amount = Money("900.0"))
            )
        ) as RoscaPayoutDecision.Pay

        assertEquals(Money("800.0"), decision.plan.totalPot)
        assertEquals(Money("800.0"), decision.plan.netPayout)
        assertTrue(decision.plan.deductions.isEmpty())
    }

    @Test
    fun `loan deductions are capped by the pot in ledger order`() {
        val first = Loan(
            id = "l1",
            memberId = "m1",
            groupId = "g1",
            totalToRepay = Money("500.0"),
            totalRepaid = Money("300.0"),
            status = LoanStatus.ACTIVE
        )
        val second = Loan(
            id = "l2",
            memberId = "m1",
            groupId = "g1",
            totalToRepay = Money("200.0"),
            totalRepaid = Money.ZERO,
            status = LoanStatus.PARTIALLY_PAID
        )

        val decision = plan(activeLoans = listOf(first, second)) as RoscaPayoutDecision.Pay

        assertEquals(Money("300.0"), decision.plan.totalPot)
        assertEquals(
            listOf(
                RoscaLoanDeduction(loanId = "l1", amount = Money("200.0")),
                RoscaLoanDeduction(loanId = "l2", amount = Money("100.0"))
            ),
            decision.plan.deductions
        )
        assertTrue(decision.plan.netPayout.isZero)
    }

    @Test
    fun `a pot the offsets do not exhaust leaves a positive net payout`() {
        val loan = Loan(
            id = "l1",
            memberId = "m1",
            groupId = "g1",
            totalToRepay = Money("150.0"),
            totalRepaid = Money.ZERO,
            status = LoanStatus.ACTIVE
        )

        val decision = plan(
            contributions = listOf(paidContribution.copy(amount = Money("500.0"))),
            activeLoans = listOf(loan)
        ) as RoscaPayoutDecision.Pay

        assertEquals(Money("500.0"), decision.plan.totalPot)
        assertEquals(Money("150.0"), decision.plan.deductions.single().amount)
        assertEquals(Money("350.0"), decision.plan.netPayout)
    }
}
