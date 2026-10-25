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
        val dueDate = effectiveDueDate(calculation)
        val breakdown = calculation.breakdown
        val allocations = mutableListOf<PaymentAllocation>()

        var remaining = allocateFee(
            allocations, amount, breakdown.recoveryFee,
            type = "member_fee", description = "Shortfall Recovery Fee", dueDate = dueDate
        )
        remaining = allocateFee(
            allocations, remaining, breakdown.platformFees,
            type = "member_fee", description = "Monthly Platform Fee", dueDate = dueDate
        )
        remaining = allocateFee(
            allocations, remaining, breakdown.lateFees,
            type = "late_fee", description = "Late Fee Payment", dueDate = dueDate
        )
        remaining = allocateLoans(allocations, remaining, activeLoans, dueDate)

        if (remaining.isPositive) {
            allocations.add(PaymentAllocation(
                type = "contribution",
                amount = remaining,
                dueDate = dueDate,
                description = "Monthly Contribution"
            ))
        }

        return allocations
    }

    private fun allocateFee(
        allocations: MutableList<PaymentAllocation>,
        remaining: Money,
        fee: Money,
        type: String,
        description: String,
        dueDate: String
    ): Money {
        if (!remaining.isPositive || !fee.isPositive) return remaining

        val payAmount = remaining.min(fee)
        allocations.add(PaymentAllocation(
            type = type,
            amount = payAmount,
            dueDate = dueDate,
            description = description
        ))
        return remaining - payAmount
    }

    private fun allocateLoans(
        allocations: MutableList<PaymentAllocation>,
        amount: Money,
        activeLoans: List<Loan>,
        dueDate: String
    ): Money {
        var remaining = amount
        if (!remaining.isPositive || activeLoans.isEmpty()) return remaining

        for (loan in activeLoans.sortedBy { it.createdAt }) {
            if (remaining.isZero) break

            val outstanding = loan.totalToRepay - loan.totalRepaid
            if (outstanding.isPositive) {
                val payAmount = remaining.min(outstanding)
                allocations.add(PaymentAllocation(
                    type = "loan_repayment",
                    amount = payAmount,
                    loanId = loan.id,
                    dueDate = dueDate,
                    description = "Loan Repayment: ${loan.id?.take(5)}"
                ))
                remaining -= payAmount
            }
        }
        return remaining
    }

    private fun effectiveDueDate(calculation: PaymentCalculation): String =
        if (calculation.nextDueDate == "N/A" || calculation.nextDueDate.isBlank()) {
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        } else calculation.nextDueDate
}
