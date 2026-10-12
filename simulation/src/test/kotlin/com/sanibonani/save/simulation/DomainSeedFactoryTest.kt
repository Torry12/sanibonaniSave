package com.sanibonani.save.simulation

import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import org.junit.Assert.*
import org.junit.Test

class DomainSeedFactoryTest {

    @Test
    fun createTestGroupGeneratesValidDomainEntity() {
        val group = DomainSeedFactory.createTestGroup(
            name = "Soweto Unity Stokvel",
            type = GroupType.STOKVEL,
            adminUserId = "admin-101",
            monthlyContribution = 500.0,
            joiningFee = 150.0
        )

        assertEquals("Soweto Unity Stokvel", group.name)
        assertEquals(GroupType.STOKVEL, group.type)
        assertEquals("admin-101", group.adminUserId)
        assertEquals(Money(500.0), group.monthlyContribution)
        assertEquals(Money(150.0), group.joiningFee)
        assertTrue(group.isPublic)
    }

    @Test
    fun createTestMemberAndContributionsGeneratesValidHistory() {
        val member = DomainSeedFactory.createTestMember(
            groupId = "group-101",
            fullName = "Lindiwe Dlamini",
            status = MemberStatus.ACTIVE
        )

        assertEquals("group-101", member.groupId)
        assertEquals("Lindiwe Dlamini", member.fullName)
        assertEquals(MemberStatus.ACTIVE, member.status)
        assertEquals("First National Bank", member.bankName)

        val contributions = DomainSeedFactory.createTestContributions(
            memberId = member.id!!,
            groupId = "group-101",
            monthlyAmount = Money(500.0),
            months = 12
        )

        // 1 joining fee + 12 monthly contributions
        assertEquals(13, contributions.size)
        assertEquals("joining_fee", contributions.first().type)
        assertEquals(Money(150.0), contributions.first().amount)
        assertEquals("contribution", contributions.last().type)
        assertEquals(Money(500.0), contributions.last().amount)
    }

    @Test
    fun createTestLoanAndPayoutRequestGeneratesValidEntities() {
        val loan = DomainSeedFactory.createTestLoan(
            groupId = "group-101",
            memberId = "member-101",
            amount = 2000.0,
            interestRate = 15.0
        )

        assertEquals("group-101", loan.groupId)
        assertEquals("member-101", loan.memberId)
        assertEquals(Money(2000.0), loan.amount)
        assertEquals(Money(2300.0), loan.totalToRepay)

        val payout = DomainSeedFactory.createTestPayoutRequest(
            groupId = "group-101",
            memberId = "member-101",
            amount = 1500.0
        )

        assertEquals("group-101", payout.groupId)
        assertEquals(Money(1500.0), payout.amount)
        assertEquals("First National Bank", payout.bankName)
    }
}
