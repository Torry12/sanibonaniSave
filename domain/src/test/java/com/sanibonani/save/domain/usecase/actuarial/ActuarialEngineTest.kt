package com.sanibonani.save.domain.usecase.actuarial

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.utils.PaymentCalculator
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Characterization coverage for the group-type actuarial engine.
 *
 * These tests deliberately go through the engine facade and the typed detailed-metric
 * entry points so that the strategy/engine decomposition can be refactored without
 * changing a single reported number.
 */
class ActuarialEngineTest {

    private val paymentCalculator = mockk<PaymentCalculator>()
    private lateinit var engine: GroupTypeActuarialEngine

    @Before
    fun setUp() {
        every { paymentCalculator.calculateExpectedMemberGroupPart(any(), any()) } answers {
            val group = it.invocation.args[0] as Group
            val member = it.invocation.args[1] as Member
            member.monthlyContributionOverride ?: group.monthlyContribution
        }
        engine = GroupTypeActuarialEngine(paymentCalculator)
    }

    private fun groupOf(
        type: GroupType = GroupType.STOKVEL,
        balance: String = "60000.0",
        monthly: String = "300.0",
        goal: String = "30000.0"
    ) = Group(
        id = "g_$type",
        type = type,
        monthlyContribution = Money(monthly),
        goalAmount = Money(goal),
        balance = Money(balance),
        periodMonths = 24
    )

    private fun members(count: Int, paid: String = "0.0") =
        List(count) { Member(status = MemberStatus.ACTIVE, totalPaid = Money(paid)) }

    // ── Group statistics ──────────────────────────────────────────────────

    @Test
    fun `group stats count active and probation members only`() {
        val members = listOf(
            Member(id = "a", status = MemberStatus.ACTIVE),
            Member(id = "b", status = MemberStatus.PROBATION),
            Member(id = "c", status = MemberStatus.SUSPENDED)
        )

        val stats = engine.getGroupStats(Group(monthlyContribution = Money("100.0")), members)

        assertEquals(2, stats.activeMemberCount)
        assertEquals(listOf("a", "b"), stats.activeMembers.map { it.id })
    }

    @Test
    fun `group stats cap payment rate at 100 percent`() {
        val group = Group(monthlyContribution = Money("100.0"), balance = Money("100000.0"))

        val stats = engine.getGroupStats(group, members(2))

        assertEquals(100.0, stats.paymentRatePct, 0.0001)
    }

    @Test
    fun `group stats report undercollection proportionally`() {
        val group = Group(monthlyContribution = Money("100.0"), balance = Money("1200.0"))

        val stats = engine.getGroupStats(group, members(2))

        assertEquals(50.0, stats.paymentRatePct, 0.0001)
    }

    @Test
    fun `group stats fall back to twelve months when dates are absent`() {
        val stats = engine.getGroupStats(Group(monthlyContribution = Money("100.0")), members(2))

        assertEquals(12, stats.monthsActive)
    }

    // ── Facade ────────────────────────────────────────────────────────────

    @Test
    fun `facade produces metrics for every group type`() {
        GroupType.entries.forEach { type ->
            val metrics = engine.calculateMetrics(groupOf(type), members(10, paid = "3000.0"))

            assertTrue("$type score", metrics.compositeRiskScore in 0..100)
            assertNotNull("$type risk level", metrics.riskLevel)
            assertTrue("$type key metrics", metrics.keyMetrics.isNotEmpty())
        }
    }

    @Test
    fun `facade produces insight for every group type`() {
        GroupType.entries.forEach { type ->
            val group = groupOf(type)
            val insight = engine.computeGroupInsight(group, members(10, paid = "3000.0"))

            assertEquals("$type group id", group.id, insight.groupId)
            assertEquals(type, insight.groupType)
            assertEquals("$type projections", 12, insight.monthlyProjections.size)
            assertTrue("$type benchmark", insight.industryBenchmark.benchmarkType.isNotEmpty())
            assertTrue("$type notes", insight.industryBenchmark.benchmarkNotes.isNotEmpty())
            assertTrue("$type breakdowns", insight.calculationBreakdowns.isNotEmpty())
        }
    }

    @Test
    fun `industry benchmark is populated for every group type`() {
        val group = groupOf()

        GroupType.entries.forEach { type ->
            val benchmark = engine.getIndustryBenchmark(group.copy(type = type), 10, Money("10000.0"))

            assertFalse("Benchmark type should not be empty for $type", benchmark.benchmarkType.isEmpty())
            assertTrue("Benchmark notes should be populated for $type", benchmark.benchmarkNotes.isNotEmpty())
        }
    }

    // ── Burial society ────────────────────────────────────────────────────

    @Test
    fun `burial society detailed metrics flag a solvent group`() {
        val group = Group(
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = Money("300.0"),
            goalAmount = Money("30000.0"),
            balance = Money("200000.0"),
            periodMonths = 24
        )

        val metrics = engine.computeBurialSocietyMetrics(group, List(30) { Member() }, 12, Money("108000.0"))

        assertTrue("Well-funded burial society should be solvent", metrics.isSolvent)
        assertTrue("Capital should be adequate", metrics.isCapitalAdequate)
    }

    @Test
    fun `burial society benefit adequacy is 100 percent when benefit equals market cost`() {
        val group = Group(
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = Money("250.0"),
            goalAmount = Money("30000.0"),
            balance = Money("50000.0")
        )

        val metrics = engine.computeBurialSocietyMetrics(group, List(20) { Member() }, 6, Money("60000.0"))

        assertEquals(100.0, metrics.benefitAdequacyPct, 0.01)
    }

