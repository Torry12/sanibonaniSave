package com.sanibonani.save.data.utils

import com.sanibonani.save.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Comprehensive tests for PaymentCalculator business logic.
 * Tests shortfall, overpayment, late fees, beneficiary adjustments, and edge cases.
 */
class PaymentCalculatorTest {

    // ══════════════════════════════════════════════════════════════════════════
    // TEST FIXTURES
    // ══════════════════════════════════════════════════════════════════════════

    private val burialGroup = Group(
        id = "burial-1",
        name = "Test Burial Society",
        type = GroupType.BURIAL_SOCIETY,
        monthlyContribution = 150.0,
        lateFee = 50.0,
        lateFeeGraceDays = 5,
        paymentDueDay = 28,
        beneficiaryIncreasePct = 10.0,
        maxBeneficiaries = 5
    )

    private val stokvelGroup = Group(
        id = "stokvel-1",
        name = "Test Stokvel",
        type = GroupType.STOKVEL,
        monthlyContribution = 500.0,
        lateFee = 25.0,
        lateFeeGraceDays = 7,
        paymentDueDay = 15
    )

    // ══════════════════════════════════════════════════════════════════════════
    // MONTHLY CONTRIBUTION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `monthly contribution - stokvel uses base rate`() {
        val member = Member(
            id = "m1",
            groupId = stokvelGroup.id!!,
            beneficiaryOver65Count = 5 // Should be ignored for stokvel
        )

        val amount = PaymentCalculator.calculateMonthlyContribution(stokvelGroup, member)

