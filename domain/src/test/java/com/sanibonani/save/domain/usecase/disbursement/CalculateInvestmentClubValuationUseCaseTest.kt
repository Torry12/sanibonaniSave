package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Investment club NAV is the number every member's payout is derived from, so the unit price and
 * the member weights have to reconstruct the group's balance exactly.
 */
class CalculateInvestmentClubValuationUseCaseTest {

    private val useCase = CalculateInvestmentClubValuationUseCase()

    private fun club(balance: Money) = Group(
        id = "g1",
        type = GroupType.INVESTMENT_CLUB,
        balance = balance,
        goalAmount = Money("5000.00")
    )

    private fun member(id: String, paid: Money) = Member(
        id = id,
        groupId = "g1",
        fullName = id.uppercase(),
        status = MemberStatus.ACTIVE,
        totalPaid = paid
    )

    @Test
    fun `rejects groups that are not investment clubs`() {
        val result = useCase(Group(id = "g1", type = GroupType.STOKVEL), emptyList())

        assertTrue(result.isFailure)
        assertEquals("Group is not an Investment Club", result.exceptionOrNull()?.message)
    }

    @Test
    fun `unit price is net asset value per rand contributed`() {
        val valuation = useCase(
            club(Money("1500.00")),
            listOf(member("m1", Money("400.00")), member("m2", Money("600.00")))
        ).getOrThrow()

        assertEquals(Money("1500.00"), valuation.totalAssets)
        assertEquals(Money("1000.00"), valuation.totalUnits)
        assertEquals(Money("1.50"), valuation.unitPrice)

        val m1 = valuation.memberValuations.first { it.memberId == "m1" }
        assertEquals(Money("400.00"), m1.unitsOwned)
        assertEquals(Money("600.00"), m1.marketValue)
        assertEquals(40.0, m1.contributionWeight, 0.0001)

        val m2 = valuation.memberValuations.first { it.memberId == "m2" }
        assertEquals(Money("600.00"), m2.unitsOwned)
        assertEquals(Money("900.00"), m2.marketValue)
        assertEquals(60.0, m2.contributionWeight, 0.0001)

        val distributed = valuation.memberValuations.fold(Money.ZERO) { acc, member -> acc + member.marketValue }
        assertEquals(valuation.totalAssets, distributed)
    }

    @Test
    fun `zero contributions fall back to a one rand unit price`() {
        val valuation = useCase(
            club(Money("250.00")),
            listOf(member("m1", Money.ZERO))
        ).getOrThrow()

        assertEquals(Money("250.00"), valuation.totalAssets)
        assertEquals(Money.ZERO, valuation.totalUnits)
        assertEquals(Money("1.00"), valuation.unitPrice)
        assertTrue(valuation.memberValuations.isEmpty())
    }

    @Test
    fun `unit price rounds exact money division without converting currency to double`() {
        val valuation = useCase(
            club(Money("1000.00")),
            listOf(member("m1", Money("1.00")), member("m2", Money("2.00")))
        ).getOrThrow()

        assertEquals(Money("333.33"), valuation.unitPrice)
    }

    @Test
    fun `shares reconstruct the NAV when the split does not divide evenly`() {
        // Each share was priced on its own, so three equal thirds of R100.00 came out as three
        // R33.33 rows: the last ten cents sat in the group and in nobody's holding, and a payout
        // built on those figures could not be reconciled to the balance it was priced from.
        val valuation = useCase(
            club(Money("100.00")),
            listOf(member("m1", Money("50.00")), member("m2", Money("50.00")), member("m3", Money("50.00")))
        ).getOrThrow()

        val distributed = valuation.memberValuations.fold(Money.ZERO) { acc, v -> acc + v.marketValue }
        assertEquals(valuation.totalAssets, distributed)

        // A tie is decided by contributor order, so the same books always give the same cent.
        assertEquals(Money("33.34"), valuation.memberValuations[0].marketValue)
        assertEquals(Money("33.33"), valuation.memberValuations[1].marketValue)
        assertEquals(Money("33.33"), valuation.memberValuations[2].marketValue)
    }

    // ---- the allocation itself ----

    @Test
    fun `a negative NAV still reconstructs exactly`() {
        // A club can hold a negative balance while its ledger is being rebuilt, and the shares must
        // still add back to it rather than to -R9.99.
        val allocation = allocateProportionally(
            Money("-10.00"),
            listOf(Money("1.00"), Money("1.00"), Money("1.00"))
        )

        assertEquals(Money("-10.00"), allocation.fold(Money.ZERO) { acc, m -> acc + m })
        assertEquals(listOf(Money("-3.33"), Money("-3.33"), Money("-3.34")), allocation)
    }

    @Test
    fun `a member who contributed nothing is allocated nothing`() {
        val allocation = allocateProportionally(Money("100.00"), listOf(Money.ZERO, Money("100.00")))

        assertEquals(listOf(Money.ZERO, Money("100.00")), allocation)
    }

    @Test
    fun `weights that sum to nothing allocate nothing instead of dividing by zero`() {
        val allocation = allocateProportionally(Money("100.00"), listOf(Money.ZERO, Money.ZERO))

        assertEquals(listOf(Money.ZERO, Money.ZERO), allocation)
    }

    @Test
    fun `the whole NAV goes to a lone contributor`() {
        assertEquals(
            listOf(Money("99.99")),
            allocateProportionally(Money("99.99"), listOf(Money("1.00")))
        )
    }

    @Test
    fun `unequal contributors still add back to the NAV cent for cent`() {
        // 1/7, 2/7 and 4/7 of R10.00 divide to nothing clean: 1.4285, 2.8571, 5.7142.
        val allocation = allocateProportionally(
            Money("10.00"),
            listOf(Money("1.00"), Money("2.00"), Money("4.00"))
        )

        assertEquals(listOf(Money("1.43"), Money("2.86"), Money("5.71")), allocation)
        assertEquals(Money("10.00"), allocation.fold(Money.ZERO) { acc, m -> acc + m })
    }
}