    @Test
    fun `burial society immediate claim capacity divides balance by benefit`() {
        val group = Group(
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = Money("250.0"),
            goalAmount = Money("30000.0"),
            balance = Money("95000.0")
        )

        val metrics = engine.computeBurialSocietyMetrics(group, List(30) { Member() }, 12, Money("90000.0"))

        assertEquals(3, metrics.maxSimultaneousClaimsCovered)
    }

    @Test
    fun `burial society metrics expose five formula breakdowns`() {
        val group = Group(
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = Money("250.0"),
            goalAmount = Money("30000.0"),
            balance = Money("60000.0")
        )

        val metrics = engine.computeBurialSocietyMetrics(group, List(25) { Member() }, 12, Money("75000.0"))

        assertEquals(5, metrics.calculationBreakdowns.size)
        assertTrue(metrics.userFriendlySummary.contains("25 active members"))
    }

    // ── Investment club ───────────────────────────────────────────────────

    @Test
    fun `investment club nav per unit is one at inception`() {
        val group = Group(
            type = GroupType.INVESTMENT_CLUB,
            monthlyContribution = Money("500.0"),
            balance = Money("10000.0"),
            goalAmount = Money("50000.0"),
            periodMonths = 24
        )

        val metrics = engine.computeInvestmentMetrics(group, List(10) { Member() }, 1, Money("10000.0"))

        assertEquals(Money("1.0"), metrics.navPerUnit)
    }

    @Test
    fun `investment club annualised return is positive for a growing portfolio`() {
        val group = Group(
            type = GroupType.INVESTMENT_CLUB,
            monthlyContribution = Money("500.0"),
            balance = Money("130000.0"),
            goalAmount = Money("200000.0"),
            periodMonths = 36
        )
        val members = List(10) { Member(totalPaid = Money("10000.0")) }

        val metrics = engine.computeInvestmentMetrics(group, members, 12, Money("100000.0"))

        assertTrue(metrics.annualisedReturnPct > 0.0)
    }

    // ── ROSCA ─────────────────────────────────────────────────────────────

    @Test
    fun `rosca monthly pot is contribution times active members`() {
        val group = Group(type = GroupType.ROSCA, monthlyContribution = Money("300.0"))

        val metrics = engine.computeRoscaMetrics(group, members(8), 4)

        assertEquals(Money("300") * 8, metrics.monthlyPot)
    }

    @Test
    fun `rosca cycle length ignores suspended members`() {
        val group = Group(type = GroupType.ROSCA, monthlyContribution = Money("500.0"))
        val members = listOf(
            Member(id = "m1", status = MemberStatus.ACTIVE),
            Member(id = "m2", status = MemberStatus.ACTIVE),
            Member(id = "m3", status = MemberStatus.SUSPENDED),
            Member(id = "m4", status = MemberStatus.ACTIVE)
        )

        val metrics = engine.computeRoscaMetrics(group, members, 2)

        assertEquals(3, metrics.cycleLength)
        assertEquals(Money("1500.0"), metrics.monthlyPot)
    }

    // ── Stokvel ───────────────────────────────────────────────────────────

    @Test
    fun `stokvel compliance is 100 percent when balance matches expected`() {
        val group = Group(type = GroupType.STOKVEL, monthlyContribution = Money("200.0"), balance = Money("2000.0"))

        val metrics = engine.computeStokvelMetrics(group, List(10) { Member() }, 1, Money("24000.0"))

        assertEquals(100.0, metrics.paymentCompliancePct, 0.01)
    }

    // ── Emergency fund ────────────────────────────────────────────────────

    @Test
    fun `emergency fund meets target above six months coverage`() {
        val group = Group(type = GroupType.EMERGENCY_FUND, monthlyContribution = Money("500.0"), balance = Money("50000.0"))

        val metrics = engine.computeEmergencyFundMetrics(group, List(5) { Member() })

        assertTrue(metrics.isMeetingTarget)
        assertEquals(Money.ZERO, metrics.coverageGap)
    }

    // ── Community savings ─────────────────────────────────────────────────

    @Test
    fun `community savings reports a reached goal`() {
        val group = Group(
            type = GroupType.COMMUNITY_SAVINGS,
            monthlyContribution = Money("300.0"),
            balance = Money("20000.0"),
            goalAmount = Money("10000.0")
        )

        val metrics = engine.computeCommunitySavingsMetrics(group, List(10) { Member() }, 6)

        assertEquals(100.0, metrics.goalProgressPct, 0.01)
        assertEquals("Goal reached ✅", metrics.projectedGoalReachDescription)
    }

    // ── Cash-flow projections ─────────────────────────────────────────────

    @Test
    fun `cash flow projections cover twelve non-negative months`() {
        GroupType.entries.forEach { type ->
            val projections = engine.computeCashFlowProjections(
                group = groupOf(type),
                memberCount = 10,
                currentBalance = Money("60000.0"),
                paymentCompliancePct = 100.0
            )

            assertEquals("$type month count", 12, projections.size)
            projections.forEach { month ->
                assertFalse("$type month ${month.month}", month.projectedBalance.isNegative)
                assertEquals(month.inflow - month.outflow, month.netFlow)
            }
        }
    }

    @Test
    fun `rosca projections redistribute the pot without changing the balance`() {
        val projections = engine.computeCashFlowProjections(
            group = groupOf(GroupType.ROSCA),
            memberCount = 10,
            currentBalance = Money("60000.0"),
            paymentCompliancePct = 100.0
        )

        projections.forEach { month ->
            assertEquals(Money.ZERO, month.netFlow)
            assertEquals(Money("60000.0"), month.projectedBalance)
        }
    }
}
