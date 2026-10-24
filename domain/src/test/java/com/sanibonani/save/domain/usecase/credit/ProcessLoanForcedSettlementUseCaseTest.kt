package com.sanibonani.save.domain.usecase.credit

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.utils.PaymentCalculator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProcessLoanForcedSettlementUseCaseTest {

    private val loanRepository: LoanRepository = mockk(relaxed = true)
    private val memberRepository: MemberRepository = mockk(relaxed = true)
    private val contributionRepository: ContributionRepository = mockk(relaxed = true)
    private val platformRepository: PlatformRepository = mockk(relaxed = true)
    private val auditLogRepository: AuditLogRepository = mockk(relaxed = true)
    private val notificationRepository: NotificationRepository = mockk(relaxed = true)
    private val paymentCalculator: PaymentCalculator = mockk(relaxed = true)
    private lateinit var useCase: ProcessLoanForcedSettlementUseCase

    private val expiredStartDate: String = LocalDate.now().minusMonths(4).toString()

    @Before
    fun setUp() {
        useCase = ProcessLoanForcedSettlementUseCase(
            loanRepository,
            memberRepository,
            contributionRepository,
            platformRepository,
            auditLogRepository,
            notificationRepository,
            paymentCalculator
        )
        coEvery { memberRepository.getMemberById(any()) } returns Result.success(Member(id = "m1"))
        coEvery { memberRepository.recordContribution(any()) } returns Result.success(Unit)
        coEvery { loanRepository.updateLoanStatus(any(), any(), any(), any()) } returns Result.success(Unit)
    }

    private fun group(id: String, type: GroupType = GroupType.STOKVEL) = Group(
        id = id,
        name = "Group $id",
        type = type,
        monthlyContribution = Money("100.00")
    )

    private fun loan(id: String, groupId: String, balance: String) = Loan(
        id = id,
        memberId = "m1",
        groupId = groupId,
        totalToRepay = Money(balance),
        status = LoanStatus.ACTIVE,
        startDate = expiredStartDate
    )

    private fun stubGroups(vararg groups: Group) {
        coEvery { platformRepository.getAllGroups() } returns Result.success(groups.toList())
    }

    private fun stubGroupLoans(groupId: String, vararg loans: Loan) {
        every { loanRepository.getGroupLoans(groupId) } returns flowOf(Result.success(loans.toList()))
    }

    @Test
    fun `a rejected settlement does not stop the later loans in the same group`() = runBlocking {
        stubGroups(group("g1"))
        stubGroupLoans("g1", loan("l1", "g1", "500.00"), loan("l2", "g1", "300.00"))
        coEvery { memberRepository.recordContribution(any()) } answers {
            if (firstArg<Contribution>().transactionId == "forced_settlement_l1")
                Result.failure(IllegalStateException("RPC rejected l1"))
            else Result.success(Unit)
        }

        val result = useCase()

        assertTrue(result.isSuccess)
        // l1 never reaches its status update, so it stays ACTIVE and is retried next sweep.
        coVerify(exactly = 0) { loanRepository.updateLoanStatus("l1", LoanStatus.COMPLETED, any(), any()) }
        coVerify(exactly = 1) { loanRepository.updateLoanStatus("l2", LoanStatus.COMPLETED, any(), any()) }
    }

    @Test
    fun `a rejected settlement does not stop the next group from settling`() = runBlocking {
        stubGroups(group("g1"), group("g2"))
        stubGroupLoans("g1", loan("l1", "g1", "500.00"))
        stubGroupLoans("g2", loan("l2", "g2", "250.00"))
        coEvery { memberRepository.recordContribution(any()) } answers {
            if (firstArg<Contribution>().groupId == "g1")
                Result.failure(IllegalStateException("RPC rejected g1"))
            else Result.success(Unit)
        }

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { loanRepository.updateLoanStatus("l1", LoanStatus.COMPLETED, any(), any()) }
        coVerify(exactly = 1) { loanRepository.updateLoanStatus("l2", LoanStatus.COMPLETED, any(), any()) }
    }

    @Test
    fun `the sweep reports a failure when the group list itself cannot be read`() = runBlocking {
        coEvery { platformRepository.getAllGroups() } returns Result.failure(IllegalStateException("offline"))

        assertTrue(useCase().isFailure)
        coVerify(exactly = 0) { memberRepository.recordContribution(any()) }
    }

    @Test
    fun `a burial society member is not returned to probation at exactly two months of shortfall`() = runBlocking {
        stubGroups(group("g1", GroupType.BURIAL_SOCIETY))
        stubGroupLoans("g1", loan("l1", "g1", "500.00"))
        stubPaymentShortfall("200.00")

        assertTrue(useCase().isSuccess)

        coVerify(exactly = 0) { memberRepository.updateMemberStatus("m1", MemberStatus.PROBATION) }
    }

    @Test
    fun `a burial society member is returned to probation past two months of shortfall`() = runBlocking {
        stubGroups(group("g1", GroupType.BURIAL_SOCIETY))
        stubGroupLoans("g1", loan("l1", "g1", "500.00"))
        stubPaymentShortfall("200.01")

        assertTrue(useCase().isSuccess)

        coVerify(exactly = 1) { memberRepository.updateMemberStatus("m1", MemberStatus.PROBATION) }
    }

    @Test
    fun `a member whose payment history cannot be read is not returned to probation`() = runBlocking {
        stubGroups(group("g1", GroupType.BURIAL_SOCIETY))
        stubGroupLoans("g1", loan("l1", "g1", "500.00"))
        every { contributionRepository.observeMemberContributions(any(), any()) } returns
            flowOf(Result.failure(IllegalStateException("offline")))
        every { loanRepository.getMemberLoans(any()) } returns flowOf(Result.success(emptyList()))

        assertTrue(useCase().isSuccess)

        // An unreadable history has no payments in it; scoring that as arrears would be a sanction
        // for a debt the member may not owe.
        verify(exactly = 0) {
            paymentCalculator.calculateStatus(any(), any(), any<List<Contribution>>(), any<List<Loan>>(), any())
        }
        coVerify(exactly = 0) { memberRepository.updateMemberStatus("m1", MemberStatus.PROBATION) }
    }

    private fun stubPaymentShortfall(shortfall: String) {
        every { contributionRepository.observeMemberContributions(any(), any()) } returns
            flowOf(Result.success(emptyList()))
        every { loanRepository.getMemberLoans(any()) } returns flowOf(Result.success(emptyList()))
        every {
            paymentCalculator.calculateStatus(any(), any(), any<List<Contribution>>(), any<List<Loan>>(), any())
        } returns PaymentCalculation(
            shortfall = Money(shortfall),
            overpayment = Money.ZERO,
            nextDueDate = LocalDate.now().toString(),
            totalDueNow = Money(shortfall)
        )
    }
}
