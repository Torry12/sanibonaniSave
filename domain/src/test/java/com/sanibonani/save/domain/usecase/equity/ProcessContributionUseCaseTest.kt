package com.sanibonani.save.domain.usecase.equity
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.service.FraudDetectionService
import com.sanibonani.save.domain.service.PaymentAllocationService
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.sanibonani.save.domain.repository.credit.LoanRepository

class ProcessContributionUseCaseTest {

    private lateinit var useCase: ProcessContributionUseCase
    private val contributionRepository: ContributionRepository = mockk()
    private val groupRepository: GroupRepository = mockk()
    private val memberRepository: MemberRepository = mockk()
    private val platformRepository: PlatformRepository = mockk()
    private val loanRepository: LoanRepository = mockk(relaxed = true)
    private val fraudDetectionService: FraudDetectionService = mockk()
    private val allocationService: PaymentAllocationService = mockk()
    private val activateGroupUseCase: ActivateGroupUseCase = mockk()

    @Before
    fun setUp() {
        useCase = ProcessContributionUseCase(
            contributionRepository,
            groupRepository,
            memberRepository,
            loanRepository,
            platformRepository,
            fraudDetectionService,
            allocationService,
            activateGroupUseCase
        )
        coEvery { fraudDetectionService.evaluateTransaction(any()) } returns Result.success(Unit)
        every { loanRepository.getMemberLoans(any()) } returns flowOf(Result.success(emptyList()))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `invoke should process joining fee successfully`() = runBlocking {
        // Given
        val group = Group(id = "g1", probationMonths = 3)
        val member = Member(id = "m1", groupId = "g1", status = MemberStatus.PENDING_PAYMENT)
        val amount = Money(150.0)

        coEvery { contributionRepository.processComplexPayment(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Money(150.0))
        coEvery { memberRepository.updateMemberStatus(any(), any()) } returns Result.success(Unit)

        // When
        val result = useCase(
            type = PaymentType.JOINING_FEE,
            amount = amount,
            groupId = "g1",
            member = member,
            group = group
        )

        // Then
        assertTrue(result is ContributionOutcome.Success)
        coVerify { contributionRepository.processComplexPayment("g1", "m1", any(), any(), any(), any(), any(), any()) }
        coVerify { memberRepository.updateMemberStatus("m1", MemberStatus.PROBATION) }
    }

    @Test
    fun `invoke should activate group on platform fee if not paid`() = runBlocking {
        // Given
        val group = Group(id = "g1", registrationPaid = false)
        val amount = Money(700.0)

        coEvery { contributionRepository.processComplexPayment(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Money(700.0))
        coEvery { activateGroupUseCase(any(), any()) } returns Result.success(Unit)

        // When
        val result = useCase(
            type = PaymentType.PLATFORM_FEE,
            amount = amount,
            groupId = "g1",
            group = group
        )

        // Then
        assertTrue(result is ContributionOutcome.Success)
        coVerify { activateGroupUseCase("g1", any()) }
    }
}
