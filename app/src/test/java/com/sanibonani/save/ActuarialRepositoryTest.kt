package com.sanibonani.save

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.data.repository.ActuarialRepositoryImpl
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import javax.inject.Provider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test


class ActuarialRepositoryTest {

    private lateinit var repo: ActuarialRepositoryImpl
    private val groupRepo = mockk<GroupRepository>()
    private val memberRepo = mockk<MemberRepository>()

    @Before
    fun setUp() {
        val memberRepoProvider = mockk<Provider<MemberRepository>>()
        every { memberRepoProvider.get() } returns memberRepo
        repo = ActuarialRepositoryImpl(groupRepo, memberRepoProvider)
    }

    @Test
    fun `pure premium is positive for valid inputs`() {
        val metrics = repo.computeMetrics(
            membersCount = 24, balance = 45000.0,
            mortalityRatePct = 0.82, avgClaim = 15000.0,
            safetyLoadingPct = 35.0, adminCostPerMember = 25.0,
            annualDiscountRatePct = 8.5, currentPremium = 250.0,
            claimsPaid = 18000.0, totalContributions = 108000.0,
            paymentRatePct = 95.0,
            totalExpectedContributionsAnnual = 72000.0
        )
        assertTrue("Pure premium must be > 0", metrics.purePremium > 0)
    }

    @Test
    fun `gross premium is greater than pure premium`() {
        val m = repo.computeMetrics(
            membersCount = 24, balance = 45000.0, mortalityRatePct = 0.82,
            avgClaim = 15000.0, safetyLoadingPct = 35.0, adminCostPerMember = 25.0,
            annualDiscountRatePct = 8.5, currentPremium = 250.0,
            claimsPaid = 18000.0, totalContributions = 108000.0,
            paymentRatePct = 95.0, totalExpectedContributionsAnnual = 72000.0
        )
        assertTrue(m.grossPremium > m.purePremium)
    }

    @Test
    fun `risk score is within 0 to 100`() {
        val m = repo.computeMetrics(24, 45000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 250.0, 18000.0, 108000.0, 95.0, 72000.0)
        assertTrue(m.compositeRiskScore in 0..100)
    }

    @Test
    fun `reserve adequacy improves with higher balance`() {
        val mLow = repo.computeMetrics(24, 10000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 250.0, 5000.0, 30000.0, 90.0, 72000.0)
        val mHigh = repo.computeMetrics(24, 80000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 250.0, 5000.0, 30000.0, 90.0, 72000.0)
        assertTrue(mHigh.reserveAdequacyPct > mLow.reserveAdequacyPct)
    }

    @Test
    fun `APV is less than undiscounted expected claims`() {
        val m = repo.computeMetrics(24, 45000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 250.0, 18000.0, 108000.0, 95.0, 72000.0)
        assertTrue(
            "APV should be less than undiscounted claims due to time value",
            m.actuarialPresentValue < m.expectedAnnualClaims
        )
    }

    @Test
    fun `sustainable group has positive insolvency months`() {
        val m = repo.computeMetrics(24, 45000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 500.0, 5000.0, 60000.0, 100.0, 144000.0)
        assertEquals(Int.MAX_VALUE, m.insolvencyMonths)
    }

    @Test
    fun `loss ratio is zero when no claims paid`() {
        val m = repo.computeMetrics(24, 45000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 250.0, 0.0, 108000.0, 95.0, 72000.0)
        assertEquals(0.0, m.lossRatioPct, 0.001)
    }

    @Test
    fun `calculateMemberContribution for non-burial society returns group contribution`() {
        val group = Group(type = GroupType.STOKVEL, monthlyContribution = 500.0)
        val member = Member()
        val result = repo.calculateMemberContribution(group, member)
        assertEquals(500.0, result, 0.0)
    }

    @Test
    fun `calculateMemberContribution for burial society with beneficiaries over 65`() {
        val group = Group(
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = 200.0,
            beneficiaryIncreasePct = 10.0
        )
        val member = Member(beneficiaryOver65Count = 2)
        // 200 + (200 * 0.1 * 2) = 200 + 40 = 240
        val result = repo.calculateMemberContribution(group, member)
        assertEquals(240.0, result, 0.0)
    }

    @Test
    fun `calculateMemberContribution uses override if present`() {
        val group = Group(type = GroupType.BURIAL_SOCIETY, monthlyContribution = 200.0)
        val member = Member(monthlyContributionOverride = 350.0)
        val result = repo.calculateMemberContribution(group, member)
        assertEquals(350.0, result, 0.0)
    }

    @Test
    fun `calculateDynamicJoiningFee increases with reserves`() = runBlocking {
        val groupId = "test-group"
        val baseGroup = Group(id = groupId, joiningFee = 100.0, balance = 0.0)
        
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(baseGroup)
        coEvery { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(listOf(Member(status = MemberStatus.ACTIVE))))
        
        val fee1 = repo.calculateDynamicJoiningFee(groupId).getOrThrow()
        assertEquals(100.0, fee1, 0.0) // No reserves, just base fee

        val groupWithBalance = baseGroup.copy(balance = 1000.0)
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(groupWithBalance)
        
        val fee2 = repo.calculateDynamicJoiningFee(groupId).getOrThrow()
        // reservesPerMember = 1000 / 1 = 1000
        // equityContribution = 1000 * 0.4 = 400
        // total = 100 + 400 = 500
        assertEquals(500.0, fee2, 0.0)
    }

    @Test
    fun `calculateViabilityPlan factors in group type`() = runBlocking {
        val groupId = "test-group"
        val group = Group(id = groupId, type = GroupType.INVESTMENT_CLUB, balance = 0.0)
        
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(listOf(Member(status = MemberStatus.ACTIVE))))
        
        val plan = repo.calculateViabilityPlan(groupId, 10000.0, 12).getOrThrow()
        
        assertTrue(plan.messages.any { it.contains("Investment Club") })
        assertTrue(plan.projectedValue >= 10000.0 || !plan.isViable)
    }
}
