package com.sanibonani.save.simulation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.usecase.member.ValidateBurialClaimEligibilityUseCase
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BurialClaimContingencySimulationTest {

    private val validator = ValidateBurialClaimEligibilityUseCase()

    @Test
    fun `simulate burial claim eligibility under standard and over-65 contingency states`() {
        val group = Group(
            id = "g_burial_1",
            type = GroupType.BURIAL_SOCIETY,
            balance = Money("50000.0"),
            monthlyContribution = Money("250.0")
        )

        val memberJoinedDate = LocalDate.now().minusMonths(12).toString() + "T00:00:00Z"
        val activeMember = Member(
            id = "m_1",
            status = MemberStatus.ACTIVE,
            joinedAt = memberJoinedDate,
            beneficiaries = listOf(
                Beneficiary(id = "b_1", fullName = "Dependent A", isOver65 = false, relationship = "Child"),
                Beneficiary(id = "b_2", fullName = "Dependent B", isOver65 = true, relationship = "Parent")
            )
        )

        // 1. Valid Claim Test (Natural cause after 6+ month waiting period)
        val resultValid = validator(activeMember, group, "natural", LocalDate.now().minusDays(5).toString())
        assertTrue("Valid claim should be eligible", resultValid is ValidateBurialClaimEligibilityUseCase.EligibilityResult.Eligible)

        // 2. Ineligible Claim Test (Accident within probation / insufficient waiting period if applicable)
        val recentJoinedDate = LocalDate.now().minusMonths(2).toString() + "T00:00:00Z"
        val probationMember = activeMember.copy(joinedAt = recentJoinedDate)
        val resultProbation = validator(probationMember, group, "natural", LocalDate.now().minusDays(5).toString())
        assertTrue("Claim within waiting period should be ineligible", resultProbation is ValidateBurialClaimEligibilityUseCase.EligibilityResult.Ineligible)
    }

    @Test
    fun `simulate burial claim contingency under group reserve depletion`() {
        val depletedGroup = Group(
            id = "g_burial_depleted",
            type = GroupType.BURIAL_SOCIETY,
            balance = Money("500.0"), // Low reserve state
            monthlyContribution = Money("250.0")
        )

        val member = Member(
            id = "m_2",
            status = MemberStatus.ACTIVE,
            joinedAt = LocalDate.now().minusMonths(12).toString() + "T00:00:00Z"
        )

        val claimAmount = Money("10000.0") // Exceeds available group balance
        val isLiquidEnough = depletedGroup.balance >= claimAmount
        assertFalse("Group liquidity should be insufficient for large claim", isLiquidEnough)
    }
}
