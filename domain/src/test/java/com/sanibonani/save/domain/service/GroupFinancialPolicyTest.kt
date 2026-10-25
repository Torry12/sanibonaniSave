package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the group-type rules that used to live in four separate places:
 * `AccountingStrategy`, `TreasuryServiceImpl`, `RequestPayoutUseCase`, and `PayoutGuard`.
 *
 * These assertions describe today's behaviour, including the parts that look surprising
 * (a STOKVEL ANNUAL payout is not probation-restricted, ROSCA is not special-cased for
 * probation). They exist so the consolidation cannot silently change a payout rule.
 */
class GroupFinancialPolicyTest {

    private val policy = GroupFinancialPolicy()

    private fun group(
        type: GroupType,
        monthlyContribution: String = "100.00",
        balance: String = "1000.00",
        goalAmount: String = "0.00",
        beneficiaryIncreasePct: Double? = null
    ) = Group(
        id = "g-1",
        name = "Policy Group",
        type = type,
        monthlyContribution = Money(monthlyContribution),
        balance = Money(balance),
        goalAmount = Money(goalAmount),
        beneficiaryIncreasePct = beneficiaryIncreasePct
    )

    private fun member(
        status: MemberStatus = MemberStatus.ACTIVE,
        totalPaid: String = "0.00",
        over65: Int? = null,
        override: String? = null
    ) = Member(
        id = "m-1",
        groupId = "g-1",
        status = status,
        totalPaid = Money(totalPaid),
        beneficiaryOver65Count = over65,
        monthlyContributionOverride = override?.let { Money(it) }
    )

    // ── Expected member contribution ────────────────────────────────────────

    @Test
    fun `burial society charges the base rate when there are no over-65 beneficiaries`() {
        val g = group(GroupType.BURIAL_SOCIETY, beneficiaryIncreasePct = 10.0)
        assertEquals(Money("100.00"), policy.expectedMemberContribution(g, member(over65 = 0)))
        assertEquals(Money("100.00"), policy.expectedMemberContribution(g, member(over65 = null)))
    }

    @Test
    fun `burial society adds a surcharge per over-65 beneficiary`() {
        val g = group(GroupType.BURIAL_SOCIETY, beneficiaryIncreasePct = 10.0)
        assertEquals(Money("120.00"), policy.expectedMemberContribution(g, member(over65 = 2)))
    }

    @Test
    fun `a missing beneficiary increase setting means no surcharge`() {
        val g = group(GroupType.BURIAL_SOCIETY, beneficiaryIncreasePct = null)
        assertEquals(Money("100.00"), policy.expectedMemberContribution(g, member(over65 = 3)))
    }

    @Test
    fun `a member override wins for every group type`() {
        GroupType.entries.forEach { type ->
            val g = group(type, beneficiaryIncreasePct = 10.0)
            val m = member(over65 = 5, override = "42.50")
            assertEquals("override for $type", Money("42.50"), policy.expectedMemberContribution(g, m))
        }
    }

    @Test
    fun `non-burial types charge the group rate`() {
        val types = GroupType.entries.filter { it != GroupType.BURIAL_SOCIETY }
        types.forEach { type ->
            val g = group(type, monthlyContribution = "250.00", beneficiaryIncreasePct = 10.0)
            assertEquals("rate for $type", Money("250.00"), policy.expectedMemberContribution(g, member(over65 = 4)))
        }
    }

    // ── Projected payout ────────────────────────────────────────────────────

    @Test
    fun `burial projected payout is the fixed goal amount`() {
        val g = group(GroupType.BURIAL_SOCIETY, goalAmount = "10000.00")
        assertEquals(Money("10000.00"), policy.projectedPayout(g, listOf(member())))
    }

    @Test
    fun `burial projected payout stays fixed per member`() {
        val g = group(GroupType.BURIAL_SOCIETY, goalAmount = "10000.00")
        assertEquals(Money("10000.00"), policy.projectedPayout(g, listOf(member(), member(), member())))
    }

    @Test
    fun `stokvel projected payout includes the remaining months of the year`() {
        val today = LocalDate.of(2026, 10, 25)
        val g = group(GroupType.STOKVEL, monthlyContribution = "100.00", balance = "1000.00")
        val roster = listOf(member(), member())
        // balance + (100 x 2 members x 2 months remaining) = 1400, split across 2 members
        assertEquals(Money("700.00"), policy.projectedPayout(g, roster, currentDate = today))
    }

    @Test
    fun `stokvel projected payout divides by at least one member`() {
        val today = LocalDate.of(2026, 12, 1)
        val g = group(GroupType.STOKVEL, monthlyContribution = "100.00", balance = "1000.00")
        assertEquals(Money("1000.00"), policy.projectedPayout(g, emptyList(), currentDate = today))
    }

    @Test
    fun `rosca projected payout is one full cycle pot`() {
        val g = group(GroupType.ROSCA, monthlyContribution = "100.00")
        assertEquals(Money("300.00"), policy.projectedPayout(g, listOf(member(), member(), member())))
    }

    @Test
    fun `rosca projected payout ignores suspended members`() {
        val g = group(GroupType.ROSCA, monthlyContribution = "100.00")
        val roster = listOf(member(), member(), member(status = MemberStatus.SUSPENDED))
        assertEquals(Money("200.00"), policy.projectedPayout(g, roster))
    }

