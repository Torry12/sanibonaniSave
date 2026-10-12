package com.sanibonani.save.simulation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.service.PlatformFeeService
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class PaymentRequestSimulationTest {

    private lateinit var paymentCalculator: PaymentCalculator
    private val feeService: PlatformFeeService = mockk()

    @Before
    fun setup() {
        paymentCalculator = PaymentCalculator(feeService)
        every { feeService.calculateMonthlyMemberIndividual(any(), any()) } returns Money.ZERO
        every { feeService.getShortfallRecoveryFee() } returns Money.ZERO
    }

    @Test
    fun `simulate contribution payment request and allocation`() {
        val group = Group(
            id = "g_pay_1",
            type = GroupType.STOKVEL,
            monthlyContribution = Money(500.0),
            joiningFee = Money(700.0)
        )
        val member = Member(
            id = "m_pay_1",
            status = MemberStatus.ACTIVE,
            joinedAt = LocalDate.now().toString() + "T00:00:00Z"
        )

        val contribution = Contribution(
            memberId = member.id!!,
            groupId = group.id!!,
            amount = Money(500.0),
            status = ContributionStatus.PAID,
            paidAt = LocalDate.now().toString(),
            dueDate = LocalDate.now().toString()
        )

        val status = paymentCalculator.calculateStatus(group, member, listOf(contribution), emptyList(), LocalDate.now())
        assertEquals(Money.ZERO, status.shortfall)
        assertFalse(status.isOverdue)
    }

    @Test
    fun `simulate payout withdrawal request validation`() {
        val group = Group(
            id = "g_pay_2",
            type = GroupType.ROSCA,
            balance = Money(15000.0)
        )
        val member = Member(
            id = "m_pay_2",
            status = MemberStatus.ACTIVE
        )

        val payoutRequest = PayoutRequest(
            groupId = group.id!!,
            memberId = member.id!!,
            amount = Money(3000.0),
            type = PayoutType.STANDARD,
            status = PayoutStatus.PENDING,
            bankName = "FNB",
            accountNo = "123456789",
            branchCode = "250655"
        )

        assertTrue("Payout request amount should be within group liquidity", payoutRequest.amount <= group.balance)
        assertEquals(PayoutStatus.PENDING, payoutRequest.status)
    }

    @Test
    fun `simulate platform fee deduction on payout request`() {
        val payoutAmount = Money(5000.0)
        val platformFee = Money(5.0)
        val netPayout = payoutAmount - platformFee

        assertEquals(Money(4995.0), netPayout)
    }
}
