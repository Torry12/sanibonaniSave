package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A burial claim is a cash payout gated by waiting periods, group reserves and arrears. Each rule
 * is a money decision, so the gates are characterised here rather than inferred from call sites.
 */
class ValidateBurialClaimEligibilityUseCaseTest {

    private val useCase = ValidateBurialClaimEligibilityUseCase()

    private fun burialGroup(
        currentMembers: Int = 10,
        minMembersForClaims: Int = 0,
        minReservePercentage: Double = 0.0,
        balance: Money = Money("50000.00"),
        claimAmount: Money = Money("10000.00")
    ) = Group(
        id = "g1",
        type = GroupType.BURIAL_SOCIETY,
        currentMembers = currentMembers,
        minMembersForClaims = minMembersForClaims,
        minReservePercentage = minReservePercentage,
        balance = balance,
        burialClaimAmount = claimAmount,
        goalAmount = Money("20000.00")
    )

    private fun member(
        status: MemberStatus = MemberStatus.ACTIVE,
        joinedAt: String? = "2025-01-01T00:00:00Z"
    ) = Member(
        id = "m1",
        groupId = "g1",
        fullName = "Alpha Member",
        status = status,
        joinedAt = joinedAt
    )

    private fun reason(result: ValidateBurialClaimEligibilityUseCase.EligibilityResult): String {
        assertTrue(result is ValidateBurialClaimEligibilityUseCase.EligibilityResult.Ineligible)
        return (result as ValidateBurialClaimEligibilityUseCase.EligibilityResult.Ineligible).reason
    }

    @Test
    fun `only burial societies can claim`() {
        val result = useCase(member(), Group(type = GroupType.STOKVEL), "Natural causes", "2026-01-01")

        assertEquals("Burial Societies only.", reason(result))
    }

    @Test
    fun `suspended members cannot claim`() {
        val result = useCase(member(status = MemberStatus.SUSPENDED), burialGroup(), "Natural causes", "2026-01-01")

        assertEquals("Suspended.", reason(result))
    }

    @Test
    fun `members pending payment cannot claim`() {
        val result = useCase(member(status = MemberStatus.PENDING_PAYMENT), burialGroup(), "Natural causes", "2026-01-01")

        assertEquals("Pending payment.", reason(result))
    }

    @Test
    fun `any arrears block the claim`() {
        val result = useCase(
            member(),
            burialGroup(),
            "Natural causes",
            "2026-01-01",
            overdueAmount = Money("10.00")
        )

        assertTrue(reason(result).startsWith("Arrears:"))
    }

    @Test
    fun `group must meet its minimum member count`() {
        val result = useCase(
            member(),
            burialGroup(currentMembers = 3, minMembersForClaims = 5),
            "Natural causes",
            "2026-01-01"
        )

        assertTrue(reason(result).contains("at least 5 active members"))
    }

    @Test
    fun `group reserves must meet the configured percentage`() {
        val result = useCase(
            member(),
            burialGroup(minReservePercentage = 100.0, balance = Money("5000.00"), claimAmount = Money("10000.00")),
            "Natural causes",
            "2026-01-01"
        )

        assertTrue(reason(result).contains("below required"))
    }

    @Test
    fun `unparseable joined date is rejected`() {
        val result = useCase(member(joinedAt = "not-a-date"), burialGroup(), "Natural causes", "2026-01-01")

        assertEquals("Invalid start date.", reason(result))
    }

    @Test
    fun `unparseable death date is rejected`() {
        val result = useCase(member(), burialGroup(), "Natural causes", "31-12-2025")

        assertEquals("Invalid death date.", reason(result))
    }

    @Test
    fun `suicide inside twelve months is excluded`() {
        val result = useCase(
            member(joinedAt = "2025-06-01"),
            burialGroup(),
            "Suicide",
            "2025-12-01"
        )

        assertEquals("Suicide 12-month wait.", reason(result))
    }

    @Test
    fun `suicide after twelve months is eligible`() {
        val result = useCase(
            member(joinedAt = "2024-12-01"),
            burialGroup(),
            "Suicide",
            "2026-01-01"
        )

        assertEquals(ValidateBurialClaimEligibilityUseCase.EligibilityResult.Eligible, result)
    }

    @Test
    fun `natural death inside six months is excluded`() {
        val result = useCase(
            member(joinedAt = "2025-10-01"),
            burialGroup(),
            "Natural causes",
            "2025-12-01"
        )

        assertEquals("Natural death 6-month wait.", reason(result))
    }

    @Test
    fun `natural death after six months is eligible`() {
        val result = useCase(
            member(joinedAt = "2025-06-01"),
            burialGroup(),
            "Natural causes",
            "2026-01-01"
        )

        assertEquals(ValidateBurialClaimEligibilityUseCase.EligibilityResult.Eligible, result)
    }

    @Test
    fun `accidental death is exempt from the six-month wait`() {
        val result = useCase(
            member(joinedAt = "2025-10-01"),
            burialGroup(),
            "Accidental fall",
            "2025-12-01"
        )

        assertEquals(ValidateBurialClaimEligibilityUseCase.EligibilityResult.Eligible, result)
    }
}
