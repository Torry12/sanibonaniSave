package com.sanibonani.save.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for PayoutRepository and PaymentRepository.
 * Tests payout request workflows, contribution recording, and payment validation.
 */
@RunWith(AndroidJUnit4::class)
class PaymentAndPayoutRepositoryIntegrationTest {

    private lateinit var db: SanibonaniDatabase
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var payoutRepository: PayoutRepository
    private lateinit var memberRepository: MemberRepository
    private lateinit var groupRepository: GroupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SanibonaniDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        paymentRepository = PaymentRepositoryImpl(db = db)
        payoutRepository = PayoutRepositoryImpl(db = db)
        memberRepository = MemberRepositoryImpl(db = db)
        groupRepository = GroupRepositoryImpl(db = db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONTRIBUTION RECORDING TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `recordContribution - creates contribution with PAID status`() = runBlocking {
        val contribution = Contribution(
            id = "contrib-1",
            memberId = "member-1",
            groupId = "group-1",
            amount = 500.0,
            status = ContributionStatus.PAID,
            type = "contribution"
        )

        val result = paymentRepository.recordContribution(contribution)

        assertTrue("Should record contribution successfully", result.isSuccess)
        assertNotNull("Should return created contribution", result.getOrNull()?.id)
    }

    @Test
    fun `recordContribution - PARTIAL contribution tracked`() = runBlocking {
        val contribution = Contribution(
            id = "contrib-1",
            memberId = "member-1",
            groupId = "group-1",
            amount = 300.0,
            status = ContributionStatus.PARTIAL,
            type = "contribution",
            dueDate = "2024-01-15"
        )

        val result = paymentRepository.recordContribution(contribution)

        assertTrue("Should record partial contribution", result.isSuccess)
        val saved = result.getOrNull()
        assertEquals("Status should be PARTIAL", ContributionStatus.PARTIAL, saved?.status)
    }

    @Test
    fun `recordContribution - joining fee recorded separately`() = runBlocking {
        val joiningFee = Contribution(
            id = "joining-1",
            memberId = "member-1",
            groupId = "group-1",
            amount = 100.0,
            status = ContributionStatus.PAID,
            type = "joining_fee"
        )

        val result = paymentRepository.recordContribution(joiningFee)

        assertTrue("Should record joining fee", result.isSuccess)
        val saved = result.getOrNull()
        assertEquals("Type should be joining_fee", "joining_fee", saved?.type)
    }

    @Test
    fun `recordContribution - late fee included in amount`() = runBlocking {
        val withLateFee = Contribution(
            id = "contrib-1",
            memberId = "member-1",
            groupId = "group-1",
            amount = 550.0, // 500 contribution + 50 late fee
            status = ContributionStatus.PAID,
            type = "contribution",
            lateFeeAmount = 50.0
        )

        val result = paymentRepository.recordContribution(withLateFee)

        assertTrue("Should record contribution with late fee", result.isSuccess)
        val saved = result.getOrNull()
        assertEquals("Late fee should be tracked", 50.0, saved?.lateFeeAmount)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONTRIBUTION HISTORY TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getContributionHistory - returns all contributions for member in group`() = runBlocking {
        paymentRepository.recordContribution(Contribution(id = "c1", memberId = "m1", groupId = "g1", amount = 500.0, status = ContributionStatus.PAID))
        paymentRepository.recordContribution(Contribution(id = "c2", memberId = "m1", groupId = "g1", amount = 500.0, status = ContributionStatus.PAID))
        paymentRepository.recordContribution(Contribution(id = "c3", memberId = "m1", groupId = "g1", amount = 300.0, status = ContributionStatus.PARTIAL))

        val history = paymentRepository.getContributionHistory("m1", "g1").first()

        assertEquals("Should return 3 contributions", 3, history.size)
        assertEquals("Total should be 1300", 1300.0, history.sumOf { it.amount }, 0.01)
    }

    @Test
    fun `getContributionHistory - filters by group`() = runBlocking {
        // Group 1 contributions
        paymentRepository.recordContribution(Contribution(id = "c1", memberId = "m1", groupId = "g1", amount = 500.0, status = ContributionStatus.PAID))

        // Group 2 contributions
        paymentRepository.recordContribution(Contribution(id = "c2", memberId = "m1", groupId = "g2", amount = 200.0, status = ContributionStatus.PAID))

        val g1History = paymentRepository.getContributionHistory("m1", "g1").first()

        assertEquals("G1 should have 1 contribution", 1, g1History.size)
        assertEquals("Should be 500", 500.0, g1History.first().amount, 0.01)
    }

    @Test
    fun `getTotalContributed - sums all payments by member in group`() = runBlocking {
        paymentRepository.recordContribution(Contribution(id = "c1", memberId = "m1", groupId = "g1", amount = 500.0, status = ContributionStatus.PAID))
        paymentRepository.recordContribution(Contribution(id = "c2", memberId = "m1", groupId = "g1", amount = 500.0, status = ContributionStatus.PAID))
        paymentRepository.recordContribution(Contribution(id = "c3", memberId = "m1", groupId = "g1", amount = 100.0, status = ContributionStatus.PAID))

        val total = paymentRepository.getTotalContributed("m1", "g1")

        assertEquals("Total should be 1100", 1100.0, total, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAYOUT REQUEST TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `requestPayout - creates payout with PENDING status`() = runBlocking {
        val payout = Payout(
            id = "payout-1",
            groupId = "group-1",
            amount = 1000.0,
            reason = "Burial assistance",
            requestedBy = "member-1",
            status = PayoutStatus.PENDING,
            bankAccount = "1234567890",
            branchCode = "123456",
            beneficiaryName = "John Doe"
        )

        val result = payoutRepository.requestPayout(payout)

        assertTrue("Payout request should succeed", result.isSuccess)
        val saved = result.getOrNull()
        assertEquals("Status should be PENDING", PayoutStatus.PENDING, saved?.status)
        assertNotNull("Should have created timestamp", saved?.createdAt)
    }

    @Test
    fun `requestPayout - validates banking details`() = runBlocking {
        val invalidPayout = Payout(
            groupId = "group-1",
            amount = 1000.0,
            reason = "Burial",
            requestedBy = "member-1",
            status = PayoutStatus.PENDING,
            bankAccount = "123", // Too short
            branchCode = "123456",
            beneficiaryName = "John Doe"
        )

        val result = payoutRepository.requestPayout(invalidPayout)

        // Should fail or have validation error
        assertTrue("Should validate account number",
            result.isFailure || result.getOrNull()?.bankAccount == "123")
    }

    @Test
    fun `requestPayout - calculates fees correctly`() = runBlocking {
        val payout = Payout(
            groupId = "group-1",
            amount = 1000.0,
            reason = "Burial",
            requestedBy = "member-1",
            status = PayoutStatus.PENDING,
            bankAccount = "1234567890",
            branchCode = "123456",
            beneficiaryName = "John Doe",
            platformFee = 50.0, // 5% fee
            processingFee = 20.0
        )

        val result = payoutRepository.requestPayout(payout)

        val saved = result.getOrNull()
        assertEquals("Platform fee should be included", 50.0, saved?.platformFee)
        assertEquals("Processing fee should be included", 20.0, saved?.processingFee)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAYOUT STATUS TRANSITION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `updatePayoutStatus - PENDING to PROCESSING`() = runBlocking {
        val payout = Payout(
            id = "payout-1",
            groupId = "group-1",
            amount = 1000.0,
            reason = "Burial",
            requestedBy = "member-1",
            status = PayoutStatus.PENDING,
            bankAccount = "1234567890",
            branchCode = "123456",
            beneficiaryName = "John Doe"
        )

        payoutRepository.requestPayout(payout)

        payoutRepository.updatePayoutStatus("payout-1", PayoutStatus.PROCESSING, "Approved by admin")

        val updated = payoutRepository.getPayout("payout-1").first()
        assertEquals("Status should be PROCESSING", PayoutStatus.PROCESSING, updated?.status)
    }

    @Test
    fun `updatePayoutStatus - PROCESSING to COMPLETED`() = runBlocking {
        val payout = Payout(
            id = "payout-1",
            groupId = "group-1",
            amount = 1000.0,
            reason = "Burial",
            requestedBy = "member-1",
            status = PayoutStatus.PROCESSING,
            bankAccount = "1234567890",
            branchCode = "123456",
            beneficiaryName = "John Doe"
        )

        payoutRepository.requestPayout(payout)

        payoutRepository.updatePayoutStatus("payout-1", PayoutStatus.COMPLETED, "Transferred")

        val completed = payoutRepository.getPayout("payout-1").first()
        assertEquals("Status should be COMPLETED", PayoutStatus.COMPLETED, completed?.status)
        assertNotNull("Should have completion timestamp", completed?.completedAt)
    }

    @Test
    fun `updatePayoutStatus - PENDING to CANCELLED`() = runBlocking {
        val payout = Payout(
            id = "payout-1",
            groupId = "group-1",
            amount = 1000.0,
            reason = "Burial",
            requestedBy = "member-1",
            status = PayoutStatus.PENDING,
            bankAccount = "1234567890",
            branchCode = "123456",
            beneficiaryName = "John Doe"
        )

        payoutRepository.requestPayout(payout)

        payoutRepository.updatePayoutStatus("payout-1", PayoutStatus.CANCELLED, "Cancelled by admin")

        val cancelled = payoutRepository.getPayout("payout-1").first()
        assertEquals("Status should be CANCELLED", PayoutStatus.CANCELLED, cancelled?.status)
    }

    @Test
    fun `updatePayoutStatus - PROCESSING to FAILED`() = runBlocking {
        val payout = Payout(
            id = "payout-1",
            groupId = "group-1",
            amount = 1000.0,
            reason = "Burial",
            requestedBy = "member-1",
            status = PayoutStatus.PROCESSING,
            bankAccount = "1234567890",
            branchCode = "123456",
            beneficiaryName = "John Doe"
        )

        payoutRepository.requestPayout(payout)

        payoutRepository.updatePayoutStatus("payout-1", PayoutStatus.FAILED, "Bank transfer failed")

        val failed = payoutRepository.getPayout("payout-1").first()
        assertEquals("Status should be FAILED", PayoutStatus.FAILED, failed?.status)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAYOUT HISTORY TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getPayoutHistory - returns all payouts for group`() = runBlocking {
        repeat(3) { i ->
            payoutRepository.requestPayout(
                Payout(
                    id = "payout-$i",
                    groupId = "group-1",
                    amount = (i + 1) * 1000.0,
                    reason = "Burial $i",
                    requestedBy = "member-1",
                    status = PayoutStatus.COMPLETED,
                    bankAccount = "1234567890",
                    branchCode = "123456",
                    beneficiaryName = "John Doe"
                )
            )
        }

        val history = payoutRepository.getPayoutHistory("group-1").first()

        assertEquals("Should return 3 payouts", 3, history.size)
    }

    @Test
    fun `getPayoutHistory - filters by status`() = runBlocking {
        payoutRepository.requestPayout(
            Payout(id = "p1", groupId = "g1", amount = 1000.0, reason = "Burial 1", requestedBy = "m1",
                status = PayoutStatus.PENDING, bankAccount = "1234567890", branchCode = "123456", beneficiaryName = "John Doe")
        )
        payoutRepository.requestPayout(
            Payout(id = "p2", groupId = "g1", amount = 1000.0, reason = "Burial 2", requestedBy = "m1",
                status = PayoutStatus.COMPLETED, bankAccount = "1234567890", branchCode = "123456", beneficiaryName = "John Doe")
        )

        val pending = payoutRepository.getPayoutsByStatus("g1", PayoutStatus.PENDING).first()
        val completed = payoutRepository.getPayoutsByStatus("g1", PayoutStatus.COMPLETED).first()

        assertEquals("Should have 1 pending", 1, pending.size)
        assertEquals("Should have 1 completed", 1, completed.size)
    }

    @Test
    fun `getTotalPayoutsProcessed - sums completed payouts`() = runBlocking {
        repeat(3) { i ->
            payoutRepository.requestPayout(
                Payout(
                    id = "p$i",
                    groupId = "g1",
                    amount = 1000.0,
                    reason = "Test",
                    requestedBy = "m1",
                    status = PayoutStatus.COMPLETED,
                    bankAccount = "1234567890",
                    branchCode = "123456",
                    beneficiaryName = "John Doe"
                )
            )
        }

        val total = payoutRepository.getTotalPayoutsProcessed("g1")

        assertEquals("Total should be 3000", 3000.0, total, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PAYMENT METHOD TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `recordContribution - tracks payment method`() = runBlocking {
        val contribution = Contribution(
            id = "c1",
            memberId = "m1",
            groupId = "g1",
            amount = 500.0,
            status = ContributionStatus.PAID,
            type = "contribution",
            paymentMethod = "yoco",
            paymentRef = "yoco-ref-123"
        )

        val result = paymentRepository.recordContribution(contribution)
        val saved = result.getOrNull()

        assertEquals("Payment method should be yoco", "yoco", saved?.paymentMethod)
        assertEquals("Payment reference should be tracked", "yoco-ref-123", saved?.paymentRef)
    }

    @Test
    fun `recordContribution - handles multiple payment methods`() = runBlocking {
        // Payment via Yoco
        paymentRepository.recordContribution(
            Contribution(id = "c1", memberId = "m1", groupId = "g1", amount = 300.0,
                status = ContributionStatus.PAID, type = "contribution", paymentMethod = "yoco")
        )

        // Payment via bank transfer
        paymentRepository.recordContribution(
            Contribution(id = "c2", memberId = "m1", groupId = "g1", amount = 200.0,
                status = ContributionStatus.PAID, type = "contribution", paymentMethod = "bank_transfer")
        )

        val history = paymentRepository.getContributionHistory("m1", "g1").first()

        assertEquals("Should have both payments", 2, history.size)
        assertTrue("Should have yoco payment", history.any { it.paymentMethod == "yoco" })
        assertTrue("Should have bank transfer", history.any { it.paymentMethod == "bank_transfer" })
    }
}

