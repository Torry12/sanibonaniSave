package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.utils.PaymentCalculation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberActuarialService @Inject constructor() {

    /**
     * Determines if a member is currently in good standing based on their status
     * and presence of any overdue contributions.
     */
    fun calculateStanding(member: Member?, calculation: PaymentCalculation?): Boolean {
        if (member == null) return false
        if (member.status != MemberStatus.ACTIVE) return false

        // If calculation is missing, we assume good standing until data arrives
        // or we check the totalPaid vs expected.
        val hasOverdue = calculation?.totalOverdue?.isPositive ?: false
        return !hasOverdue
    }

    /**
     * Formats a user-friendly string explaining the next financial action required.
     */
    fun getNextActionMessage(calculation: PaymentCalculation?): String {
        val calc = calculation ?: return "Checking status..."

        if (calc.totalOverdue.isPositive) {
            return "Action Required: Overdue ${calc.totalOverdue.format()}"
        }

        return "Next: ${calc.nextContributionAmount.format()} due on ${calc.nextDueDate}"
    }

    /**
     * Scores payment consistency from 0-100 based on contribution history.
     */
    fun calculateConsistencyScore(contributions: List<Contribution>): Int {
        if (contributions.isEmpty()) return 100 // New members start perfect

        val total = contributions.size
        val onTime = contributions.count { it.status == ContributionStatus.PAID && it.paidAt != null }

        return ((onTime.toDouble() / total.toDouble()) * 100).toInt()
    }
}
