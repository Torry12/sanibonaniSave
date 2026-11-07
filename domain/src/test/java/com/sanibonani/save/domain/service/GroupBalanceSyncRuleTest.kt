package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.IssueSeverity
import com.sanibonani.save.domain.model.LedgerDrift
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The admin's data-verification panel answers one money question above all others: does the balance
 * the group shows match the ledger behind it. `GroupBalanceSyncRule` used to answer that by reading
 * the newest cached `balance_after` column, which is a value the server rewrites during a rebuild and
 * which any stale row can make agree with a wrong balance. These tests pin the rule to the sum of the
 * rows, to the same one-cent tolerance the reconciliation engine and `recalculate_all_group_financials_v3`
 * use, and to staying silent when there is no ledger cached at all.
 */
class GroupBalanceSyncRuleTest {

    private val rule = GroupBalanceSyncRule()

    private fun group(balance: String) = Group(
        id = "g-1",
        name = "Balance rule group",
        balance = Money(balance)
    )

    private fun ledgerEntry(
        amount: String,
        balanceAfter: String,
        createdAt: String? = "2026-10-18T00:00:00Z"
    ) = LedgerEntry(
        id = "ledger-$amount",
        groupId = "g-1",
        amount = Money(amount),
        balanceAfter = Money(balanceAfter),
        category = "contribution",
        createdAt = createdAt
    )

    private fun context(balance: String, vararg entries: LedgerEntry) =
        GroupVerificationContext(group = group(balance), members = emptyList(), ledger = entries.toList())

    @Test
    fun `a ledger that sums to the recorded balance reports nothing`() = runBlocking {
        val issues = rule.verify(
            context("900.00", ledgerEntry("400.00", "400.00"), ledgerEntry("500.00", "900.00"))
        )

        assertEquals(emptyList<String>(), issues.map { it.message })
    }

    @Test
    fun `a gap between the balance and the sum of the rows is a critical finding`() = runBlocking {
        val issues = rule.verify(
            context("1000.00", ledgerEntry("400.00", "400.00"), ledgerEntry("500.00", "900.00"))
        )

        assertEquals(1, issues.size)
        assertEquals(IssueSeverity.CRITICAL, issues.single().severity)
        assertEquals("Financial", issues.single().category)
    }

    @Test
    fun `a stale running balance on the newest row cannot hide the drift`() = runBlocking {
        // The rows sum to R900 while the group claims R1000. The newest row's cached
        // balance_after says R1000, which is exactly what the old rule compared against the
        // group balance - so it reported a clean book for a group that is short by R100.
        val issues = rule.verify(
            context("1000.00", ledgerEntry("400.00", "400.00"), ledgerEntry("500.00", "1000.00"))
        )

        assertEquals(1, issues.size)
        // The rule prints amounts through Money.format(), whose separators depend on the JVM
        // locale, so the expected figure is formatted the same way rather than hard-coded.
        assertTrue(
            "the finding must name the ledger sum, not the cached column",
            issues.single().message.contains(Money("900.00").format())
        )
    }

    @Test
    fun `the finding does not depend on the order the rows arrive in`() = runBlocking {
        val forward = rule.verify(
            context("1000.00", ledgerEntry("400.00", "400.00"), ledgerEntry("500.00", "900.00"))
        )
        val reversed = rule.verify(
            context("1000.00", ledgerEntry("500.00", "900.00"), ledgerEntry("400.00", "400.00"))
        )

        assertEquals(forward, reversed)
    }

    @Test
    fun `a row with no timestamp is still part of the ledger`() = runBlocking {
        val issues = rule.verify(
            context("1000.00", ledgerEntry("400.00", "400.00"), ledgerEntry("500.00", "900.00", createdAt = null))
        )

        assertEquals(1, issues.size)
    }

    @Test
    fun `a one cent gap is tolerated, the same way the reconciliation engine tolerates it`() = runBlocking {
        val issues = rule.verify(context("900.01", ledgerEntry("900.00", "900.00")))

        assertEquals(emptyList<String>(), issues.map { it.message })
        assertTrue(LedgerDrift.isBalanced(Money("0.01")))
    }

    @Test
    fun `two cents of gap is reported`() = runBlocking {
        assertEquals(1, rule.verify(context("900.02", ledgerEntry("900.00", "900.00"))).size)
    }

    @Test
    fun `an unsynced ledger is not reported as a clean book`() = runBlocking {
        // Nothing cached means nothing audited. The rule must stay silent rather than
        // implying the balance was verified, and must never imply a drift it cannot see.
        val issues = rule.verify(context("1000.00"))

        assertEquals(emptyList<String>(), issues.map { it.message })
    }

    @Test
    fun `the member total rule shares the ledger tolerance`() = runBlocking {
        val member = Member(id = "m-1", groupId = "g-1", totalPaid = Money("300.01"))
        val contributions = listOf(
            Contribution(
                id = "c-1",
                memberId = "m-1",
                groupId = "g-1",
                amount = Money("300.00"),
                status = ContributionStatus.PAID
            )
        )
        val context = MemberVerificationContext(member = member, group = group("0.00"), contributions = contributions)

        assertEquals(emptyList<String>(), MemberTotalPaidSyncRule().verify(context).map { it.message })

        val overTolerance = context.copy(member = member.copy(totalPaid = Money("300.02")))
        assertEquals(1, MemberTotalPaidSyncRule().verify(overTolerance).size)
    }
}
