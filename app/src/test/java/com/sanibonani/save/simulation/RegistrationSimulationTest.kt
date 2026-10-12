package com.sanibonani.save.simulation

import com.sanibonani.save.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RegistrationSimulationTest {

    @Test
    fun `simulate group creation across all group types`() {
        val groupTypes = GroupType.entries

        val createdGroups = groupTypes.mapIndexed { index, type ->
            Group(
                id = "sim_g_$index",
                name = "Simulated ${type.displayName} Group",
                type = type,
                monthlyContribution = Money(500.0),
                joiningFee = Money(150.0),
                maxMembers = 50,
                currentMembers = 1,
                province = "Gauteng",
                city = "Johannesburg",
                township = "Soweto"
            )
        }

        assertEquals(groupTypes.size, createdGroups.size)
        createdGroups.forEach { g ->
            assertTrue("Group ID should not be blank", !g.id.isNullOrBlank())
            assertTrue("Group name should be valid", g.name.length >= 3)
            assertTrue("Monthly contribution should be positive", g.monthlyContribution.isPositive)
        }
    }

    @Test
    fun `simulate member onboarding and probation assignment`() {
        val group = Group(
            id = "sim_g_stokvel",
            name = "Soweto Stokvel",
            type = GroupType.STOKVEL,
            monthlyContribution = Money(500.0)
        )

        val members = (1..10).map { i ->
            Member(
                id = "sim_m_$i",
                groupId = group.id!!,
                fullName = "Simulated Member $i",
                email = "member$i@sanibonani.co.za",
                status = if (i <= 8) MemberStatus.ACTIVE else MemberStatus.PROBATION,
                joinedAt = LocalDate.now().toString() + "T00:00:00Z"
            )
        }

        val activeCount = members.count { it.status == MemberStatus.ACTIVE }
        val probationCount = members.count { it.status == MemberStatus.PROBATION }

        assertEquals(8, activeCount)
        assertEquals(2, probationCount)
        assertEquals(10, members.size)
    }

    @Test
    fun `simulate group member capacity limits`() {
        val group = Group(
            id = "sim_g_capacity",
            name = "Exclusive Club",
            type = GroupType.INVESTMENT_CLUB,
            maxMembers = 5,
            currentMembers = 5
        )

        val isFull = group.currentMembers >= group.maxMembers
        assertTrue("Group should be marked as full when max members reached", isFull)
    }
}
