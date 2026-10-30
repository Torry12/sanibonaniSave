package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.InsufficientFundsException
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutType
import com.sanibonani.save.domain.model.WithdrawalLimitExceededException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the treasury guards every payout request passes through before it is queued.
 *
 * The emergency cap is the only payout-kind-specific amount limit: a single emergency withdrawal
 * releases at most half the live treasury balance, so one request cannot drain the fund even when
 * the plain solvency check would allow it. Claims and ordinary payouts stay bounded by the balance
 * alone.
 */
class PayoutGuardTest {

    private val treasuryService: TreasuryService = mockk()
    private val guard = PayoutGuardImpl(treasuryService, GroupFinancialPolicy())

    private fun group(
        type: GroupType = GroupType.EMERGENCY_FUND,
        balance: String = "1000.00"
    ) = Group(id = "g1", name = "Test Group", type = type, balance = Money(balance))

    private val member = Member(id = "m1", groupId = "g1", status = MemberStatus.ACTIVE)

    private fun stubTreasury(balance: String, reserve: String = "0.00") {
        coEvery { treasuryService.getTreasuryBalance("g1") } returns Result.success(Money(balance))
        coEvery { treasuryService.calculateRequiredReserve("g1") } returns Result.success(Money(reserve))
    }

    private fun validate(amount: String, type: PayoutType, group: Group = group()): Result<Unit> =
        runBlocking { guard.validateRequest(group, member, Money(amount), type) }

    @Test
    fun `an emergency withdrawal of exactly half the balance passes`() {
        stubTreasury("1000.00")

        assertTrue(validate("500.00", PayoutType.EMERGENCY).isSuccess)
    }

    @Test
    fun `an emergency withdrawal above half the balance is rejected`() {
        stubTreasury("1000.00")

        val result = validate("500.01", PayoutType.EMERGENCY)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("expected a withdrawal limit error, got $error", error is WithdrawalLimitExceededException)
        error as WithdrawalLimitExceededException
        assertEquals(Money("500.01"), error.requested)
        assertEquals(Money("500.00"), error.limit)
        assertTrue("message names the 50% cap: ${error.message}", error.message.orEmpty().contains("50%"))
    }

    @Test
    fun `the cap is measured on the live treasury balance`() {
        // The cached group holds R1,000 while the ledger holds R2,000: R900 must pass here and
        // would fail against the cached balance's R500 cap.
        stubTreasury("2000.00")

        assertTrue(validate("900.00", PayoutType.EMERGENCY).isSuccess)
    }

    @Test
    fun `an amount above the balance is still reported as insufficient funds`() {
        stubTreasury("1000.00")

        val error = validate("1200.00", PayoutType.EMERGENCY).exceptionOrNull()

        assertTrue("expected insufficient funds, got $error", error is InsufficientFundsException)
    }

    @Test
    fun `claims are not capped`() {
        stubTreasury("1000.00")

        assertTrue(validate("1000.00", PayoutType.CLAIM, group(GroupType.BURIAL_SOCIETY)).isSuccess)
    }

    @Test
    fun `ordinary payouts are not capped`() {
        stubTreasury("1000.00")

        assertTrue(validate("1000.00", PayoutType.ANNUAL, group(GroupType.STOKVEL)).isSuccess)
    }

    @Test
    fun `the emergency exemption from the reserve floor still holds`() {
        // The cap, not the reserve floor, is the emergency bound: half the fund is releasable even
        // when the remaining balance would sit below the required reserve.
        stubTreasury("1000.00", reserve = "900.00")

        assertTrue(validate("500.00", PayoutType.EMERGENCY).isSuccess)
    }
}
