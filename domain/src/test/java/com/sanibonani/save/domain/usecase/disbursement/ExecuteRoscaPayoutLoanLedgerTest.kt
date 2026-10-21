package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.service.PayoutGuard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The loan ledger is what turns a collected pot into a net payout. When it cannot be read the
 * deductions are unknown, so paying the gross pot spends the member's debt settlement twice.
 */
class ExecuteRoscaPayoutLoanLedgerTest {

    private val groupRepository: GroupRepository = mockk()
    private val memberRepository: MemberRepository = mockk()
    private val contributionRepository: ContributionRepository = mockk()
    private val payoutRepository: PayoutRepository = mockk()
    private val loanRepository: LoanRepository = mockk()
    private val behaviorTrackingRepository: BehaviorTrackingRepository = mockk()
    private val platformRepository: PlatformRepository = mockk()
    private val rotationUseCase: CalculateRoscaRotationUseCase = mockk()
    private val payoutGuard: PayoutGuard = mockk()

    private lateinit var useCase: ExecuteRoscaPayoutUseCase

    private val group = Group(
        id = "g1",
        name = "Saru",
        type = GroupType.ROSCA,
        monthlyContribution = Money("300.0")
    )
    private val member = Member(
        id = "m1",
        groupId = "g1",
        fullName = "Nomsa",
        status = MemberStatus.ACTIVE,
        bankName = "FNB",
        accountNo = "123456789",
        branchCode = "250611"
    )
    private val item = CalculateRoscaRotationUseCase.RoscaRotationItem(
        memberId = "m1",
        memberName = "Nomsa",
        payoutDate = "2026-03-15",
        isCurrent = true,
        isCompleted = false
    )
    private val schedule = CalculateRoscaRotationUseCase.RoscaSchedule(
        groupId = "g1",
        totalPot = Money("300.0"),
        cycleMonths = 1,
        items = listOf(item),
        subgroups = listOf(
            CalculateRoscaRotationUseCase.RoscaSubgroup(
                id = "s1",
                memberIds = listOf("m1"),
                items = listOf(item)
            )
        )
    )

    @Before
    fun setUp() {
        useCase = ExecuteRoscaPayoutUseCase(
            groupRepository, memberRepository, contributionRepository, payoutRepository,
            loanRepository, behaviorTrackingRepository, platformRepository, rotationUseCase,
            payoutGuard
        )

        coEvery { groupRepository.getGroupById("g1") } returns Result.success(group)
        coEvery { memberRepository.syncGroupMembers("g1") } returns Result.success(listOf(member))
        every { contributionRepository.observeGroupContributions("g1") } returns flowOf(
            Result.success(
                listOf(
                    Contribution(
                        memberId = "m1",
                        groupId = "g1",
                        amount = Money("300.0"),
                        dueDate = "2026-03-15",
                        status = ContributionStatus.PAID
                    )
                )
            )
        )
        every { rotationUseCase(any(), any()) } returns Result.success(schedule)
        coEvery { payoutGuard.validateRequest(any(), any(), any(), any()) } returns Result.success(Unit)
        // No behaviour record: the arrears guard must not be what stops the payout.
        coEvery { behaviorTrackingRepository.getMemberBehavior(any()) } returns Result.failure(
            Exception("no record")
        )
        // An empty queue: the duplicate-payout guard must not be what stops the payout either.
        coEvery { payoutRepository.getPendingPayouts() } returns Result.success(emptyList())
    }

    @Test
    fun `a payoutee whose loan ledger could not be read is skipped instead of paid the gross pot`() = runBlocking {
        coEvery { loanRepository.getGroupLoansSync("g1") } returns Result.failure(Exception("offline"))

        val result = useCase("g1")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.contains("Skipped") == true)
        coVerify(exactly = 0) {
            payoutRepository.processRoscaPayout(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a readable ledger with no active loans still pays the member out`() = runBlocking {
        coEvery { loanRepository.getGroupLoansSync("g1") } returns Result.success(emptyList())
        coEvery { payoutRepository.processRoscaPayout(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success("payout-1")
        coEvery { platformRepository.logAuditEvent(any()) } returns Result.success(Unit)

        val result = useCase("g1")

        assertTrue(result.isSuccess)
        val deductions = slot<JsonArray>()
        val netAmount = slot<Money>()
        coVerify(exactly = 1) {
            payoutRepository.processRoscaPayout(
                groupId = "g1",
                memberId = "m1",
                totalPot = Money("300.0"),
                loanDeductions = capture(deductions),
                payoutAmount = capture(netAmount),
                payoutData = any(),
                period = "2026-03"
            )
        }
        assertTrue(deductions.captured.isEmpty())
        assertTrue(netAmount.captured == Money("300.0"))
    }

    @Test
    fun `a readable ledger deducts the outstanding loan from the pot`() = runBlocking {
        coEvery { loanRepository.getGroupLoansSync("g1") } returns Result.success(
            listOf(
                Loan(
                    id = "l1",
                    memberId = "m1",
                    groupId = "g1",
                    totalToRepay = Money("500.0"),
                    totalRepaid = Money("100.0"),
                    status = LoanStatus.ACTIVE
                )
            )
        )
        coEvery { payoutRepository.processRoscaPayout(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success("payout-1")
        coEvery { platformRepository.logAuditEvent(any()) } returns Result.success(Unit)

        val result = useCase("g1")

        assertTrue(result.isSuccess)
        val deductions = slot<JsonArray>()
        val netAmount = slot<Money>()
        coVerify(exactly = 1) {
            payoutRepository.processRoscaPayout(
                groupId = "g1",
                memberId = "m1",
                totalPot = Money("300.0"),
                loanDeductions = capture(deductions),
                payoutAmount = capture(netAmount),
                payoutData = any(),
                period = "2026-03"
            )
        }
        // The pot is fully absorbed by the outstanding loan, so the net payout must be zero.
        assertTrue(deductions.captured.size == 1)
        assertTrue(netAmount.captured.isZero)
    }
}
