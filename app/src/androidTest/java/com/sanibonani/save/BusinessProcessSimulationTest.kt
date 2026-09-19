package com.sanibonani.save

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.di.TestAuthSessionController
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.domain.usecase.groups.GetGroupBusinessInsightsUseCase
import com.sanibonani.save.domain.utils.PaymentCalculator
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BusinessProcessSimulationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var groupRepo: GroupRepository
    @Inject lateinit var memberRepo: MemberRepository
    @Inject lateinit var contributionRepo: MemberContributionRepository
    @Inject lateinit var loanRepo: LoanRepository
    @Inject lateinit var payoutRepo: PayoutRepository
    @Inject lateinit var claimRepo: BeneficiaryClaimRepository
    @Inject lateinit var ledgerRepo: LedgerRepository
    @Inject lateinit var supabaseRepo: SupabaseRepository
    @Inject lateinit var processPayoutUseCase: ProcessPayoutUseCase
    @Inject lateinit var processBurialClaimUseCase: ProcessBurialClaimUseCase
    @Inject lateinit var getGroupBusinessInsightsUseCase: GetGroupBusinessInsightsUseCase

    private val testGroupIds = mutableListOf<String>()

    @Before
    fun init() {
        hiltRule.inject()
        TestAuthSessionController.reset(UserRole.PLATFORM_ADMIN)
        TestAuthSessionController.signIn("torrymsimango@gmail.com")
    }

    @After
    fun cleanup() = runBlocking {
        // Targeted cleanup for SIM- records
        testGroupIds.forEach { gid ->
            // In a real scenario, we might want to call a "DeleteGroup" RPC or similar.
            // For this simulation, we leave records if no easy delete is available,
            // but usually, we should clean up.
        }
    }

    @Test
    fun simulateRoscaCycleNetZeroPot() = runBlocking {
        // 1. Create ROSCA Group
        val groupId = UUID.randomUUID().toString()
        val group = Group(
            id = groupId,
            name = "SIM-ROSCA-Cycle",
            type = GroupType.ROSCA,
            monthlyContribution = Money(1000.0),
            roscaCycle = 5,
            rotationMethod = RoscaRotationMethod.FIXED,
            adminUserId = TestAuthSessionController.mockedUserId,
            registrationPaid = true
        )
        groupRepo.createGroup(group).getOrThrow()
        testGroupIds.add(groupId)

        // 2. Add 5 Members with Positions
        val members = (1..5).map { i ->
            val member = Member(
                groupId = groupId,
                fullName = "SIM-ROSCA-Member-$i",
                email = "rosca.$i@sim.com",
                status = MemberStatus.ACTIVE,
                roscaPosition = i
            )
            memberRepo.registerMember(member).getOrThrow()
        }

        // 3. Collect the Pot (5 x R1,000)
        members.forEach { m ->
            contributionRepo.recordContribution(
                m.id!!, groupId, Money(1000.0), "2026-07-01", "contribution"
            ).getOrThrow()
        }

        val midBalance = groupRepo.getGroupById(groupId).getOrThrow().balance
        assertEquals(5000.0, midBalance.toDouble(), 0.01)

        // 4. Execute Payout for Position 1
        val payout = PayoutRequest(
            groupId = groupId,
            memberId = members[0].id,
            amount = Money(5000.0),
            status = PayoutStatus.GROUP_APPROVED,
            type = PayoutType.STANDARD,
            bankName = "Sim Bank",
            accountNo = "123456",
            branchCode = "0000"
        )
        val payoutId = payoutRepo.requestPayout(payout).getOrThrow()

        processPayoutUseCase(payoutId, groupId, PayoutStatus.COMPLETED, TestAuthSessionController.mockedUserId!!).getOrThrow()

        // 5. Verify Net-Zero Balance
        val finalBalance = groupRepo.getGroupById(groupId).getOrThrow().balance
        assertEquals(0.0, finalBalance.toDouble(), 0.01)
    }

    @Test
    fun simulateLoanLifecycleAndInterest() = runBlocking {
        // 1. Create Group & Member
        val groupId = UUID.randomUUID().toString()
        val group = Group(
            id = groupId,
            name = "SIM-Loan-Lifecycle",
            type = GroupType.STOKVEL,
            monthlyContribution = Money(500.0),
            adminUserId = TestAuthSessionController.mockedUserId,
            registrationPaid = true,
            balance = Money(10000.0) // Seed initial liquidity
        )
        groupRepo.createGroup(group).getOrThrow()
        testGroupIds.add(groupId)

        val member = memberRepo.registerMember(Member(
            groupId = groupId,
            fullName = "SIM-Loan-Borrower",
            status = MemberStatus.ACTIVE
        )).getOrThrow()

        // 2. Apply for R3,000 Loan
        val loan = Loan(
            memberId = member.id!!,
            groupId = groupId,
            amount = Money(3000.0),
            interestRate = 10.0,
            totalToRepay = Money(3300.0),
            monthlyRepayment = Money(1100.0), // 3 months
            status = LoanStatus.PENDING
        )
        val loanId = loanRepo.requestLoan(loan).getOrThrow()

        // 3. Approve and Disburse
        loanRepo.disburseLoan(loanId, PaymentMethod.BANK, TestAuthSessionController.mockedUserId).getOrThrow()

        var currentGroup = groupRepo.getGroupById(groupId).getOrThrow()
        assertEquals(7000.0, currentGroup.balance.toDouble(), 0.01)

        // 4. Repayments
        loanRepo.recordRepayment(LoanRepayment(
            loanId = loanId, memberId = member.id!!, groupId = groupId, amount = Money(1100.0), paymentMethod = PaymentMethod.BANK
        )).getOrThrow()

        loanRepo.recordRepayment(LoanRepayment(
            loanId = loanId, memberId = member.id!!, groupId = groupId, amount = Money(2200.0), paymentMethod = PaymentMethod.BANK
        )).getOrThrow()

        // 5. Verify Completion and Interest Gain
        val finalLoan = loanRepo.getLoanById(loanId).getOrThrow()
        assertEquals(LoanStatus.COMPLETED, finalLoan.status)

        val finalGroup = groupRepo.getGroupById(groupId).getOrThrow()
        // Start: 10000, Disburse: -3000 (7000), Repay: +3300 = 10300
        assertEquals(10300.0, finalGroup.balance.toDouble(), 0.01)
    }

    @Test
    fun simulateStokvelYearEndProjection() = runBlocking {
        val groupId = UUID.randomUUID().toString()
        val group = Group(
            id = groupId,
            name = "SIM-Stokvel-Projection",
            type = GroupType.STOKVEL,
            monthlyContribution = Money(500.0),
            adminUserId = TestAuthSessionController.mockedUserId,
            registrationPaid = true
        )
        groupRepo.createGroup(group).getOrThrow()
        testGroupIds.add(groupId)

        val members = (1..10).map { i ->
            memberRepo.registerMember(Member(
                groupId = groupId, fullName = "SIM-Stokvel-Member-$i", status = MemberStatus.ACTIVE
            )).getOrThrow()
        }

        // Simulate 8 months history
        members.forEach { m ->
            for (i in 1..8) {
                contributionRepo.recordContribution(m.id!!, groupId, Money(500.0), "2026-0$i-01", "contribution").getOrThrow()
            }
        }

        // Verify Payout Projection
        val insight = getGroupBusinessInsightsUseCase(groupRepo.getGroupById(groupId).getOrThrow(), members)
        assertTrue(insight is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Stokvel)
        val projection = (insight as GetGroupBusinessInsightsUseCase.GroupBusinessInsight.Stokvel).projection

        // Expected: (500 * 10 * 8) already paid. (500 * 10 * 4) more expected. Total 60000.
        // Payout per member = 6000.
        assertEquals(6000.0, projection.projectedPayoutPerMember.toDouble(), 0.01)
    }

    @Test
    fun simulateBurialSocietyCompliance() = runBlocking {
        val groupId = UUID.randomUUID().toString()
        val group = Group(
            id = groupId,
            name = "SIM-Burial-Compliance",
            type = GroupType.BURIAL_SOCIETY,
            monthlyContribution = Money(200.0),
            beneficiaryIncreasePct = 10.0,
            adminUserId = TestAuthSessionController.mockedUserId,
            registrationPaid = true,
            balance = Money(50000.0)
        )
        groupRepo.createGroup(group).getOrThrow()
        testGroupIds.add(groupId)

        // Member with one over-65 beneficiary
        val member = Member(
            groupId = groupId,
            fullName = "SIM-Burial-Member",
            status = MemberStatus.ACTIVE,
            beneficiaryOver_65Count = 1
        )
        val m = memberRepo.registerMember(member).getOrThrow()

        // 1. Verify Surcharge: 200 + (10% of 200) = 220
        val expected = PaymentCalculator.calculateExpectedMemberGroupPart(group, m)
        assertEquals(220.0, expected.toDouble(), 0.01)

        // 2. Submit Claim
        val beneficiary = Beneficiary(
            groupId = groupId, memberId = m.id!!, fullName = "SIM-Elderly-Parent", isOver65 = true
        )
        val b = claimRepo.addBeneficiary(beneficiary).getOrThrow()

        val claim = BeneficiaryPayoutClaim(
            groupId = groupId,
            memberId = m.id!!,
            beneficiaryId = b.id!!,
            beneficiaryName = b.fullName,
            claimAmount = Money(15000.0),
            causeOfDeath = "Accidental",
            dateOfDeath = "2026-07-30",
            status = BeneficiaryClaimStatus.APPROVED,
            bankName = "FNB",
            accountNo = "123",
            branchCode = "456",
            accountHolder = "Member"
        )
        val claimId = claimRepo.submitClaim(claim).getOrThrow().id!!

        // 3. Process Payout
        processBurialClaimUseCase(claimId, BeneficiaryClaimStatus.PAID, TestAuthSessionController.mockedUserId!!).getOrThrow()

        // 4. Verify Ledger
        val finalGroup = groupRepo.getGroupById(groupId).getOrThrow()
        assertEquals(35000.0, finalGroup.balance.toDouble(), 0.01)
    }

    @Test
    fun simulateInvestmentClubNAV() = runBlocking {
        val groupId = UUID.randomUUID().toString()
        val group = Group(
            id = groupId,
            name = "SIM-Invest-NAV",
            type = GroupType.INVESTMENT_CLUB,
            adminUserId = TestAuthSessionController.mockedUserId,
            registrationPaid = true
        )
        groupRepo.createGroup(group).getOrThrow()
        testGroupIds.add(groupId)

        // Two investors: A (R10k), B (R5k)
        val memberA = memberRepo.registerMember(Member(groupId = groupId, fullName = "Investor A")).getOrThrow()
        val memberB = memberRepo.registerMember(Member(groupId = groupId, fullName = "Investor B")).getOrThrow()

        contributionRepo.recordContribution(memberA.id!!, groupId, Money(10000.0), "2026-07-01", "contribution").getOrThrow()
        contributionRepo.recordContribution(memberB.id!!, groupId, Money(5000.0), "2026-07-01", "contribution").getOrThrow()

        // Inject R3k Gain
        ledgerRepo.incrementGroupBalance(groupId, Money(3000.0), "Stock Gain", "injection", null, null).getOrThrow()

        // Verify NAV
        // Total Pool = 15k + 3k = 18k.
        // A's share = 10/15 = 66.6%. Value = 0.66 * 18000 = 12000.
        // B's share = 5/15 = 33.3%. Value = 0.33 * 18000 = 6000.

        val insight = getGroupBusinessInsightsUseCase(groupRepo.getGroupById(groupId).getOrThrow(), listOf(memberA, memberB))
        assertTrue(insight is GetGroupBusinessInsightsUseCase.GroupBusinessInsight.InvestmentClub)
        val valuation = (insight as GetGroupBusinessInsightsUseCase.GroupBusinessInsight.InvestmentClub).valuation

        assertEquals(18000.0, valuation.totalPortfolioValue.toDouble(), 0.01)
    }

    @Test
    fun simulateMfaDisbursementGuard() = runBlocking {
        val groupId = UUID.randomUUID().toString()
        val group = Group(
            id = groupId,
            name = "SIM-MFA-Guard",
            type = GroupType.STOKVEL,
            adminUserId = TestAuthSessionController.mockedUserId,
            registrationPaid = true,
            balance = Money(20000.0)
        )
        groupRepo.createGroup(group).getOrThrow()
        testGroupIds.add(groupId)

        val payoutId = payoutRepo.requestPayout(PayoutRequest(
            groupId = groupId,
            amount = Money(15000.0),
            status = PayoutStatus.GROUP_APPROVED,
            bankName = "Sim", accountNo = "1", branchCode = "1"
        )).getOrThrow()

        // 1. Attempt without AAL2
        // Since we are using TestAuthSessionController, we'd need to mock isMfaVerified
        // But the UseCase checks supabaseRepo.isMfaVerified
        // Our MockSupabaseRepository in TestRepoModule returns false for isMfaVerified by default.

        val result = processPayoutUseCase(payoutId, groupId, PayoutStatus.COMPLETED, TestAuthSessionController.mockedUserId!!)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("MFA_REQUIRED") == true)
    }
}
