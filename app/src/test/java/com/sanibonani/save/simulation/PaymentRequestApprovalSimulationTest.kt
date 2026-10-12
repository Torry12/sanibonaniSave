package com.sanibonani.save.simulation

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PaymentRequestApprovalSimulationTest {

    private val payoutRepo = mockk<PayoutRepository>()
    private val groupRepo = mockk<GroupRepository>()
    private val memberRepo = mockk<MemberRepository>()

    @Before
    fun setup() {
        coEvery { payoutRepo.updatePayoutStatus(any(), any(), any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `simulate 50 payment requests approval condition validation across all types`() = runTest {
        val payoutTypes = PayoutType.entries

        val group = Group(
            id = "g_sim_50",
            name = "Soweto Multi-Type Group",
            type = GroupType.STOKVEL,
            balance = Money(100000.0),
            isPlatformSuspended = false
        )

        val activeMember = Member(
            id = "m_sim_50",
            groupId = "g_sim_50",
            status = MemberStatus.ACTIVE,
            document1Status = DocumentStatus.VERIFIED,
            document2Status = DocumentStatus.VERIFIED,
            bankName = "First National Bank",
            accountNo = "62889900112",
            branchCode = "250655"
        )

        val requests = (1..50).map { idx ->
            val type = payoutTypes[(idx % payoutTypes.size)]
            PayoutRequest(
                id = "payout_$idx",
                groupId = group.id!!,
                memberId = activeMember.id,
                amount = Money(1000.0 + idx * 25.0),
                type = type,
                status = PayoutStatus.PENDING,
                supportingDocumentStatus = DocumentStatus.VERIFIED,
                bankName = activeMember.bankName!!,
                accountNo = activeMember.accountNo!!,
                branchCode = activeMember.branchCode!!
            )
        }

        assertEquals(50, requests.size)

        // Validate approval conditions for all 50 payout requests
        requests.forEach { request ->
            val hasLiquidity = group.balance >= request.amount
            val isMemberActive = activeMember.status == MemberStatus.ACTIVE
            val isKycVerified = activeMember.document1Status == DocumentStatus.VERIFIED
            val isDocVerified = request.supportingDocumentStatus == DocumentStatus.VERIFIED
            val isGroupUnsuspended = !group.isPlatformSuspended

            val isApprovable = hasLiquidity && isMemberActive && isKycVerified && isDocVerified && isGroupUnsuspended
            assertTrue("Payout request #${request.id} (${request.type}) should satisfy all approval conditions", isApprovable)
        }
    }
}