        assertEquals("Stokvel should use base contribution", 500.0, amount, 0.01)
    }

    @Test
    fun `monthly contribution - burial society with no over-65 beneficiaries`() {
        val member = Member(
            id = "m1",
            groupId = burialGroup.id!!,
            beneficiaryOver65Count = 0
        )

        val amount = PaymentCalculator.calculateMonthlyContribution(burialGroup, member)

        assertEquals("Should be base amount", 150.0, amount, 0.01)
    }

    @Test
    fun `monthly contribution - burial society with over-65 beneficiaries`() {
        val member = Member(
            id = "m1",
            groupId = burialGroup.id!!,
            beneficiaryOver65Count = 2
        )

        val amount = PaymentCalculator.calculateMonthlyContribution(burialGroup, member)

        // Base 150 + (150 * 0.10 * 2) = 150 + 30 = 180
        assertEquals("Should include 10% increase per over-65", 180.0, amount, 0.01)
    }

    @Test
    fun `monthly contribution - manual override takes precedence`() {
        val member = Member(
            id = "m1",
            groupId = burialGroup.id!!,
            beneficiaryOver65Count = 5,
            monthlyContributionOverride = 100.0 // Admin set a lower rate
        )

        val amount = PaymentCalculator.calculateMonthlyContribution(burialGroup, member)

        assertEquals("Override should take precedence", 100.0, amount, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SHORTFALL TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `shortfall - new member first month no payment`() {
        val joinDate = LocalDate.of(2024, 1, 1)
        val currentDate = LocalDate.of(2024, 1, 28) // Due day

        val member = Member(
            id = "m1",
            groupId = stokvelGroup.id!!,
            joinedAt = "${joinDate}T00:00:00Z"
        )

        val status = PaymentCalculator.calculateStatus(stokvelGroup, member, emptyList(), currentDate)

        assertEquals("Should owe one month", 500.0, status.shortfall, 0.01)
    }

    @Test
    fun `shortfall - member two months behind`() {
        val joinDate = LocalDate.of(2023, 12, 1)
        val currentDate = LocalDate.of(2024, 2, 15) // On due day month 2

        val member = Member(
            id = "m1",
            groupId = stokvelGroup.id!!,
            joinedAt = "${joinDate}T00:00:00Z"
        )

        val status = PaymentCalculator.calculateStatus(stokvelGroup, member, emptyList(), currentDate)

        // Dec + Jan + Feb (on due day) = 1500
        assertEquals("Should owe three months", 1500.0, status.shortfall, 0.01)
    }

    @Test
    fun `shortfall - partial payment reduces shortfall`() {
        val joinDate = LocalDate.of(2024, 1, 1)
        val currentDate = LocalDate.of(2024, 1, 28)

        val member = Member(
            id = "m1",
            groupId = stokvelGroup.id!!,
            joinedAt = "${joinDate}T00:00:00Z"
        )

        val contributions = listOf(
            Contribution(
                memberId = "m1",
                groupId = stokvelGroup.id!!,
                amount = 200.0,
                status = ContributionStatus.PARTIAL,
                dueDate = "2024-01-15",
                paidAt = "2024-01-10T00:00:00Z"
            )
        )

        val status = PaymentCalculator.calculateStatus(stokvelGroup, member, contributions, currentDate)

        assertEquals("Shortfall should be reduced by payment", 300.0, status.shortfall, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OVERPAYMENT TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `overpayment - extra payment creates credit`() {
        val joinDate = LocalDate.of(2024, 1, 1)
        val currentDate = LocalDate.of(2024, 1, 28)

        val member = Member(
            id = "m1",
            groupId = stokvelGroup.id!!,
            joinedAt = "${joinDate}T00:00:00Z"
        )

        val contributions = listOf(
            Contribution(
                memberId = "m1",
                groupId = stokvelGroup.id!!,
                amount = 750.0, // 500 due + 250 extra
                status = ContributionStatus.PAID,
                dueDate = "2024-01-15",
                paidAt = "2024-01-10T00:00:00Z"
            )
        )

        val status = PaymentCalculator.calculateStatus(stokvelGroup, member, contributions, currentDate)

        assertEquals("Should have no shortfall", 0.0, status.shortfall, 0.01)
        assertEquals("Should have overpayment", 250.0, status.overpayment, 0.01)
    }

    @Test
    fun `overpayment - advances next due date`() {
        val joinDate = LocalDate.of(2024, 1, 1)
        val currentDate = LocalDate.of(2024, 1, 28)

        val member = Member(
            id = "m1",
            groupId = stokvelGroup.id!!,
            joinedAt = "${joinDate}T00:00:00Z"
        )

        // Pay for Jan + Feb
        val contributions = listOf(
            Contribution(
                memberId = "m1",
                groupId = stokvelGroup.id!!,
                amount = 1000.0,
                status = ContributionStatus.PAID,
                dueDate = "2024-01-15",
                paidAt = "2024-01-05T00:00:00Z"
            )
        )

        val status = PaymentCalculator.calculateStatus(stokvelGroup, member, contributions, currentDate)

        // Next due should be March (since Jan and Feb are covered)
        assertEquals("Next due should be March", "2024-03-15", status.nextDueDate)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LATE FEE TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `late fee - not applied within grace period`() {
        val joinDate = LocalDate.of(2024, 1, 1)
        // Due day is 28th, grace period is 5 days, so no late fee until Feb 3
        val currentDate = LocalDate.of(2024, 2, 2) // Within grace period

        val member = Member(
            id = "m1",
            groupId = burialGroup.id!!,
            joinedAt = "${joinDate}T00:00:00Z"
        )

        val status = PaymentCalculator.calculateStatus(burialGroup, member, emptyList(), currentDate)

        // Should owe Jan + Feb, but Feb not due yet (due Feb 28)
        // So shortfall is just Jan = 150
        // Not overdue yet because we're not past grace period for Jan
        assertEquals("Should not include late fee within grace", status.shortfall, status.totalDueNow, 0.01)
    }

    @Test
    fun `late fee - applied after grace period`() {
        val joinDate = LocalDate.of(2024, 1, 1)
        // Due day 28th, grace 5 days = Feb 2, test on Feb 5
        val currentDate = LocalDate.of(2024, 2, 5)

        val member = Member(
            id = "m1",
            groupId = burialGroup.id!!,
            joinedAt = "${joinDate}T00:00:00Z"
        )

        val status = PaymentCalculator.calculateStatus(burialGroup, member, emptyList(), currentDate)

        // Shortfall: Jan = 150
        // Should be overdue, so totalDueNow includes late fee
        assertTrue("Should be marked as overdue", status.isOverdue)
        // But totalDueNow may include late fee only if overdue
        if (status.isOverdue && status.shortfall > 0) {
            assertEquals("Total due should include late fee", status.shortfall + burialGroup.lateFee, status.totalDueNow, 0.01)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REALTIME CALCULATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `realtime - payment clears shortfall`() {
        val (newShortfall, newOverpayment, _) = PaymentCalculator.calculateRealtime(
            inputAmount = 500.0,
            currentShortfall = 500.0,
            currentOverpayment = 0.0,
            lateFee = 0.0,
            monthlyContribution = 500.0,
            group = stokvelGroup,
            joinedDate = LocalDate.of(2024, 1, 1),
            totalPaidBefore = 0.0
        )

        assertEquals("Shortfall should be cleared", 0.0, newShortfall, 0.01)
        assertEquals("No overpayment", 0.0, newOverpayment, 0.01)
    }

    @Test
    fun `realtime - payment with late fee deduction`() {
        val (newShortfall, newOverpayment, _) = PaymentCalculator.calculateRealtime(
            inputAmount = 550.0, // 500 contribution + 50 late fee
            currentShortfall = 500.0,
            currentOverpayment = 0.0,
            lateFee = 50.0,
            monthlyContribution = 500.0,
            group = stokvelGroup,
            joinedDate = LocalDate.of(2024, 1, 1),
            totalPaidBefore = 0.0
        )

        // 550 - 50 late fee = 500 net, which clears 500 shortfall
        assertEquals("Shortfall should be cleared", 0.0, newShortfall, 0.01)
        assertEquals("No overpayment after late fee", 0.0, newOverpayment, 0.01)
    }

    @Test
    fun `realtime - partial payment reduces shortfall`() {
        val (newShortfall, newOverpayment, _) = PaymentCalculator.calculateRealtime(
            inputAmount = 300.0,
            currentShortfall = 500.0,
            currentOverpayment = 0.0,
            lateFee = 0.0,
            monthlyContribution = 500.0,
            group = stokvelGroup,
            joinedDate = LocalDate.of(2024, 1, 1),
            totalPaidBefore = 0.0
        )

        assertEquals("Shortfall should be reduced", 200.0, newShortfall, 0.01)
        assertEquals("No overpayment", 0.0, newOverpayment, 0.01)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EDGE CASE TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `edge case - zero monthly contribution`() {
        val zeroGroup = Group(
            id = "zero-1",
            monthlyContribution = 0.0,
            type = GroupType.OTHER
        )

        val member = Member(id = "m1", groupId = "zero-1")

        val status = PaymentCalculator.calculateStatus(zeroGroup, member, emptyList())

        assertEquals("Should have no shortfall", 0.0, status.shortfall, 0.01)
        assertEquals("Next due should be N/A", "N/A", status.nextDueDate)
    }

    @Test
    fun `edge case - payment due day on 31st for short month`() {
        val endOfMonthGroup = Group(
            id = "eom-1",
            monthlyContribution = 100.0,
            paymentDueDay = 31, // Will adjust for Feb
            type = GroupType.STOKVEL
        )

        val member = Member(
            id = "m1",
            groupId = "eom-1",
            joinedAt = "2024-02-01T00:00:00Z"
        )

        // Feb 2024 has 29 days (leap year)
        val currentDate = LocalDate.of(2024, 2, 29)

        val status = PaymentCalculator.calculateStatus(endOfMonthGroup, member, emptyList(), currentDate)

        // Should handle gracefully without exception
        assertTrue("Shortfall should be positive or zero", status.shortfall >= 0.0)
    }

    @Test
    fun `edge case - multiple small payments sum correctly`() {
        val joinDate = LocalDate.of(2024, 1, 1)
        val currentDate = LocalDate.of(2024, 1, 28)

        val member = Member(
            id = "m1",
            groupId = stokvelGroup.id!!,
            joinedAt = "${joinDate}T00:00:00Z"
        )

        // Multiple partial payments totaling exactly the due amount
        val contributions = listOf(
            Contribution(memberId = "m1", groupId = stokvelGroup.id!!, amount = 100.0, status = ContributionStatus.PARTIAL, dueDate = "2024-01-15"),
            Contribution(memberId = "m1", groupId = stokvelGroup.id!!, amount = 150.0, status = ContributionStatus.PARTIAL, dueDate = "2024-01-15"),
            Contribution(memberId = "m1", groupId = stokvelGroup.id!!, amount = 250.0, status = ContributionStatus.PAID, dueDate = "2024-01-15")
        )

        val status = PaymentCalculator.calculateStatus(stokvelGroup, member, contributions, currentDate)

        assertEquals("All payments should be summed correctly", 0.0, status.shortfall, 0.01)
    }

    @Test
    fun `roundToTwoDecimals - handles floating point correctly`() {
        val value = 123.456789
        val rounded = value.roundToTwoDecimals()

        assertEquals("Should round to two decimals", 123.46, rounded, 0.001)
    }
}

