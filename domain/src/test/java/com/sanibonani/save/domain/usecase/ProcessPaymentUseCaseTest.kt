package com.sanibonani.save.domain.usecase

import com.sanibonani.save.data.utils.PaymentCalculation
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.model.PlatformFees
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.repository.PaymentGatewayRepository
import com.sanibonani.save.domain.repository.PaymentRepository
import com.sanibonani.save.domain.repository.PlatformRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProcessPaymentUseCaseTest {

    private lateinit var processPaymentUseCase: ProcessPaymentUseCase
    private val paymentRepository: PaymentRepository = mockk()
    private val groupRepository: GroupRepository = mockk()
    private val memberRepository: MemberRepository = mockk()
    private val notificationRepository: NotificationRepository = mockk()
    private val platformRepository: PlatformRepository = mockk()
    private val gatewayRepository: PaymentGatewayRepository = mockk()

    @Before
    fun setUp() {
        PlatformFees.MONTHLY_MEMBER_FEE = 0.0
        processPaymentUseCase = ProcessPaymentUseCase(
            paymentRepository,
            groupRepository,
            memberRepository,
            notificationRepository,
            platformRepository,
            gatewayRepository
        )
    }

    @After
    fun tearDown() {
        PlatformFees.MONTHLY_MEMBER_FEE = 0.0
    }

    @Test
    fun `process joining fee payment success`() = runBlocking {
        // Given
        val member = Member(id = "member-123", groupId = "group-456", status = MemberStatus.PENDING_PAYMENT)
        val amount = 150.0
        val groupId = "group-456"
        val txIdPrefix = "tx_"
        
        coEvery { paymentRepository.recordPayment(any()) } returns Result.success("payment-id")
        coEvery { memberRepository.registerMember(any(), any()) } returns Result.success(member.copy(status = MemberStatus.ACTIVE))
        coEvery { notificationRepository.sendNotification(any()) } returns Result.success(Unit)

        // When
        val result = processPaymentUseCase(
            type = PaymentType.JOINING_FEE,
            amount = amount,
            groupId = groupId,
            member = member
        )

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().startsWith(txIdPrefix))
        
        coVerify { paymentRepository.recordPayment(match { 
            it.paymentType == PaymentType.JOINING_FEE && it.amount == amount 
        }) }
        coVerify { memberRepository.registerMember(member, any()) }
        coVerify { notificationRepository.sendNotification(any()) }
    }

    @Test
    fun `process contribution payment success`() = runBlocking {
        // Given
        PlatformFees.MONTHLY_MEMBER_FEE = 10.0
        val member = Member(id = "member-123", groupId = "group-456", status = MemberStatus.ACTIVE)
        val group = Group(id = "group-456", monthlyContribution = 200.0)
        val amount = 200.0
        val calculation = PaymentCalculation(
            shortfall = 0.0,
            overpayment = 0.0,
            nextDueDate = "2024-05-28",
            totalDueNow = 200.0
        )
        
        coEvery { paymentRepository.recordPayment(any()) } returns Result.success("payment-id")
        coEvery { memberRepository.recordContribution(any()) } returns Result.success(mockk())
        coEvery { notificationRepository.sendNotification(any()) } returns Result.success(Unit)

        // When
        val result = processPaymentUseCase(
            type = PaymentType.CONTRIBUTION,
            amount = amount,
            groupId = "group-456",
            member = member,
            group = group,
            calculation = calculation
        )

        // Then
        assertTrue(result.isSuccess)
        coVerify { memberRepository.recordContribution(match {
            it.type == "contribution" && it.amount == 190.0 && it.dueDate == calculation.nextDueDate
        }) }
        coVerify { memberRepository.recordContribution(match {
            it.type == "member_fee_ledger" && it.amount == 10.0 && it.dueDate == calculation.nextDueDate
        }) }
    }

    @Test
    fun `process joining fee payment failure on recordPayment should fail entire use case`() = runBlocking {
        // Given
        val member = Member(id = "member-123", groupId = "group-456", status = MemberStatus.PENDING_PAYMENT)
        val amount = 150.0
        
        coEvery { paymentRepository.recordPayment(any()) } returns Result.failure(Exception("DB Error"))

        // When
        val result = processPaymentUseCase(
            type = PaymentType.JOINING_FEE,
            amount = amount,
            groupId = "group-456",
            member = member
        )

        // Then
        assertTrue(result.isFailure)
        assertEquals("DB Error", result.exceptionOrNull()?.message)
        
        coVerify(exactly = 0) { memberRepository.registerMember(any(), any()) }
        coVerify(exactly = 0) { notificationRepository.sendNotification(any()) }
    }

    @Test
    fun `process joining fee payment failure on registerMember should fail entire use case`() = runBlocking {
        // Given
        val member = Member(id = "member-123", groupId = "group-456", status = MemberStatus.PENDING_PAYMENT)
        val amount = 150.0
        
        coEvery { paymentRepository.recordPayment(any()) } returns Result.success("payment-id")
        coEvery { memberRepository.registerMember(any(), any()) } returns Result.failure(Exception("Activation Failed"))

        // When
        val result = processPaymentUseCase(
            type = PaymentType.JOINING_FEE,
            amount = amount,
            groupId = "group-456",
            member = member
        )

        // Then
        assertTrue(result.isFailure)
        assertEquals("Activation Failed", result.exceptionOrNull()?.message)
        
        coVerify { paymentRepository.recordPayment(any()) }
        coVerify { memberRepository.registerMember(any(), any()) }
        coVerify(exactly = 0) { notificationRepository.sendNotification(any()) }
    }

    @Test
    fun `process platform fee for new group returns txId without side effects`() = runBlocking {
        // Given
        val amount = 500.0
        val groupId = "new_group"

        // When
        val result = processPaymentUseCase(
            type = PaymentType.PLATFORM_FEE,
            amount = amount,
            groupId = groupId
        )

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().startsWith("tx_"))
        
        coVerify(exactly = 0) { groupRepository.activateGroup(any(), any()) }
        coVerify(exactly = 0) { paymentRepository.recordPayment(any()) }
    }

    @Test
    fun `process platform fee for existing group with registrationPaid false activates group`() = runBlocking {
        // Given
        val amount = 500.0
        val groupId = "group-123"
        val group = Group(id = groupId, registrationPaid = false)
        
        coEvery { groupRepository.activateGroup(any(), any()) } returns Result.success(Unit)
        coEvery { paymentRepository.recordPayment(any()) } returns Result.success("payment-id")

        // When
        val result = processPaymentUseCase(
            type = PaymentType.PLATFORM_FEE,
            amount = amount,
            groupId = groupId,
            group = group
        )

        // Then
        assertTrue(result.isSuccess)
        coVerify { groupRepository.activateGroup(groupId, any()) }
        coVerify { paymentRepository.recordPayment(match { it.paymentType == PaymentType.PLATFORM_FEE }) }
    }
}
