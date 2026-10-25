package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.event.DomainEvent
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.PayoutProcessedEvent
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A rotation pays out automatically, so nothing stands between a defect here and money leaving the
 * group treasury: the same member must not be paid twice for one period, the banking details have to
 * be the ones the validation brain accepts, and the group must only be told funds moved when a payout
 * request actually exists.
 */
class ExecuteRoscaPayoutSecurityTest {

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

    private fun outstandingRequest(createdAt: String?) = PayoutRequest(
        id = "p-existing",
        groupId = "g1",
        memberId = "m1",
        amount = Money("300.0"),
        bankName = "FNB",
        accountNo = "123456789",
        branchCode = "250611",
        status = PayoutStatus.PENDING,
        type = PayoutType.ROTATION,
        createdAt = createdAt
    )

    private fun useCaseWith(member: Member) {
        useCase = ExecuteRoscaPayoutUseCase(
            groupRepository, memberRepository, contributionRepository, payoutRepository,
            loanRepository, behaviorTrackingRepository, platformRepository, rotationUseCase,
            payoutGuard
        )
        coEvery { memberRepository.syncGroupMembers("g1") } returns Result.success(listOf(member))
    }

    @Before
    fun setUp() {
        useCaseWith(member)

        coEvery { groupRepository.getGroupById("g1") } returns Result.success(group)
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
        coEvery { loanRepository.getGroupLoansSync("g1") } returns Result.success(emptyList())
        coEvery { behaviorTrackingRepository.getMemberBehavior(any()) } returns Result.failure(
            Exception("no record")
        )
        coEvery { payoutRepository.getPendingPayouts() } returns Result.success(emptyList())
        coEvery { payoutRepository.processRoscaPayout(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success("payout-1")
        coEvery { platformRepository.logAuditEvent(any()) } returns Result.success(Unit)
    }

    @Test
    fun `a member with an unsettled payout for this period is not paid a second time`() = runBlocking {
        coEvery { payoutRepository.getPendingPayouts() } returns
            Result.success(listOf(outstandingRequest("2026-03-02T09:15:00+00:00")))

        val result = useCase("g1")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.contains("Skipped") == true)
        coVerify(exactly = 0) {
            payoutRepository.processRoscaPayout(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `an unsettled payout from an earlier period does not hold this rotation`() = runBlocking {
        coEvery { payoutRepository.getPendingPayouts() } returns
            Result.success(listOf(outstandingRequest("2026-02-27T09:15:00+00:00")))

        val result = useCase("g1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            payoutRepository.processRoscaPayout("g1", "m1", Money("300.0"), any(), any(), any(), "2026-03")
        }
    }

    @Test
    fun `an unreadable payout queue holds the rotation instead of risking a second payout`() =
        runBlocking {
            coEvery { payoutRepository.getPendingPayouts() } returns Result.failure(
                Exception("offline")
            )

            val result = useCase("g1")

            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()?.contains("Skipped") == true)
            coVerify(exactly = 0) {
                payoutRepository.processRoscaPayout(any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `banking details the validation brain rejects stop the payout`() = runBlocking {
        useCaseWith(member.copy(accountNo = "12ab456789"))

        val result = useCase("g1")

        assertTrue(result.isSuccess)
        val message = result.getOrNull()
        assertTrue(message?.contains("Skipped") == true)
        assertTrue(message?.contains("digits") == true)
        coVerify(exactly = 0) {
            payoutRepository.processRoscaPayout(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a branch code the validation brain rejects stops the payout`() = runBlocking {
        useCaseWith(member.copy(branchCode = "2506"))

        val result = useCase("g1")

        assertTrue(result.isSuccess)
        val message = result.getOrNull()
        assertTrue(message?.contains("Skipped") == true)
        assertTrue(message?.contains("Branch code") == true)
        coVerify(exactly = 0) {
            payoutRepository.processRoscaPayout(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a rotation payout is announced as a request, not as money already paid`() = runBlocking {
        val received = CopyOnWriteArrayList<DomainEvent>()
        // EventBus has no replay, so the collector has to be registered before the emission, and
        // Unconfined keeps the emission synchronous instead of queued behind the test body.
        val collector = launch(context = Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            EventBus.events.collect { received.add(it) }
        }

        val result = useCase("g1")

        collector.cancel()
        assertTrue(result.isSuccess)
        val announcement = received.filterIsInstance<PayoutProcessedEvent>().single()
        assertTrue(announcement.status == PayoutStatus.PENDING)
        assertTrue(announcement.payout.status == PayoutStatus.PENDING)

        val audit = slot<AuditLog>()
        coVerify { platformRepository.logAuditEvent(capture(audit)) }
        assertTrue(audit.captured.action == "ROSCA_PAYOUT_INITIATED")
    }

    @Test
    fun `a rotation absorbed entirely by loan offsets announces no payout at all`() = runBlocking {
        coEvery { loanRepository.getGroupLoansSync("g1") } returns Result.success(
            listOf(
                Loan(
                    id = "l1",
                    memberId = "m1",
                    groupId = "g1",
                    totalToRepay = Money("500.0"),
                    totalRepaid = Money("200.0"),
                    status = LoanStatus.ACTIVE
                )
            )
        )
        // A pot the offsets absorb creates no payouts row, and the server reports that as no id.
        coEvery { payoutRepository.processRoscaPayout(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success<String?>(null)
        val received = CopyOnWriteArrayList<DomainEvent>()
        val collector = launch(context = Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            EventBus.events.collect { received.add(it) }
        }

        val result = useCase("g1")

        collector.cancel()
        assertTrue(result.isSuccess)
        val netAmount = slot<Money>()
        coVerify(exactly = 1) {
            payoutRepository.processRoscaPayout("g1", "m1", any(), any(), capture(netAmount), any(), "2026-03")
        }
        assertTrue(netAmount.captured.isZero)
        assertTrue(received.filterIsInstance<PayoutProcessedEvent>().isEmpty())
    }

    @Test
    fun `a rotation that creates no payout row does not claim a payout was created`() = runBlocking {
        coEvery { payoutRepository.processRoscaPayout(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success<String?>(null)

        val result = useCase("g1")

        assertTrue(result.isSuccess)
        val message = result.getOrNull().orEmpty()
        assertFalse(message.contains("Created payout"))
        assertTrue(message.contains("No payout"))
        // No payout row exists, so there is nothing to announce and no initiation to audit.
        coVerify(exactly = 0) { platformRepository.logAuditEvent(any()) }
    }
}
