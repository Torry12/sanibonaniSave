package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.treasury.*
import com.sanibonani.save.domain.service.FraudDetectionService
import com.sanibonani.save.domain.usecase.equity.ProcessContributionUseCase
import com.sanibonani.save.domain.usecase.treasury.ActivateGroupUseCase
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProcessContributionUseCaseTest {

    private lateinit var useCase: ProcessContributionUseCase
    private val contributionRepository: ContributionRepository = mockk()
    private val groupRepository: GroupRepository = mockk()
    private val memberRepository: MemberRepository = mockk()
    private val platformRepository: PlatformRepository = mockk()
    private val providerRepository: FinancialProviderRepository = mockk(relaxed = true)
    private val loanRepository: LoanRepository = mockk(relaxed = true)
    private val fraudDetectionService: FraudDetectionService = mockk()
    private val activateGroupUseCase: ActivateGroupUseCase = mockk()

    @Before
    fun setUp() {
        useCase = ProcessContributionUseCase(
            contributionRepository,
            groupRepository,
            memberRepository,
            loanRepository,
            platformRepository,
            providerRepository,
            fraudDetectionService,
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
        val member = Member(id = "m1", groupId = "g1", status = MemberStatus.PENDING_PAYMENT)
        val amount = Money(150.0)

        coEvery { contributionRepository.processComplexPayment(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Money(150.0))
        coEvery { memberRepository.updateMemberStatus(any(), any()) } returns Result.success(Unit)

        // When
        val result = useCase(
            type = PaymentType.JOINING_FEE,
            amount = amount,
            groupId = "g1",
            member = member
        )

        // Then
        assertTrue(result.isSuccess)
        coVerify { contributionRepository.processComplexPayment(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify { memberRepository.updateMemberStatus("m1", any()) }
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
        assertTrue(result.isSuccess)
        coVerify { activateGroupUseCase("g1", any()) }
    }
}
