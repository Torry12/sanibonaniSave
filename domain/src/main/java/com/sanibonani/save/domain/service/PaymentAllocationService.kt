package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculation
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
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
    ): kotlinx.serialization.json.JsonArray {
        var remainingAmount = amount
        val breakdown = calculation.breakdown
        val effectiveDueDate = if (calculation.nextDueDate == "N/A" || calculation.nextDueDate.isBlank()) {
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        } else calculation.nextDueDate

        return buildJsonArray {
            // 1. Shortfall Recovery Fee
            if (remainingAmount.isPositive && breakdown.recoveryFee.isPositive) {
                val payAmount = remainingAmount.min(breakdown.recoveryFee)
                addJsonObject {
                    put("type", "member_fee")
                    put("amount", payAmount.amount.toPlainString())
                    put("due_date", effectiveDueDate)
                    put("description", "Shortfall Recovery Fee")
                }
                remainingAmount -= payAmount
            }

            // 2. Monthly Platform Fees
            if (remainingAmount.isPositive && breakdown.platformFees.isPositive) {
                val payAmount = remainingAmount.min(breakdown.platformFees)
                addJsonObject {
                    put("type", "member_fee")
                    put("amount", payAmount.amount.toPlainString())
                    put("due_date", effectiveDueDate)
                    put("description", "Monthly Platform Fee")
                }
                remainingAmount -= payAmount
            }

            // 3. Late Fees
            if (remainingAmount.isPositive && breakdown.lateFees.isPositive) {
                val payAmount = remainingAmount.min(breakdown.lateFees)
                addJsonObject {
                    put("type", "late_fee")
                    put("amount", payAmount.amount.toPlainString())
                    put("due_date", effectiveDueDate)
                    put("description", "Late Fee Payment")
                }
                remainingAmount -= payAmount
            }

            // 4. Loans (Chronological order)
            if (remainingAmount.isPositive && activeLoans.isNotEmpty()) {
                activeLoans.sortedBy { it.createdAt }.forEach { loan ->
                    if (remainingAmount.isZero) return@forEach
                    val outstanding = loan.totalToRepay - loan.totalRepaid
                    if (outstanding.isPositive) {
                        val payAmount = remainingAmount.min(outstanding)
                        addJsonObject {
                            put("type", "loan_repayment")
                            put("amount", payAmount.amount.toPlainString())
                            put("loan_id", loan.id)
                            put("due_date", effectiveDueDate)
                            put("description", "Loan Repayment: ${loan.id?.take(5)}")
                        }
                        remainingAmount -= payAmount
                    }
                }
            }

            // 5. Monthly Contribution (Remaining Principal)
            if (remainingAmount.isPositive) {
                addJsonObject {
                    put("type", "contribution")
                    put("amount", remainingAmount.amount.toPlainString())
                    put("due_date", effectiveDueDate)
                    put("description", "Monthly Contribution")
                }
            }
        }
    }
}