    @Test
    fun `investment club payout is proportional to what the member paid in`() {
        val g = group(GroupType.INVESTMENT_CLUB, balance = "1000.00")
        val mine = member(totalPaid = "250.00")
        val roster = listOf(mine, member(totalPaid = "750.00"))
        assertEquals(Money("250.00"), policy.projectedPayout(g, roster, mine))
    }

    @Test
    fun `investment club payout is zero without a member or contributions`() {
        val g = group(GroupType.INVESTMENT_CLUB, balance = "1000.00")
        assertEquals(Money.ZERO, policy.projectedPayout(g, listOf(member(totalPaid = "100.00")), null))
        assertEquals(Money.ZERO, policy.projectedPayout(g, listOf(member(totalPaid = "0.00")), member(totalPaid = "0.00")))
    }

    @Test
    fun `other types split the balance evenly`() {
        val evenTypes = listOf(
            GroupType.EMERGENCY_FUND,
            GroupType.COMMUNITY_SAVINGS,
            GroupType.HYBRID_SAVINGS_GROUP,
            GroupType.OTHER
        )
        evenTypes.forEach { type ->
            val g = group(type, balance = "900.00")
            assertEquals("split for $type", Money("300.00"), policy.projectedPayout(g, listOf(member(), member(), member())))
        }
    }

    // ── Required reserve ────────────────────────────────────────────────────

    @Test
    fun `burial reserve is five months of contributions`() {
        val g = group(GroupType.BURIAL_SOCIETY, monthlyContribution = "150.00", balance = "9999.00")
        assertEquals(Money("750.00"), policy.requiredReserve(g))
    }

    @Test
    fun `stokvel reserve is ten percent of the balance`() {
        val g = group(GroupType.STOKVEL, balance = "1000.00")
        assertEquals(Money("100.00"), policy.requiredReserve(g))
    }

    @Test
    fun `emergency fund reserve is twenty percent of the balance`() {
        val g = group(GroupType.EMERGENCY_FUND, balance = "1000.00")
        assertEquals(Money("200.00"), policy.requiredReserve(g))
    }

    @Test
    fun `other types hold no reserve`() {
        val noReserve = listOf(
            GroupType.INVESTMENT_CLUB,
            GroupType.ROSCA,
            GroupType.COMMUNITY_SAVINGS,
            GroupType.HYBRID_SAVINGS_GROUP,
            GroupType.OTHER
        )
        noReserve.forEach { type ->
            assertEquals("reserve for $type", Money.ZERO, policy.requiredReserve(group(type, balance = "5000.00")))
        }
    }

    // ── Payout type ─────────────────────────────────────────────────────────

    @Test
    fun `payout type follows the group type`() {
        assertEquals(PayoutType.ANNUAL, policy.payoutTypeFor(group(GroupType.STOKVEL)))
        assertEquals(PayoutType.EMERGENCY, policy.payoutTypeFor(group(GroupType.EMERGENCY_FUND)))
        assertEquals(PayoutType.DISINVESTMENT, policy.payoutTypeFor(group(GroupType.INVESTMENT_CLUB)))
        assertEquals(PayoutType.STANDARD, policy.payoutTypeFor(group(GroupType.BURIAL_SOCIETY)))
        assertEquals(PayoutType.STANDARD, policy.payoutTypeFor(group(GroupType.ROSCA)))
        assertEquals(PayoutType.STANDARD, policy.payoutTypeFor(group(GroupType.COMMUNITY_SAVINGS)))
        assertEquals(PayoutType.STANDARD, policy.payoutTypeFor(group(GroupType.HYBRID_SAVINGS_GROUP)))
        assertEquals(PayoutType.STANDARD, policy.payoutTypeFor(group(GroupType.OTHER)))
    }

    // ── Probation restrictions ──────────────────────────────────────────────

    @Test
    fun `burial society only restricts claims during probation`() {
        assertTrue(policy.isProbationRestricted(GroupType.BURIAL_SOCIETY, PayoutType.CLAIM))
        assertFalse(policy.isProbationRestricted(GroupType.BURIAL_SOCIETY, PayoutType.STANDARD))
    }

    @Test
    fun `stokvel restricts standard and emergency payouts but not annual`() {
        assertTrue(policy.isProbationRestricted(GroupType.STOKVEL, PayoutType.STANDARD))
        assertTrue(policy.isProbationRestricted(GroupType.STOKVEL, PayoutType.EMERGENCY))
        assertFalse(policy.isProbationRestricted(GroupType.STOKVEL, PayoutType.ANNUAL))
    }

    @Test
    fun `investment club only restricts disinvestment`() {
        assertTrue(policy.isProbationRestricted(GroupType.INVESTMENT_CLUB, PayoutType.DISINVESTMENT))
        assertFalse(policy.isProbationRestricted(GroupType.INVESTMENT_CLUB, PayoutType.STANDARD))
    }

    @Test
    fun `remaining group types place no probation restriction on payouts`() {
        val unrestricted = listOf(
            GroupType.ROSCA,
            GroupType.EMERGENCY_FUND,
            GroupType.COMMUNITY_SAVINGS,
            GroupType.HYBRID_SAVINGS_GROUP,
            GroupType.OTHER
        )
        unrestricted.forEach { type ->
            PayoutType.entries.forEach { payout ->
                assertFalse("$type/$payout", policy.isProbationRestricted(type, payout))
            }
        }
    }
}
