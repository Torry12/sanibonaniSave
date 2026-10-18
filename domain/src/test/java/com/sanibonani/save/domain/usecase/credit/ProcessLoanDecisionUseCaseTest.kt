package com.sanibonani.save.domain.usecase.credit
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProcessLoanDecisionUseCaseTest {

    private val loanRepo = mockk<LoanRepository>()
    private val memberRepo = mockk<MemberRepository>()
    private val groupRepo = mockk<GroupRepository>()
    private val behaviorRepo = mockk<BehaviorTrackingRepository>()
    private val validateEligibility = mockk<ValidateLoanEligibilityUseCase>()
    private val auditLogRepo = mockk<AuditLogRepository>(relaxed = true)
    private val notificationRepo = mockk<NotificationRepository>(relaxed = true)

    private lateinit var useCase: ProcessLoanDecisionUseCase

    @Before
    fun setUp() {
        useCase = ProcessLoanDecisionUseCase(
            loanRepo, memberRepo, groupRepo, behaviorRepo, validateEligibility, auditLogRepo, notificationRepo
        )
    }

    @Test
    fun `successful approval notifies member and logs audit`() = runBlocking {
        val loan = Loan(id = "l1", memberId = "m1", groupId = "g1", amount = Money("1000.0"))
        val member = Member(id = "m1")
        val group = Group(id = "g1")

        coEvery { loanRepo.getLoanById("l1") } returns Result.success(loan)
        coEvery { memberRepo.getMemberById("m1") } returns Result.success(member)
        coEvery { groupRepo.getGroupById("g1") } returns Result.success(group)
        coEvery { behaviorRepo.getMemberBehavior("m1") } returns Result.success(null)
        coEvery { validateEligibility(any(), any(), any(), any()) } returns ValidateLoanEligibilityUseCase.EligibilityResult.Eligible()
        coEvery { loanRepo.approveLoan("l1", "admin1") } returns Result.success(Unit)

        val result = useCase("l1", ProcessLoanDecisionUseCase.LoanDecision.Approve, "admin1")

        assertTrue(result.isSuccess)
        coVerify { loanRepo.approveLoan("l1", "admin1") }
        coVerify { notificationRepo.sendNotification(any()) }
        coVerify { auditLogRepo.logEvent(match { it.action == "LOAN_DECISION_APPROVED" }) }
    }

    @Test
    fun `rejection notifies member and logs audit`() = runBlocking {
        val loan = Loan(id = "l1", memberId = "m1", groupId = "g1", amount = Money("1000.0"))
        val member = Member(id = "m1")
        val group = Group(id = "g1")

        coEvery { loanRepo.getLoanById("l1") } returns Result.success(loan)
        coEvery { memberRepo.getMemberById("m1") } returns Result.success(member)
        coEvery { groupRepo.getGroupById("g1") } returns Result.success(group)
        coEvery { loanRepo.rejectLoan("l1", "Low credit", "admin1") } returns Result.success(Unit)

        val result = useCase("l1", ProcessLoanDecisionUseCase.LoanDecision.Reject("Low credit"), "admin1")

        assertTrue(result.isSuccess)
        coVerify { loanRepo.rejectLoan("l1", "Low credit", "admin1") }
        coVerify { auditLogRepo.logEvent(match { it.action == "LOAN_DECISION_REJECTED" }) }
    }

    @Test
    fun `approval blocked by critical fraud risk`() = runBlocking {
        val loan = Loan(id = "l1", memberId = "m1", groupId = "g1", amount = Money("1000.0"))
        val member = Member(id = "m1")
        val group = Group(id = "g1")
        val track = MemberBehaviorTrack(memberId = "m1", groupId = "g1", fraudRiskLevel = FraudRiskLevel.CRITICAL)

        coEvery { loanRepo.getLoanById("l1") } returns Result.success(loan)
        coEvery { memberRepo.getMemberById("m1") } returns Result.success(member)
        coEvery { groupRepo.getGroupById("g1") } returns Result.success(group)
        coEvery { behaviorRepo.getMemberBehavior("m1") } returns Result.success(track)

        val result = useCase("l1", ProcessLoanDecisionUseCase.LoanDecision.Approve, "admin1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("CRITICAL fraud risk") == true)
        coVerify(exactly = 0) { loanRepo.approveLoan(any(), any()) }
    }

    @Test
    fun `approval blocked by eligibility re-validation failure`() = runBlocking {
        val loan = Loan(id = "l1", memberId = "m1", groupId = "g1", amount = Money("1000.0"))
        val member = Member(id = "m1")
        val group = Group(id = "g1")

        coEvery { loanRepo.getLoanById("l1") } returns Result.success(loan)
        coEvery { memberRepo.getMemberById("m1") } returns Result.success(member)
        coEvery { groupRepo.getGroupById("g1") } returns Result.success(group)
        coEvery { behaviorRepo.getMemberBehavior("m1") } returns Result.success(null)
        coEvery { validateEligibility(any(), any(), any(), any()) } returns ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible("Arrears")

        val result = useCase("l1", ProcessLoanDecisionUseCase.LoanDecision.Approve, "admin1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Arrears") == true)
        coVerify(exactly = 0) { loanRepo.approveLoan(any(), any()) }
    }
}
