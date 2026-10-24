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
}
