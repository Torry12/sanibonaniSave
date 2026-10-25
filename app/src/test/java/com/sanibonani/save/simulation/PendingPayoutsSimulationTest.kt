package com.sanibonani.save.simulation

import com.sanibonani.save.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PendingPayoutsSimulationTest {

    @Test
    fun `simulate lifecycle of pending payouts across all payout types`() {
        val payoutTypes = PayoutType.entries

        val group = Group(
            id = "g_pending_sim",
            name = "Pending Payout Simulation Group",
            type = GroupType.STOKVEL,
            balance = Money("85000.0")
        )

        val member = Member(
            id = "m_pending_sim",
            groupId = group.id!!,
            status = MemberStatus.ACTIVE,
            bankName = "Standard Bank",
            accountNo = "051234567",
            branchCode = "051001"
        )

        val pendingPayouts = payoutTypes.mapIndexed { index, type ->
            PayoutRequest(
                id = "payout_pending_$index",
                groupId = group.id!!,
                memberId = member.id,
                amount = Money("2500") + Money("500") * index,
                type = type,
                status = PayoutStatus.PENDING,
                bankName = member.bankName!!,
                accountNo = member.accountNo!!,
                branchCode = member.branchCode!!,
                createdAt = LocalDate.now().minusDays(index.toLong()).toString()
            )
        }

        assertEquals(payoutTypes.size, pendingPayouts.size)

        pendingPayouts.forEach { payout ->
            // 1. Verify status is PENDING
            assertEquals(PayoutStatus.PENDING, payout.status)

            // 2. Verify group liquidity
            assertTrue("Group balance must cover pending payout", group.balance >= payout.amount)

            // 3. Simulate state transition to GROUP_APPROVED
            val approvedPayout = payout.copy(status = PayoutStatus.GROUP_APPROVED)
            assertEquals(PayoutStatus.GROUP_APPROVED, approvedPayout.status)

            // 4. Simulate state transition to COMPLETED with R5 platform fee deduction
            val completedPayout = approvedPayout.copy(
                status = PayoutStatus.COMPLETED,
                processedAt = LocalDate.now().toString()
            )
            assertEquals(PayoutStatus.COMPLETED, completedPayout.status)
            val netPayout = completedPayout.amount - Money("5.0")
            assertTrue("Net payout after fee deduction should be positive", netPayout.isPositive)
        }
    }
}
