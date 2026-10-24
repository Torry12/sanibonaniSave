package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculation
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain service responsible for calculating the allocation of incoming funds
 * across various financial buckets (Fees, Loans, Contributions).
 */
@Singleton
class PaymentAllocationService @Inject constructor() {

    fun calculateAllocations(
        amount: Money,
        calculation: PaymentCalculation,
        activeLoans: List<Loan> = emptyList()
    ): List<PaymentAllocation> {
        var remainingAmount = amount
        val breakdown = calculation.breakdown
        val effectiveDueDate = if (calculation.nextDueDate == "N/A" || calculation.nextDueDate.isBlank()) {
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        } else calculation.nextDueDate

        val allocations = mutableListOf<PaymentAllocation>()

        // 1. Shortfall Recovery Fee
        if (remainingAmount.isPositive && breakdown.recoveryFee.isPositive) {
            val payAmount = remainingAmount.min(breakdown.recoveryFee)
            allocations.add(PaymentAllocation(
                type = "member_fee",
                amount = payAmount,
                dueDate = effectiveDueDate,
                description = "Shortfall Recovery Fee"
            ))
            remainingAmount -= payAmount
        }

        // 2. Monthly Platform Fees
        if (remainingAmount.isPositive && breakdown.platformFees.isPositive) {
            val payAmount = remainingAmount.min(breakdown.platformFees)
            allocations.add(PaymentAllocation(
                type = "member_fee",
                amount = payAmount,
                dueDate = effectiveDueDate,
                description = "Monthly Platform Fee"
            ))
            remainingAmount -= payAmount
        }

        // 3. Late Fees
        if (remainingAmount.isPositive && breakdown.lateFees.isPositive) {
            val payAmount = remainingAmount.min(breakdown.lateFees)
            allocations.add(PaymentAllocation(
                type = "late_fee",
                amount = payAmount,
                dueDate = effectiveDueDate,
                description = "Late Fee Payment"
            ))
            remainingAmount -= payAmount
        }

        // 4. Loans (Chronological order)
        if (remainingAmount.isPositive && activeLoans.isNotEmpty()) {
            activeLoans.sortedBy { it.createdAt }.forEach { loan ->
                if (remainingAmount.isZero) return@forEach
                val outstanding = loan.totalToRepay - loan.totalRepaid
                if (outstanding.isPositive) {
                    val payAmount = remainingAmount.min(outstanding)
                    allocations.add(PaymentAllocation(
                        type = "loan_repayment",
                        amount = payAmount,
                        loanId = loan.id,
                        dueDate = effectiveDueDate,
                        description = "Loan Repayment: ${loan.id?.take(5)}"
                    ))
                    remainingAmount -= payAmount
                }
            }
        }

        // 5. Monthly Contribution (Remaining Principal)
        if (remainingAmount.isPositive) {
            allocations.add(PaymentAllocation(
                type = "contribution",
                amount = remainingAmount,
                dueDate = effectiveDueDate,
                description = "Monthly Contribution"
            ))
        }

        return allocations
    }
}
