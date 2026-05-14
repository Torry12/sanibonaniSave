package com.sanibonani.save

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.data.repository.ActuarialRepositoryImpl
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.usecase.actuarial.GroupTypeActuarialEngine
import javax.inject.Provider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class ActuarialRepositoryTest {

    private lateinit var repo: ActuarialRepositoryImpl
    private val groupRepo   = mockk<GroupRepository>()
    private val memberRepo  = mockk<MemberRepository>()

    @Before
    fun setUp() {
        val memberRepoProvider = mockk<Provider<MemberRepository>>()
        every { memberRepoProvider.get() } returns memberRepo
        repo = ActuarialRepositoryImpl(groupRepo, memberRepoProvider)
    }

    // ── Core scalar math (existing) ───────────────────────────────────────

    @Test
    fun `pure premium is positive for valid inputs`() {
        val m = repo.computeActuarialScalars(24, 45000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 250.0, 18000.0, 108000.0, 95.0, 72000.0)
        assertTrue("Pure premium must be > 0", m.purePremium > 0)
    }

    @Test
    fun `gross premium is greater than pure premium`() {
        val m = repo.computeActuarialScalars(24, 45000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 250.0, 18000.0, 108000.0, 95.0, 72000.0)
        assertTrue(m.grossPremium > m.purePremium)
    }

    @Test
    fun `risk score is within 0 to 100`() {
        val m = repo.computeActuarialScalars(24, 45000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 250.0, 18000.0, 108000.0, 95.0, 72000.0)
        assertTrue(m.compositeRiskScore in 0..100)
    }

    @Test
    fun `reserve adequacy improves with higher balance`() {
        val low  = repo.computeActuarialScalars(24, 10000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 250.0, 5000.0, 30000.0, 90.0, 72000.0)
        val high = repo.computeActuarialScalars(24, 80000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 250.0, 5000.0, 30000.0, 90.0, 72000.0)
        assertTrue(high.reserveAdequacyPct > low.reserveAdequacyPct)
    }

    @Test
    fun `APV is less than undiscounted expected claims`() {
        val m = repo.computeActuarialScalars(24, 45000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 250.0, 18000.0, 108000.0, 95.0, 72000.0)
        assertTrue("APV < undiscounted claims (time value)", m.actuarialPresentValue < m.expectedAnnualClaims)
    }

    @Test
    fun `sustainable group has infinite insolvency months`() {
        val m = repo.computeActuarialScalars(24, 45000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 500.0, 5000.0, 60000.0, 100.0, 144000.0)
        assertEquals(Int.MAX_VALUE, m.insolvencyMonths)
    }

    @Test
    fun `loss ratio is zero when no claims paid`() {
        val m = repo.computeActuarialScalars(24, 45000.0, 0.82, 15000.0, 35.0, 25.0, 8.5, 250.0, 0.0, 108000.0, 95.0, 72000.0)
        assertEquals(0.0, m.lossRatioPct, 0.001)
    }

    @Test
    fun `property based - actuarial scalar outputs remain within safe bounds`() {
        val random = Random(42)

        repeat(200) {
            val members = random.nextInt(1, 300)
            val balance = random.nextDouble(0.0, 2_000_000.0)
            val mortality = random.nextDouble(0.0, 10.0)
            val avgClaim = random.nextDouble(500.0, 80_000.0)
            val safety = random.nextDouble(0.0, 60.0)
            val adminCost = random.nextDouble(0.0, 250.0)
            val discount = random.nextDouble(0.0, 20.0)
            val premium = random.nextDouble(0.0, 5_000.0)
            val claimsPaid = random.nextDouble(0.0, 1_000_000.0)
            val contributions = random.nextDouble(1.0, 3_000_000.0)
            val paymentRate = random.nextDouble(0.0, 100.0)
            val expectedAnnual = random.nextDouble(0.0, 3_000_000.0)

            val m = repo.computeActuarialScalars(
                members,
                balance,
                mortality,
                avgClaim,
                safety,
                adminCost,
                discount,
                premium,
                claimsPaid,
                contributions,
                paymentRate,
                expectedAnnual
            )

            assertTrue("purePremium should never be negative", m.purePremium >= 0.0)
            assertTrue("grossPremium should not be below purePremium", m.grossPremium >= m.purePremium)
            assertTrue("risk score bounded", m.compositeRiskScore in 0..100)
            assertTrue("paymentRate bounded", m.paymentRatePct in 0.0..100.0)
            assertTrue("expected annual claims should never be negative", m.expectedAnnualClaims >= 0.0)
            assertTrue("insolvency months must be non-negative", m.insolvencyMonths >= 0)
        }
    }

    @Test
    fun `property based - reserve adequacy is monotonic with balance`() {
        val random = Random(7)

        repeat(120) {
            val members = random.nextInt(5, 80)
            val lowBalance = random.nextDouble(0.0, 100_000.0)
            val highBalance = lowBalance + random.nextDouble(1_000.0, 500_000.0)

            val params = listOf(
                random.nextDouble(0.3, 2.0),      // mortality
                random.nextDouble(5_000.0, 40_000.0),
                35.0,
                25.0,
                8.5,
                random.nextDouble(50.0, 1_000.0),
                random.nextDouble(0.0, 100_000.0),
                random.nextDouble(1_000.0, 500_000.0),
                random.nextDouble(50.0, 100.0),
                random.nextDouble(30_000.0, 600_000.0)
            )

            val low = repo.computeActuarialScalars(
                members,
                lowBalance,
                params[0],
                params[1],
                params[2],
                params[3],
                params[4],
                params[5],
                params[6],
                params[7],
                params[8],
                params[9]
            )

            val high = repo.computeActuarialScalars(
                members,
                highBalance,
                params[0],
                params[1],
                params[2],
                params[3],
                params[4],
                params[5],
                params[6],
                params[7],
                params[8],
                params[9]
            )

            assertTrue(
                "reserve adequacy should not decrease when only balance increases",
                high.reserveAdequacyPct >= low.reserveAdequacyPct
            )
        }
    }

    @Test
    fun `computeActuarialScalars sanitizes negative numeric inputs`() {
        val m = repo.computeActuarialScalars(
            membersCount = -10,
            balance = -1000.0,
            mortalityRatePct = -2.0,
            avgClaim = -500.0,
            safetyLoadingPct = -10.0,
            adminCostPerMember = -5.0,
            annualDiscountRatePct = -3.0,
            currentPremium = -100.0,
            claimsPaid = -50.0,
            totalContributions = -100.0,
            paymentRatePct = -20.0,
            totalExpectedContributionsAnnual = -1200.0
        )

        assertEquals(0.0, m.purePremium, 0.001)
        assertEquals(0.0, m.grossPremium, 0.001)
        assertEquals(0.0, m.expectedAnnualClaims, 0.001)
        assertTrue(m.compositeRiskScore in 0..100)
        assertEquals(0.0, m.paymentRatePct, 0.001)
    }

    @Test
    fun `break even members is max value when premium cannot cover risk and loadings`() {
        val m = repo.computeActuarialScalars(
            membersCount = 20,
            balance = 10000.0,
            mortalityRatePct = 1.0,
            avgClaim = 10000.0,
            safetyLoadingPct = 35.0,
            adminCostPerMember = 25.0,
            annualDiscountRatePct = 8.0,
            currentPremium = 10.0,
            claimsPaid = 1000.0,
            totalContributions = 5000.0,
            paymentRatePct = 90.0,
            totalExpectedContributionsAnnual = 2400.0
        )

        assertEquals(Int.MAX_VALUE, m.breakEvenMembers)
    }

    // ── Member contribution helpers ───────────────────────────────────────

    @Test
    fun `calculateMemberContribution for non-burial society returns group contribution`() {
        val group = Group(type = GroupType.STOKVEL, monthlyContribution = 500.0)
        assertEquals(500.0, repo.calculateMemberContribution(group, Member()), 0.0)
    }

    @Test
    fun `calculateMemberContribution for burial society with beneficiaries over 65`() {
        val group = Group(type = GroupType.BURIAL_SOCIETY, monthlyContribution = 200.0, beneficiaryIncreasePct = 10.0)
        val member = Member(beneficiaryOver65Count = 2)
        // 200 + (200 × 0.1 × 2) = 240
        assertEquals(240.0, repo.calculateMemberContribution(group, member), 0.0)
    }

    @Test
    fun `calculateMemberContribution uses override if present`() {
        val group = Group(type = GroupType.BURIAL_SOCIETY, monthlyContribution = 200.0)
        val member = Member(monthlyContributionOverride = 350.0)
        assertEquals(350.0, repo.calculateMemberContribution(group, member), 0.0)
    }

    // ── Dynamic joining fee ───────────────────────────────────────────────

    @Test
    fun `calculateDynamicJoiningFee increases with reserves`() = runBlocking {
        val groupId = "test-group"
        val baseGroup = Group(id = groupId, joiningFee = 100.0, balance = 0.0)
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(baseGroup)
        coEvery { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(listOf(Member(status = MemberStatus.ACTIVE))))

        val fee1 = repo.calculateDynamicJoiningFee(groupId).getOrThrow()
        assertEquals(100.0, fee1, 0.0)

        val groupWithBalance = baseGroup.copy(balance = 1000.0)
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(groupWithBalance)
        val fee2 = repo.calculateDynamicJoiningFee(groupId).getOrThrow()
        // reservesPerMember=1000; equity=400; total=500
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
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Extended ActuarialMetrics fields
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `riskLevel defaults to moderate for mid-range score`() {
        assertEquals(RiskLevel.LOW,      GroupTypeActuarialEngine.classifyRiskLevel(85))
        assertEquals(RiskLevel.MODERATE, GroupTypeActuarialEngine.classifyRiskLevel(65))
        assertEquals(RiskLevel.HIGH,     GroupTypeActuarialEngine.classifyRiskLevel(40))
        assertEquals(RiskLevel.CRITICAL, GroupTypeActuarialEngine.classifyRiskLevel(15))
    }

    @Test
    fun `calculateMetrics populates extended cash flow fields for burial society`() {
        val group = Group(
            id = "g1", type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = 250.0, goalAmount = 30000.0, balance = 50000.0
        )
        val members = (1..20).map { Member(status = MemberStatus.ACTIVE) }
        val m = repo.calculateMetrics(group, members)
        assertTrue("projectedBalanceM3 should be set", m.projectedBalanceM3 >= 0.0)
        assertTrue("projectedBalanceM6 should be set", m.projectedBalanceM6 >= 0.0)
        assertTrue("projectedBalanceM12 should be set", m.projectedBalanceM12 >= 0.0)
        assertTrue("cashFlowRiskScore 0-100", m.cashFlowRiskScore in 0..100)
        assertNotNull("riskLevel populated", m.riskLevel)
    }

    @Test
    fun `calculateMetrics populates typeSpecificWarnings`() {
        // Under-funded burial society should produce warnings
        val group = Group(
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = 50.0, goalAmount = 30000.0, balance = 100.0
        )
        val members = (1..30).map { Member(status = MemberStatus.ACTIVE) }
        val m = repo.calculateMetrics(group, members)
        assertTrue("Should have type-specific warnings", m.typeSpecificWarnings.isNotEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ViabilityPlan extended fields
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `viabilityPlan optimistic is greater than pessimistic`() = runBlocking {
        val groupId = "g2"
        val group = Group(id = groupId, type = GroupType.STOKVEL, monthlyContribution = 300.0, balance = 0.0)
        val members = (1..10).map { Member(status = MemberStatus.ACTIVE) }
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(members))

        val plan = repo.calculateViabilityPlan(groupId, 36000.0, 12).getOrThrow()
        assertTrue("Optimistic > pessimistic", plan.optimisticProjectedValue > plan.pessimisticProjectedValue)
    }

    @Test
    fun `viabilityPlan shortfallAmount is zero when viable`() = runBlocking {
        val groupId = "g3"
        val group = Group(id = groupId, type = GroupType.STOKVEL, monthlyContribution = 1000.0, balance = 0.0, goalAmount = 5000.0, periodMonths = 12)
        val members = (1..12).map { Member(status = MemberStatus.ACTIVE) }
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(members))

        val plan = repo.calculateViabilityPlan(groupId, 5000.0, 12).getOrThrow()
        assertEquals(0.0, plan.shortfallAmount, 0.01)
    }

    @Test
    fun `viabilityPlan includes ROSCA-specific risk factors`() = runBlocking {
        val groupId = "g4"
        val group = Group(id = groupId, type = GroupType.ROSCA, monthlyContribution = 300.0, balance = 0.0)
        val members = (1..8).map { Member(status = MemberStatus.ACTIVE) }
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(members))

        val plan = repo.calculateViabilityPlan(groupId, 12000.0, 12).getOrThrow()
        assertTrue(plan.messages.any { it.contains("ROSCA factors") })
        assertTrue(plan.messages.any { it.contains("default_risk") })
        assertTrue("ROSCA default risk factor should be > 1", plan.defaultRiskFactor > 1.0)
        assertTrue("ROSCA cycle slippage factor should be >= 1", plan.cycleSlippageFactor >= 1.0)
    }

    @Test
    fun `rosca viability increases risk factors when active ratio drops`() = runBlocking {
        val groupId = "g_rosca_ratio"
        val group = Group(id = groupId, type = GroupType.ROSCA, monthlyContribution = 300.0, balance = 0.0)
        val mostlyActive = List(10) { idx ->
            if (idx < 9) Member(status = MemberStatus.ACTIVE) else Member(status = MemberStatus.SUSPENDED)
        }
        val mixedActive = List(10) { idx ->
            if (idx < 6) Member(status = MemberStatus.ACTIVE) else Member(status = MemberStatus.SUSPENDED)
        }

        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepo.getGroupMembers(groupId) } returnsMany listOf(
            flowOf(Result.success(mostlyActive)),
            flowOf(Result.success(mixedActive))
        )

        val highParticipationPlan = repo.calculateViabilityPlan(groupId, 12000.0, 12).getOrThrow()
        val lowParticipationPlan = repo.calculateViabilityPlan(groupId, 12000.0, 12).getOrThrow()

        assertTrue(lowParticipationPlan.defaultRiskFactor > highParticipationPlan.defaultRiskFactor)
        assertTrue(lowParticipationPlan.cycleSlippageFactor >= highParticipationPlan.cycleSlippageFactor)
        assertTrue(lowParticipationPlan.projectionRetentionFactor <= highParticipationPlan.projectionRetentionFactor)
    }

    @Test
    fun `rosca viability default risk grows for very long cycles`() = runBlocking {
        val groupId = "g_rosca_cycle"
        val group = Group(id = groupId, type = GroupType.ROSCA, monthlyContribution = 250.0, balance = 0.0)
        val shortCycleMembers = List(10) { Member(status = MemberStatus.ACTIVE) }
        val longCycleMembers = List(18) { Member(status = MemberStatus.ACTIVE) }

        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepo.getGroupMembers(groupId) } returnsMany listOf(
            flowOf(Result.success(shortCycleMembers)),
            flowOf(Result.success(longCycleMembers))
        )

        val shortCyclePlan = repo.calculateViabilityPlan(groupId, 18000.0, 12).getOrThrow()
        val longCyclePlan = repo.calculateViabilityPlan(groupId, 18000.0, 12).getOrThrow()

        assertTrue(longCyclePlan.defaultRiskFactor > shortCyclePlan.defaultRiskFactor)
    }

    @Test
    fun `viabilityPlan includes burial-specific reserve factors`() = runBlocking {
        val groupId = "g5"
        val group = Group(id = groupId, type = GroupType.BURIAL_SOCIETY, monthlyContribution = 250.0, balance = 500.0)
        val members = (1..10).map { Member(status = MemberStatus.ACTIVE) }
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(members))

        val plan = repo.calculateViabilityPlan(groupId, 50000.0, 24).getOrThrow()
        assertTrue(plan.messages.any { it.contains("Burial Society factors") })
        assertTrue(plan.messages.any { it.contains("reserve_adequacy") })
        assertEquals(1.25, plan.claimReadinessFactor, 0.001)
        assertTrue("Mortality buffer should be additive > 1", plan.mortalityBufferFactor > 1.0)
        assertTrue("Projection retention should be below 1 for burial", plan.projectionRetentionFactor < 1.0)
    }

    @Test
    fun `viabilityPlan captures active member ratio for diagnostics`() = runBlocking {
        val groupId = "g6"
        val group = Group(id = groupId, type = GroupType.STOKVEL, monthlyContribution = 200.0, balance = 0.0)
        val members = listOf(
            Member(status = MemberStatus.ACTIVE),
            Member(status = MemberStatus.ACTIVE),
            Member(status = MemberStatus.SUSPENDED),
            Member(status = MemberStatus.SUSPENDED)
        )
        coEvery { groupRepo.getGroupById(groupId) } returns Result.success(group)
        coEvery { memberRepo.getGroupMembers(groupId) } returns flowOf(Result.success(members))

        val plan = repo.calculateViabilityPlan(groupId, 9600.0, 12).getOrThrow()

        assertEquals(0.5, plan.activeMemberRatio, 0.001)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GroupTypeActuarialEngine unit tests
    // ══════════════════════════════════════════════════════════════════════

    // ── Burial Society ────────────────────────────────────────────────────

    @Test
    fun `burial society metrics - solvent group passes FSB minimum solvency`() {
        val group = Group(type = GroupType.BURIAL_SOCIETY, monthlyContribution = 300.0, goalAmount = 30000.0, balance = 200000.0, periodMonths = 24)
        val m = GroupTypeActuarialEngine.computeBurialSocietyMetrics(group, 30, 200000.0, 108000.0)
        assertTrue("Well-funded burial society should be solvent", m.isSolvent)
        assertTrue("Capital should be adequate", m.isCapitalAdequate)
    }

    @Test
    fun `burial society metrics - underfunded group fails capital adequacy`() {
        val group = Group(type = GroupType.BURIAL_SOCIETY, monthlyContribution = 100.0, goalAmount = 30000.0, balance = 500.0, periodMonths = 12)
        val m = GroupTypeActuarialEngine.computeBurialSocietyMetrics(group, 50, 500.0, 60000.0)
        assertFalse("Underfunded group should fail capital adequacy", m.isCapitalAdequate)
        assertFalse("Underfunded group should show reserve inadequacy", m.isReserveAdequate)
        assertTrue("Should produce warnings", m.warnings.isNotEmpty())
    }

    @Test
    fun `burial society benefit adequacy is 100 pct when benefit equals market cost`() {
        val group = Group(type = GroupType.BURIAL_SOCIETY, monthlyContribution = 250.0, goalAmount = 30000.0, balance = 50000.0)
        val m = GroupTypeActuarialEngine.computeBurialSocietyMetrics(group, 20, 50000.0, 60000.0)
        assertEquals(100.0, m.benefitAdequacyPct, 0.01)
    }

    @Test
    fun `burial society insolvency returns -1 for profitable group`() {
        val group = Group(type = GroupType.BURIAL_SOCIETY, monthlyContribution = 500.0, goalAmount = 15000.0, balance = 200000.0, periodMonths = 12)
        val m = GroupTypeActuarialEngine.computeBurialSocietyMetrics(group, 20, 200000.0, 120000.0)
        assertEquals(-1.0, m.yearsToInsolvencyAtCurrentRate, 0.01)
    }

    // ── Investment Club ────────────────────────────────────────────────────

    @Test
    fun `investment club NAV per unit is 1 at inception`() {
        val group = Group(type = GroupType.INVESTMENT_CLUB, monthlyContribution = 500.0, balance = 10000.0, goalAmount = 50000.0, periodMonths = 24)
        val m = GroupTypeActuarialEngine.computeInvestmentMetrics(group, 10, 10000.0, 10000.0, 1)
        assertEquals(1.0, m.navPerUnit, 0.01)
    }

    @Test
    fun `investment club annualised return is positive for growing portfolio`() {
        val group = Group(type = GroupType.INVESTMENT_CLUB, monthlyContribution = 500.0, balance = 130000.0, goalAmount = 200000.0, periodMonths = 36)
        val m = GroupTypeActuarialEngine.computeInvestmentMetrics(group, 10, 130000.0, 100000.0, 24)
        assertTrue("Growing portfolio should show positive return", m.annualisedReturnPct > 0.0)
    }

    @Test
    fun `investment club capital at risk is 30 pct of per-member NAV`() {
        val group = Group(type = GroupType.INVESTMENT_CLUB, monthlyContribution = 500.0, balance = 60000.0, goalAmount = 100000.0, periodMonths = 24)
        val m = GroupTypeActuarialEngine.computeInvestmentMetrics(group, 10, 60000.0, 50000.0, 12)
        assertEquals(60000.0 / 10 * 0.30, m.capitalAtRiskPerMember, 0.01)
    }

    // ── ROSCA ──────────────────────────────────────────────────────────────

    @Test
    fun `rosca monthly pot is members times contribution`() {
        val group = Group(type = GroupType.ROSCA, monthlyContribution = 300.0)
        val members = (1..8).map { Member(status = MemberStatus.ACTIVE) }
        val m = GroupTypeActuarialEngine.computeRoscaMetrics(group, members, 4)
        assertEquals(300.0 * 8, m.monthlyPot, 0.01)
    }

    @Test
    fun `rosca cycle length equals member count`() {
        val group = Group(type = GroupType.ROSCA, monthlyContribution = 300.0)
        val members = (1..10).map { Member(status = MemberStatus.ACTIVE) }
        val m = GroupTypeActuarialEngine.computeRoscaMetrics(group, members, 2)
        assertEquals(10, m.cycleLength)
    }

    @Test
    fun `rosca cycle completion probability is below 100`() {
        val group = Group(type = GroupType.ROSCA, monthlyContribution = 300.0)
        val members = (1..20).map { Member(status = MemberStatus.ACTIVE) }
        val m = GroupTypeActuarialEngine.computeRoscaMetrics(group, members, 5)
        assertTrue("Completion probability must be < 100", m.cycleCompletionProbability < 100.0)
        assertTrue("Completion probability must be > 0", m.cycleCompletionProbability > 0.0)
    }

    @Test
    fun `small rosca generates group size warning`() {
        val group = Group(type = GroupType.ROSCA, monthlyContribution = 300.0)
        val members = (1..3).map { Member(status = MemberStatus.ACTIVE) }
        val m = GroupTypeActuarialEngine.computeRoscaMetrics(group, members, 1)
        assertTrue("Small ROSCA should warn", m.warnings.any { it.contains("small") || it.contains("3") })
    }

    @Test
    fun `rosca cycle month is month 1 when first month is active`() {
        val group = Group(type = GroupType.ROSCA, monthlyContribution = 300.0)
        val members = (1..8).map { Member(status = MemberStatus.ACTIVE) }
        val m = GroupTypeActuarialEngine.computeRoscaMetrics(group, members, 1)
        assertEquals(1, m.currentCycleMonth)
    }

    @Test
    fun `rosca excludes suspended members from pot and queue`() {
        val group = Group(type = GroupType.ROSCA, monthlyContribution = 250.0)
        val members = listOf(
            Member(fullName = "A", status = MemberStatus.ACTIVE),
            Member(fullName = "B", status = MemberStatus.ACTIVE),
            Member(fullName = "C", status = MemberStatus.SUSPENDED),
            Member(fullName = "D", status = MemberStatus.PROBATION)
        )

        val m = GroupTypeActuarialEngine.computeRoscaMetrics(group, members, 2)

        assertEquals(3, m.cycleLength)
        assertEquals(750.0, m.monthlyPot, 0.01)
        assertTrue(m.warnings.any { it.contains("excluded") })
    }

    // ── Stokvel ────────────────────────────────────────────────────────────

    @Test
    fun `stokvel projected payout increases with more months remaining`() {
        val group = Group(type = GroupType.STOKVEL, monthlyContribution = 200.0, balance = 5000.0)
        // Early in year vs late in year
        val earlyYear = GroupTypeActuarialEngine.computeStokvelMetrics(group, 10, 5000.0, 24000.0, 2)
        val lateYear  = GroupTypeActuarialEngine.computeStokvelMetrics(group, 10, 5000.0, 24000.0, 10)
        assertTrue("Early-year has more months to compound -> higher projection",
            earlyYear.totalProjectedFund >= lateYear.totalProjectedFund)
    }

    @Test
    fun `stokvel payment compliance is 100 when balance matches expected`() {
        val group = Group(type = GroupType.STOKVEL, monthlyContribution = 200.0, balance = 2000.0)
        val m = GroupTypeActuarialEngine.computeStokvelMetrics(group, 10, 2000.0, 24000.0, 1)
        // expected by now = 200 * 10 * 1 = 2000, balance = 2000
        assertEquals(100.0, m.paymentCompliancePct, 0.01)
    }

    @Test
    fun `stokvel savings efficiency score is within 0 to 100`() {
        val group = Group(type = GroupType.STOKVEL, monthlyContribution = 200.0, balance = 3000.0)
        val m = GroupTypeActuarialEngine.computeStokvelMetrics(group, 10, 3000.0, 24000.0, 5)
        assertTrue(m.savingsEfficiencyScore in 0..100)
    }

    // ── Emergency Fund ─────────────────────────────────────────────────────

    @Test
    fun `emergency fund - meets target when coverage exceeds 6 months`() {
        val group = Group(type = GroupType.EMERGENCY_FUND, monthlyContribution = 500.0, balance = 50000.0)
        val m = GroupTypeActuarialEngine.computeEmergencyFundMetrics(group, 5, 50000.0)
        // monthly expenses proxy = 500 * 5 * 1.5 = 3750; 6-month target = 22500; balance > target
        assertTrue("High balance should meet 6-month target", m.isMeetingTarget)
        assertEquals(0.0, m.coverageGap, 0.01)
    }

    @Test
    fun `emergency fund - coverage gap is positive when underfunded`() {
        val group = Group(type = GroupType.EMERGENCY_FUND, monthlyContribution = 500.0, balance = 1000.0)
        val m = GroupTypeActuarialEngine.computeEmergencyFundMetrics(group, 10, 1000.0)
        assertTrue("Underfunded: coverage gap should be positive", m.coverageGap > 0.0)
        assertFalse("Should not be meeting target", m.isMeetingTarget)
        assertTrue("monthsToTarget should be > 0", m.monthsToTarget > 0)
    }

    // ── Tontine ────────────────────────────────────────────────────────────

    @Test
    fun `tontine projected share per survivor exceeds simple average`() {
        val group = Group(type = GroupType.TONTINE, monthlyContribution = 400.0, balance = 30000.0, periodMonths = 60)
        val m = GroupTypeActuarialEngine.computeTontineMetrics(group, 20, 30000.0, 12)
        // Survivor benefit > simple per-head share because not all survive
        assertTrue("Projected share per survivor >= current share", m.projectedShareAtEnd >= m.currentSharePerMember)
    }

    @Test
    fun `small tontine generates member count warning`() {
        val group = Group(type = GroupType.TONTINE, monthlyContribution = 400.0, balance = 5000.0, periodMonths = 36)
        val m = GroupTypeActuarialEngine.computeTontineMetrics(group, 5, 5000.0, 6)
        assertTrue("Small tontine should warn about member count", m.warnings.isNotEmpty())
    }

    // ── Community Savings ──────────────────────────────────────────────────

    @Test
    fun `community savings goal progress is 100 when goal met`() {
        val group = Group(type = GroupType.COMMUNITY_SAVINGS, monthlyContribution = 300.0, balance = 20000.0, goalAmount = 10000.0)
        val m = GroupTypeActuarialEngine.computeCommunitySavingsMetrics(group, 10, 20000.0, 6)
        assertEquals(100.0, m.goalProgressPct, 0.01)
        assertEquals("Goal reached ✅", m.projectedGoalReachDescription)
    }

    @Test
    fun `community savings annual dividend is 6 pct of balance`() {
        val group = Group(type = GroupType.COMMUNITY_SAVINGS, monthlyContribution = 300.0, balance = 10000.0, goalAmount = 50000.0)
        val m = GroupTypeActuarialEngine.computeCommunitySavingsMetrics(group, 5, 10000.0, 4)
        assertEquals(10000.0 * 0.06, m.annualDividendProjection, 0.01)
    }

    // ── Industry benchmarks ────────────────────────────────────────────────

    @Test
    fun `industry benchmark type is non-empty for all group types`() {
        val group = Group(monthlyContribution = 300.0, balance = 10000.0)
        GroupType.entries.forEach { type ->
            val g = group.copy(type = type)
            val bm = GroupTypeActuarialEngine.getIndustryBenchmark(g, 10, 10000.0)
            assertTrue("Benchmark type should not be empty for $type", bm.benchmarkType.isNotEmpty())
        }
    }

    // ── Cash flow projections ──────────────────────────────────────────────

    @Test
    fun `cash flow projections returns 12 months`() {
        val group = Group(type = GroupType.STOKVEL, monthlyContribution = 300.0, balance = 5000.0)
        val proj = GroupTypeActuarialEngine.computeCashFlowProjections(group, 10, 5000.0, 95.0)
        assertEquals(12, proj.size)
    }

    @Test
    fun `cash flow projections all months are positive for healthy stokvel`() {
        val group = Group(type = GroupType.STOKVEL, monthlyContribution = 500.0, balance = 20000.0)
        val proj = GroupTypeActuarialEngine.computeCashFlowProjections(group, 20, 20000.0, 100.0)
        assertTrue("All projections non-negative", proj.all { it.projectedBalance >= 0.0 })
    }

    @Test
    fun `cash flow projections for ROSCA shows outflow equals monthly pot`() {
        val group = Group(type = GroupType.ROSCA, monthlyContribution = 300.0, balance = 3000.0)
        val proj = GroupTypeActuarialEngine.computeCashFlowProjections(group, 8, 3000.0, 100.0)
        val expectedOutflow = 300.0 * 8  // full pot
        assertEquals(expectedOutflow, proj.first().outflow, 0.01)
    }

    // ── computeGroupInsight integration ───────────────────────────────────

    @Test
    fun `computeGroupInsight for burial society sets solvencyRatio`() {
        val group = Group(
            id = "b1", type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = 300.0, goalAmount = 30000.0, balance = 100000.0
        )
        val members = (1..25).map { Member(status = MemberStatus.ACTIVE) }
        val insight = repo.computeGroupInsight(group, members)
        assertEquals(GroupType.BURIAL_SOCIETY, insight.groupType)
        assertTrue("Solvency ratio should be > 0", insight.solvencyRatio > 0.0)
        assertTrue("Key findings not empty", insight.keyFindings.isNotEmpty())
        assertTrue("Recommendations not empty", insight.recommendations.isNotEmpty())
        assertEquals(12, insight.monthlyProjections.size)
    }

    @Test
    fun `computeGroupInsight for investment club sets navPerUnit`() {
        val group = Group(id = "i1", type = GroupType.INVESTMENT_CLUB, monthlyContribution = 500.0, balance = 15000.0, goalAmount = 60000.0, periodMonths = 24)
        val members = (1..10).map { Member(status = MemberStatus.ACTIVE, totalPaid = 1000.0) }
        val insight = repo.computeGroupInsight(group, members)
        assertEquals(GroupType.INVESTMENT_CLUB, insight.groupType)
        assertTrue("navPerUnit should be set", insight.navPerUnit > 0.0)
    }

    @Test
    fun `computeGroupInsight for ROSCA sets monthlyPot`() {
        val group = Group(id = "r1", type = GroupType.ROSCA, monthlyContribution = 300.0, balance = 2400.0)
        val members = (1..8).map { Member(status = MemberStatus.ACTIVE) }
        val insight = repo.computeGroupInsight(group, members)
        assertEquals(GroupType.ROSCA, insight.groupType)
        assertEquals(300.0 * 8, insight.monthlyPot, 0.01)
    }

    @Test
    fun `computeGroupInsight for stokvel sets potMilestonePct`() {
        val group = Group(id = "s1", type = GroupType.STOKVEL, monthlyContribution = 200.0, balance = 5000.0)
        val members = (1..12).map { Member(status = MemberStatus.ACTIVE) }
        val insight = repo.computeGroupInsight(group, members)
        assertEquals(GroupType.STOKVEL, insight.groupType)
        assertTrue("potMilestonePct in range", insight.potMilestonePct in 0.0..100.0)
    }

    @Test
    fun `computeGroupInsight for emergency fund sets coverageMonths`() {
        val group = Group(id = "e1", type = GroupType.EMERGENCY_FUND, monthlyContribution = 500.0, balance = 40000.0)
        val members = (1..8).map { Member(status = MemberStatus.ACTIVE) }
        val insight = repo.computeGroupInsight(group, members)
        assertEquals(GroupType.EMERGENCY_FUND, insight.groupType)
        assertTrue("coverageMonths >= 0", insight.coverageMonths >= 0.0)
    }

    @Test
    fun `computeGroupInsight for tontine sets projectedShareAtEnd`() {
        val group = Group(id = "t1", type = GroupType.TONTINE, monthlyContribution = 400.0, balance = 20000.0, periodMonths = 60)
        val members = (1..15).map { Member(status = MemberStatus.ACTIVE) }
        val insight = repo.computeGroupInsight(group, members)
        assertEquals(GroupType.TONTINE, insight.groupType)
        assertTrue("projectedShareAtEnd > 0", insight.projectedShareAtEnd > 0.0)
    }

    @Test
    fun `computeGroupInsight benchmark notes are populated for all types`() {
        val types = listOf(
            GroupType.BURIAL_SOCIETY, GroupType.STOKVEL, GroupType.ROSCA,
            GroupType.INVESTMENT_CLUB, GroupType.EMERGENCY_FUND,
            GroupType.TONTINE, GroupType.COMMUNITY_SAVINGS, GroupType.OTHER
        )
        val group = Group(monthlyContribution = 300.0, balance = 10000.0)
        val members = (1..10).map { Member(status = MemberStatus.ACTIVE) }
        types.forEach { type ->
            val insight = repo.computeGroupInsight(group.copy(type = type), members)
            assertTrue("Benchmark notes for $type", insight.industryBenchmark.benchmarkNotes.isNotEmpty())
        }
    }
}
