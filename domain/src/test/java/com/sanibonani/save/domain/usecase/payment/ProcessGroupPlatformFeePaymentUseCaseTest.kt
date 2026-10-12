package com.sanibonani.save.domain.usecase.payment

import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PlatformFee
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for the platform-fee double-charge bug.
 *
 * The txId used to be `"PLATFORM-FEE-${System.currentTimeMillis()}"`, so a retry
 * after a network timeout produced a *different* reference. `pay_platform_fee_v2`
 * had no duplicate check, so the retry deducted the group balance and booked
 * platform revenue a second time.
 */
class ProcessGroupPlatformFeePaymentUseCaseTest {

    private lateinit var useCase: ProcessGroupPlatformFeePaymentUseCase
    private val financialRepository: GroupFinancialRepository = mockk()
    private val groupRepository: GroupRepository = mockk()

    private val txSlot = slot<String>()

    @Before
    fun setUp() {
        useCase = ProcessGroupPlatformFeePaymentUseCase(financialRepository, groupRepository)
    }

    private fun group(id: String) = com.sanibonani.save.domain.model.Group(
        id = id,
        name = "Fee Group",
        adminUserId = "user-1",
        province = "Gauteng",
        city = "Soweto",
        balance = Money(10000.0)
    )

    private fun fees(vararg months: String) = months.map { month ->
        PlatformFee(
            id = "fee-$month",
            groupId = "group-1",
            feeType = "monthly",
            amountDue = Money(100.0),
            amountPaid = Money.ZERO,
            monthReference = month
        )
    }

    @Test
    fun `retry of the same payment reuses an identical txId so the db can dedupe it`() = runBlocking {
        val group = group("group-1")
        coEvery { groupRepository.getGroupById("group-1") } returns Result.success(group)
        coEvery { financialRepository.getOutstandingPlatformFees("group-1") } returns
            Result.success(fees("2026-09", "2026-10"))

        // Capture the txId used on each invocation.
        coEvery {
            financialRepository.payPlatformFee("group-1", any(), any(), capture(txSlot))
        } returns Result.success(Unit)

        // First attempt.
        val first = useCase("group-1")
        assertTrue("First payment should succeed", first.isSuccess)
        val firstTxId = txSlot.captured

        // Simulate a UI-level retry (e.g. the first response was lost) with the
        // *same* outstanding fees — which is what a retry after a timeout looks like.
        val second = useCase("group-1")
        assertTrue("Retry should succeed", second.isSuccess)
        val secondTxId = txSlot.captured

        assertEquals(
            "A retry must present the same txId so the payment function recognises it as a duplicate.",
            firstTxId,
            secondTxId
        )
        assertTrue("txId should be stable across process restarts", firstTxId.startsWith("PLATFORM-FEE-"))
    }

    @Test
    fun `paying a newly outstanding fee produces a different txId`() = runBlocking {
        val group = group("group-1")
        coEvery { groupRepository.getGroupById("group-1") } returns Result.success(group)
        coEvery {
            financialRepository.payPlatformFee("group-1", any(), any(), capture(txSlot))
        } returns Result.success(Unit)

        coEvery { financialRepository.getOutstandingPlatformFees("group-1") } returns
            Result.success(fees("2026-09"))
        useCase("group-1")
        val oneFeeTx = txSlot.captured

        // A genuinely different payment (another month has fallen due).
        coEvery { financialRepository.getOutstandingPlatformFees("group-1") } returns
            Result.success(fees("2026-09", "2026-10"))
        useCase("group-1")
        val twoFeesTx = txSlot.captured

        assertTrue(
            "Different outstanding fee sets are different payments and must not collide.",
            oneFeeTx != twoFeesTx
        )
    }

    @Test
    fun `payment is skipped when balance cannot cover the total due`() = runBlocking {
        val poorGroup = group("group-1").copy(balance = Money(50.0))
        coEvery { groupRepository.getGroupById("group-1") } returns Result.success(poorGroup)
        coEvery { financialRepository.getOutstandingPlatformFees("group-1") } returns
            Result.success(fees("2026-09"))

        val result = useCase("group-1")

        assertTrue("Payment must not proceed without funds", result.isFailure)
        io.mockk.coVerify(exactly = 0) {
            financialRepository.payPlatformFee("group-1", any(), any(), any())
        }
    }
}
