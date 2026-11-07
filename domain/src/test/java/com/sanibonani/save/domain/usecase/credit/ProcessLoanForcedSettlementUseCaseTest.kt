package com.sanibonani.save.domain.usecase.credit

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanRepayment
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.RecordRepaymentResult
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
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProcessLoanForcedSettlementUseCaseTest {

    private val loanRepository: LoanRepository = mockk(relaxed = true)
    private val memberRepository: MemberRepository = mockk(relaxed = true)
    private val contributionRepository: ContributionRepository = mockk(relaxed = true)
    private val platformRepository: PlatformRepository = mockk(relaxed = true)
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
            notificationRepository,
            paymentCalculator
        )
        coEvery { memberRepository.getMemberById(any()) } returns Result.success(Member(id = "m1"))
        coEvery { loanRepository.settleExpiredLoan(any()) } answers {
            settled(firstArg(), "g1", "500.00")
        }
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
        coEvery { loanRepository.getGroupLoansSync(groupId) } returns Result.success(loans.toList())
    }

    @Test
    fun `expired loan settlement is performed through one atomic repository operation`() = runBlocking {
        stubGroups(group("g1"))
        stubGroupLoans("g1", loan("l1", "g1", "500.00"))

        assertTrue(useCase().isSuccess)

        coVerify(exactly = 1) { loanRepository.settleExpiredLoan("l1") }
        coVerify(exactly = 0) { loanRepository.updateLoanStatus(any(), any(), any(), any()) }
        coVerify(exactly = 0) { memberRepository.recordContribution(any()) }
    }

    @Test
    fun `a rejected settlement does not stop the later loans in the same group`() = runBlocking {
        stubGroups(group("g1"))
        stubGroupLoans("g1", loan("l1", "g1", "500.00"), loan("l2", "g1", "300.00"))
        coEvery { loanRepository.settleExpiredLoan(any()) } answers {
            if (firstArg<String>() == "l1")
                Result.failure(IllegalStateException("RPC rejected l1"))
            else settled("l2", "g1", "300.00")
        }

        val result = useCase()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("loan l1") == true)
        coVerify(exactly = 1) { loanRepository.settleExpiredLoan("l1") }
        coVerify(exactly = 1) { loanRepository.settleExpiredLoan("l2") }
    }

    @Test
    fun `a rejected settlement does not stop the next group from settling`() = runBlocking {
        stubGroups(group("g1"), group("g2"))
        stubGroupLoans("g1", loan("l1", "g1", "500.00"))
        stubGroupLoans("g2", loan("l2", "g2", "250.00"))
        coEvery { loanRepository.settleExpiredLoan(any()) } answers {
            if (firstArg<String>() == "l1")
                Result.failure(IllegalStateException("RPC rejected g1"))
            else settled("l2", "g2", "250.00")
        }

        val result = useCase()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("loan l1") == true)
        coVerify(exactly = 1) { loanRepository.settleExpiredLoan("l1") }
        coVerify(exactly = 1) { loanRepository.settleExpiredLoan("l2") }
    }

    @Test
    fun `an unreadable group loan ledger is reported while the next group is still processed`() = runBlocking {
        stubGroups(group("g1"), group("g2"))
        // Stubbed on the one-shot read the sweep now makes. The observe flow answers with the Room
        // cache first and sends the refusal behind it, so a failure on its first emission is a shape
        // production never gives - and the sweep would settle against cached rows believing it had
        // read the ledger.
        coEvery { loanRepository.getGroupLoansSync("g1") } returns
            Result.failure(IllegalStateException("offline"))
        stubGroupLoans("g2", loan("l2", "g2", "250.00"))

        val result = useCase()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("group g1 loan history") == true)
        coVerify(exactly = 0) { loanRepository.settleExpiredLoan("l1") }
        coVerify(exactly = 1) { loanRepository.settleExpiredLoan("l2") }
    }

    @Test
    fun `the sweep reports a failure when the group list itself cannot be read`() = runBlocking {
        coEvery { platformRepository.getAllGroups() } returns Result.failure(IllegalStateException("offline"))

        assertTrue(useCase().isFailure)
        coVerify(exactly = 0) { loanRepository.settleExpiredLoan(any()) }
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
        coEvery { contributionRepository.syncMemberContributions(any(), any()) } returns
            Result.failure(IllegalStateException("offline"))
        coEvery { loanRepository.getMemberLoansSync(any()) } returns Result.success(emptyList())

        assertTrue(useCase().isSuccess)

        // An unreadable history has no payments in it; scoring that as arrears would be a sanction
        // for a debt the member may not owe.
        verify(exactly = 0) {
            paymentCalculator.calculateStatus(any(), any(), any<List<Contribution>>(), any<List<Loan>>(), any())
        }
        coVerify(exactly = 0) { memberRepository.updateMemberStatus("m1", MemberStatus.PROBATION) }
    }

    @Test
    fun `a probation demotion the server refused is reported by the sweep that ordered it`() = runBlocking {
        stubGroups(group("g1", GroupType.BURIAL_SOCIETY))
        stubGroupLoans("g1", loan("l1", "g1", "500.00"))
        stubPaymentShortfall("200.01")
        coEvery { memberRepository.updateMemberStatus("m1", MemberStatus.PROBATION) } returns
            Result.failure(IOException("HTTP 500"))

        val result = useCase()

        // The Result used to be dropped on the floor: the member keeps full standing, the sweep
        // reports a clean run, and because the loan is no longer active nothing ever revisits it.
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("the report names the sanction that did not apply: $message", message.contains("m1 probation"))
        // and it must not read as though the settlement itself had not happened - that money moved.
        assertFalse("the settled loan is not reported as unsettled: $message", message.contains("loan l1"))
    }

    private fun stubPaymentShortfall(shortfall: String) {
        coEvery { contributionRepository.syncMemberContributions(any(), any()) } returns
            Result.success(emptyList())
        coEvery { loanRepository.getMemberLoansSync(any()) } returns Result.success(emptyList())
        every {
            paymentCalculator.calculateStatus(any(), any(), any<List<Contribution>>(), any<List<Loan>>(), any())
        } returns PaymentCalculation(
            shortfall = Money(shortfall),
            overpayment = Money.ZERO,
            nextDueDate = LocalDate.now().toString(),
            totalDueNow = Money(shortfall)
        )
    }

    private fun settled(loanId: String, groupId: String, amount: String): Result<RecordRepaymentResult> =
        Result.success(
            RecordRepaymentResult(
                repayment = LoanRepayment(
                    loanId = loanId,
                    memberId = "m1",
                    groupId = groupId,
                    amount = Money(amount)
                ),
                newBalance = Money("500.00")
            )
        )
}
