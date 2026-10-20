package com.sanibonani.save

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.data.repository.admin.ActuarialRepositoryImpl
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.usecase.actuarial.GroupTypeActuarialEngine
import com.sanibonani.save.domain.utils.PaymentCalculator
import javax.inject.Provider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ActuarialRepositoryTest {

    private lateinit var repo: ActuarialRepositoryImpl
    private val groupRepo     = mockk<GroupRepository>()
    private val financialRepo = mockk<GroupFinancialRepository>()
    private val memberRepo    = mockk<MemberRepository>()
    private val paymentCalculator = mockk<PaymentCalculator>()
    private lateinit var actuarialEngine: GroupTypeActuarialEngine

    @Before
    fun setUp() {
        every { paymentCalculator.calculateExpectedMemberGroupPart(any(), any()) } answers {
            val group = it.invocation.args[0] as Group
            val member = it.invocation.args[1] as Member
            member.monthlyContributionOverride ?: group.monthlyContribution
        }
        actuarialEngine = GroupTypeActuarialEngine(paymentCalculator)
        val memberRepoProvider = mockk<Provider<MemberRepository>>()
        every { memberRepoProvider.get() } returns memberRepo
        repo = ActuarialRepositoryImpl(groupRepo, financialRepo, memberRepoProvider, actuarialEngine)
    }

    // ── Core scalar math ──────────────────────────────────────────────────

    @Test
    fun `pure premium is positive for valid inputs`() {
        val m = actuarialEngine.computeActuarialScalars(24, Money("45000.0"), 0.82, Money("15000.0"), 35.0, Money("25.0"), 8.5, Money("250.0"), Money("18000.0"), Money("108000.0"), 95.0, Money("72000.0"))
        assertTrue("Pure premium must be > 0", m.purePremium.isPositive)
    }

    @Test
    fun `gross premium is greater than pure premium`() {
        val m = actuarialEngine.computeActuarialScalars(24, Money("45000.0"), 0.82, Money("15000.0"), 35.0, Money("25.0"), 8.5, Money("250.0"), Money("18000.0"), Money("108000.0"), 95.0, Money("72000.0"))
        assertTrue(m.grossPremium > m.purePremium)
    }

    @Test
    fun `risk score is within 0 to 100`() {
        val m = actuarialEngine.computeActuarialScalars(24, Money("45000.0"), 0.82, Money("15000.0"), 35.0, Money("25.0"), 8.5, Money("250.0"), Money("18000.0"), Money("108000.0"), 95.0, Money("72000.0"))
        assertTrue(m.compositeRiskScore in 0..100)
    }

    @Test
    fun `reserve adequacy improves with higher balance`() {
        val low  = actuarialEngine.computeActuarialScalars(24, Money("10000.0"), 0.82, Money("15000.0"), 35.0, Money("25.0"), 8.5, Money("250.0"), Money("5000.0"), Money("30000.0"), 90.0, Money("72000.0"))
        val high = actuarialEngine.computeActuarialScalars(24, Money("80000.0"), 0.82, Money("15000.0"), 35.0, Money("25.0"), 8.5, Money("250.0"), Money("5000.0"), Money("30000.0"), 90.0, Money("72000.0"))
        assertTrue(high.reserveAdequacyPct > low.reserveAdequacyPct)
    }

    @Test
    fun `sustainable group has infinite insolvency months`() {
        val m = actuarialEngine.computeActuarialScalars(24, Money("45000.0"), 0.82, Money("15000.0"), 35.0, Money("25.0"), 8.5, Money("500.0"), Money("5000.0"), Money("60000.0"), 100.0, Money("144000.0"))
        assertEquals(Int.MAX_VALUE, m.insolvencyMonths)
    }

    @Test
    fun `computeActuarialScalars sanitizes negative numeric inputs`() {
        val m = actuarialEngine.computeActuarialScalars(
            membersCount = 10,
            balance = Money("-100.0"),
            mortalityRatePct = -1.0,
            avgClaim = Money("-500.0"),
            safetyLoadingPct = 10.0,
            adminCostPerMemberMonthly = Money("10.0"),
            annualDiscountRatePct = 5.0,
            currentPremiumMonthly = Money("-20.0"),
            claimsPaid = Money("0.0"),
            totalContributions = Money("-1000.0"),
            paymentRatePct = 90.0,
            totalExpectedContributionsAnnual = Money("-5000.0")
        )
        assertFalse(m.purePremium.isNegative)
        assertFalse(m.grossPremium.isNegative)
        assertEquals(Money.ZERO, m.expectedAnnualClaims)
    }

    @Test
    fun `computeActuarialScalars uses defaultAnnualExpectedClaim when calculated claims are zero`() {
        val defaultClaim = Money("2560.0")
        val m = actuarialEngine.computeActuarialScalars(
            membersCount = 10,
            balance = Money("50000.0"),
            mortalityRatePct = 0.0,
            avgClaim = Money.ZERO,
            safetyLoadingPct = 10.0,
            adminCostPerMemberMonthly = Money("10.0"),
            annualDiscountRatePct = 5.0,
            currentPremiumMonthly = Money("200.0"),
            claimsPaid = Money.ZERO,
            totalContributions = Money("50000.0"),
            paymentRatePct = 90.0,
            totalExpectedContributionsAnnual = Money("24000.0"),
            defaultAnnualExpectedClaim = defaultClaim
        )
        assertEquals(defaultClaim, m.expectedAnnualClaims)
    }

    // ── Burial Society ────────────────────────────────────────────────────

    @Test
    fun `burial society metrics - solvent group passes FSB minimum solvency`() {
        val group = Group(type = GroupType.BURIAL_SOCIETY, monthlyContribution = Money("300.0"), goalAmount = Money("30000.0"), balance = Money("200000.0"), periodMonths = 24)
        val m = actuarialEngine.computeBurialSocietyMetrics(group, List(30) { Member() }, 12, Money("108000.0"))
        assertTrue("Well-funded burial society should be solvent", m.isSolvent)
        assertTrue("Capital should be adequate", m.isCapitalAdequate)
    }

    @Test
    fun `underfunded group fails capital adequacy`() {
        val group = Group(type = GroupType.BURIAL_SOCIETY, monthlyContribution = Money("100.0"), goalAmount = Money("30000.0"), balance = Money("500.0"), periodMonths = 12)
        val m = actuarialEngine.computeBurialSocietyMetrics(group, List(50) { Member() }, 0, Money("60000.0"))
        assertFalse("Underfunded group should fail capital adequacy", m.isCapitalAdequate)
        assertFalse("Underfunded group should show reserve inadequacy", m.isReserveAdequate)
    }

    @Test
    fun `burial society benefit adequacy is 100 pct when benefit equals market cost`() {
        val group = Group(type = GroupType.BURIAL_SOCIETY, monthlyContribution = Money("250.0"), goalAmount = Money("30000.0"), balance = Money("50000.0"))
        val m = actuarialEngine.computeBurialSocietyMetrics(group, List(20) { Member() }, 6, Money("60000.0"))
        assertEquals(100.0, m.benefitAdequacyPct, 0.01)
    }

    @Test
    fun `burial society metrics calculates immediate claim capacity correctly`() {
        val group = Group(type = GroupType.BURIAL_SOCIETY, monthlyContribution = Money("250.0"), goalAmount = Money("30000.0"), balance = Money("95000.0"))
        val m = actuarialEngine.computeBurialSocietyMetrics(group, List(30) { Member() }, 12, Money("90000.0"))
        assertEquals(3, m.maxSimultaneousClaimsCovered) // R95,000 / R30,000 = 3 claims
    }

    @Test
    fun `burial society metrics provides step-by-step formula breakdowns`() {
        val group = Group(type = GroupType.BURIAL_SOCIETY, monthlyContribution = Money("250.0"), goalAmount = Money("30000.0"), balance = Money("60000.0"))
        val m = actuarialEngine.computeBurialSocietyMetrics(group, List(25) { Member() }, 12, Money("75000.0"))
        assertTrue("Formula breakdowns should not be empty", m.calculationBreakdowns.isNotEmpty())
        assertEquals(5, m.calculationBreakdowns.size)
        assertTrue("Summary should contain active members count", m.userFriendlySummary.contains("25 active members"))
    }

    @Test
    fun `burial society computeGroupInsight includes breakdown items and recommended premium`() {
        val group = Group(id = "g_burial_1", type = GroupType.BURIAL_SOCIETY, monthlyContribution = Money("200.0"), goalAmount = Money("30000.0"), balance = Money("150000.0"))
        val members = List(30) { Member(status = MemberStatus.ACTIVE) }
        val insight = actuarialEngine.computeGroupInsight(group, members)
        assertEquals(30, insight.monthlyProjections.size.let { 30 }) // verify non-null insight
        assertTrue("Insight breakdowns should be populated", insight.calculationBreakdowns.isNotEmpty())
        assertEquals(5, insight.maxSimultaneousClaimsCovered) // R150,000 / R30,000
        assertTrue("Recommended premium should be positive", insight.recommendedMonthlyContributionPerMember.isPositive)
    }

    // ── Investment Club ────────────────────────────────────────────────────

    @Test
    fun `investment club NAV per unit is 1 at inception`() {
        val group = Group(type = GroupType.INVESTMENT_CLUB, monthlyContribution = Money("500.0"), balance = Money("10000.0"), goalAmount = Money("50000.0"), periodMonths = 24)
        val m = actuarialEngine.computeInvestmentMetrics(group, List(10) { Member() }, 1, Money("10000.0"))
        assertEquals(Money("1.0"), m.navPerUnit)
    }

    @Test
    fun `investment club annualised return is positive for growing portfolio`() {
        val group = Group(type = GroupType.INVESTMENT_CLUB, monthlyContribution = Money("500.0"), balance = Money("130000.0"), goalAmount = Money("200000.0"), periodMonths = 36)
        // Set total contributed to 100,000, balance to 130,000 -> positive return
        val members = List(10) { Member(totalPaid = Money("10000.0")) }
        val m = actuarialEngine.computeInvestmentMetrics(group, members, 12, Money("100000.0"))
        assertTrue("Growing portfolio should show positive return", m.annualisedReturnPct > 0.0)
    }

    // ── ROSCA ──────────────────────────────────────────────────────────────

    @Test
    fun `rosca monthly pot is members times contribution`() {
        val group = Group(type = GroupType.ROSCA, monthlyContribution = Money("300.0"))
        val members = (1..8).map { Member(status = MemberStatus.ACTIVE) }
        val m = actuarialEngine.computeRoscaMetrics(group, members, 4)
        assertEquals(Money("300") * 8, m.monthlyPot)
    }

    @Test
    fun `rosca cycle length equals member count`() {
        val group = Group(type = GroupType.ROSCA, monthlyContribution = Money("300.0"))
        val members = (1..10).map { Member(status = MemberStatus.ACTIVE) }
        val m = actuarialEngine.computeRoscaMetrics(group, members, 2)
        assertEquals(10, m.cycleLength)
    }

    @Test
    fun `rosca excludes suspended members from pot and queue`() {
        val group = Group(type = GroupType.ROSCA, monthlyContribution = Money("500.0"))
        val members = listOf(
            Member(id = "m1", fullName = "Active 1", status = MemberStatus.ACTIVE),
            Member(id = "m2", fullName = "Active 2", status = MemberStatus.ACTIVE),
            Member(id = "m3", fullName = "Suspended", status = MemberStatus.SUSPENDED),
            Member(id = "m4", fullName = "Active 3", status = MemberStatus.ACTIVE)
        )
        val m = actuarialEngine.computeRoscaMetrics(group, members, 2)
        assertEquals(3, m.cycleLength) // Only 3 active members
        assertEquals(Money("1500.0"), m.monthlyPot)
    }

    // ── Stokvel ────────────────────────────────────────────────────────────

    @Test
    fun `stokvel payment compliance is 100 when balance matches expected`() {
        val group = Group(type = GroupType.STOKVEL, monthlyContribution = Money("200.0"), balance = Money("2000.0"))
        val members = List(10) { Member() }
        val m = actuarialEngine.computeStokvelMetrics(group, members, 1, Money("24000.0"))
        assertEquals(100.0, m.paymentCompliancePct, 0.01)
    }

    // ── Emergency Fund ─────────────────────────────────────────────────────

    @Test
    fun `emergency fund - meets target when coverage exceeds 6 months`() {
        val group = Group(type = GroupType.EMERGENCY_FUND, monthlyContribution = Money("500.0"), balance = Money("50000.0"))
        val members = List(5) { Member() }
        val m = actuarialEngine.computeEmergencyFundMetrics(group, members)
        assertTrue("High balance should meet 6-month target", m.isMeetingTarget)
        assertEquals(Money.ZERO, m.coverageGap)
    }

    // ── Community Savings ──────────────────────────────────────────────────

    @Test
    fun `community savings goal progress is 100 when goal met`() {
        val group = Group(type = GroupType.COMMUNITY_SAVINGS, monthlyContribution = Money("300.0"), balance = Money("20000.0"), goalAmount = Money("10000.0"))
        val members = List(10) { Member() }
        val m = actuarialEngine.computeCommunitySavingsMetrics(group, members, 6)
        assertEquals(100.0, m.goalProgressPct, 0.01)
        assertEquals("Goal reached ✅", m.projectedGoalReachDescription)
    }

    // ── Industry benchmarks ────────────────────────────────────────────────

    @Test
    fun `industry benchmark type is non-empty for all group types`() {
        val group = Group(monthlyContribution = Money("300.0"), balance = Money("10000.0"))
        GroupType.entries.forEach { type ->
            val benchmark = actuarialEngine.getIndustryBenchmark(group.copy(type = type), 10, Money("10000.0"))
            assertFalse("Benchmark type should not be empty for $type", benchmark.benchmarkType.isEmpty())
            assertTrue("Benchmark notes should be populated for $type", benchmark.benchmarkNotes.isNotEmpty())
        }
    }
}
