package com.sanibonani.save.data.utils

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class PaymentCalculation(
    val shortfall: Double,
    val overpayment: Double,
    val nextDueDate: String,
    val totalDueNow: Double,
    val periodsAhead: Int = 0,
    val isOverdue: Boolean = false
)

fun Double.roundToTwoDecimals(): Double = (this * 100.0).roundToInt().toDouble() / 100.0

/**
 * Provides a system-synchronized current date.
 * This ensures all date calculations use the same reference point.
 */
object DateProvider {
    /**
     * Returns the current date from the system clock.
     * Always uses LocalDate.now() to get the most accurate current date.
     */
    fun getCurrentDate(): LocalDate = LocalDate.now()
}

object PaymentCalculator {

    fun calculateMonthlyContribution(group: Group, member: Member): Double {
        if (group.type != GroupType.BURIAL_SOCIETY) {
            return group.monthlyContribution.roundToTwoDecimals()
        }

        // Use override if available (manually set by admin)
        member.monthlyContributionOverride?.let { return it.roundToTwoDecimals() }

        val baseContribution = group.monthlyContribution
        val over65Count = member.beneficiaryOver65Count ?: 0
        val increasePct = (group.beneficiaryIncreasePct?.div(100.0)) ?: 0.0

        // Calculation: Base + (Base * increasePct * no_of_beneficiaries_over_65)
        val increase = baseContribution * increasePct * over65Count.toDouble()
        return (baseContribution + increase).roundToTwoDecimals()
    }

    fun calculateStatus(
        group: Group,
        member: Member,
        contributions: List<Contribution>,
        currentDate: LocalDate = DateProvider.getCurrentDate()
    ): PaymentCalculation {
        val monthlyAmount = calculateMonthlyContribution(group, member)
        if (monthlyAmount <= 0.0) {
            return PaymentCalculation(0.0, 0.0, "N/A", 0.0)
        }

        val joinedDate = try {
            val dateStr = member.joinedAt?.substringBefore("T") ?: currentDate.toString()
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            currentDate
        }

        val totalPaid = contributions
            .filter { it.status == ContributionStatus.PAID || it.status == ContributionStatus.PARTIAL }
            .sumOf { it.amount }

        // 1. Calculate total expected up to now
        var totalExpectedNow = 0.0
        var tempDate = joinedDate.withDayOfMonth(1)
        val endLimit = currentDate.withDayOfMonth(1)

        while (!tempDate.isAfter(endLimit)) {
             val dueDayThisMonth = try {
                tempDate.withDayOfMonth(group.paymentDueDay)
             } catch (_: Exception) {
                tempDate.withDayOfMonth(tempDate.lengthOfMonth())
            }

            if (!currentDate.isBefore(dueDayThisMonth)) {
                totalExpectedNow += monthlyAmount
            } else if (tempDate.isBefore(endLimit)) {
                totalExpectedNow += monthlyAmount
            }
            tempDate = tempDate.plusMonths(1)
        }

        val balance = (totalPaid - totalExpectedNow).roundToTwoDecimals()
        val shortfall = (if (balance < 0) -balance else 0.0).roundToTwoDecimals()
        val overpayment = (if (balance > 0) balance else 0.0).roundToTwoDecimals()
        
        // 2. Calculate the next due date (the first month that is not fully paid)
        val nextDueDate = calculateNextDueDate(group, joinedDate, monthlyAmount, totalPaid)

        // Overdue logic:
        // A member is overdue if they still have a shortfall AND the first unpaid due date
        // is past its grace period.
        val nextDueLocal = runCatching { LocalDate.parse(nextDueDate) }.getOrNull()
        val isCurrentlyOverdue = nextDueLocal
            ?.plusDays(group.lateFeeGraceDays.toLong())
            ?.let { graceEnd -> currentDate.isAfter(graceEnd) }
            ?: false

        // Late Fee logic
        val lateFeeToAdd = if (isCurrentlyOverdue && shortfall > 0) group.lateFee else 0.0
        val totalDueNow = (shortfall + lateFeeToAdd).roundToTwoDecimals()

        return PaymentCalculation(
            shortfall = shortfall,
            overpayment = overpayment,
            nextDueDate = nextDueDate,
            totalDueNow = totalDueNow,
            isOverdue = isCurrentlyOverdue && shortfall > 0
        )
    }

    /**
     * Finds the due date of the first month that is not yet fully covered by totalPaid.
     */
    fun calculateNextDueDate(
        group: Group,
        joinedDate: LocalDate,
        monthlyAmount: Double,
        totalPaid: Double
    ): String {
        var cumulativeExpected = 0.0
        var tempDate = joinedDate.withDayOfMonth(1)
        
        // We iterate until we find a month that isn't fully paid for
        // or we reach a reasonable future limit (e.g. 2 years ahead)
        val limit = DateProvider.getCurrentDate().plusYears(2)

        while (tempDate.isBefore(limit)) {
            cumulativeExpected += monthlyAmount
            if (cumulativeExpected > totalPaid + 0.01) { // 1 cent tolerance
                val dueDayThisMonth = try {
                    tempDate.withDayOfMonth(group.paymentDueDay)
                } catch (_: Exception) {
                    tempDate.withDayOfMonth(tempDate.lengthOfMonth())
                }
                return dueDayThisMonth.toString()
            }
            tempDate = tempDate.plusMonths(1)
        }
        return "N/A"
    }

    /**
     * Calculates the member's share of the total group fund.
     * Used for Investment Clubs to distribute dividends.
     */
    fun calculateMemberSharePercentage(groupBalance: Double, memberTotalPaid: Double): Double {
        if (groupBalance <= 0) return 0.0
        return (memberTotalPaid / groupBalance * 100.0).roundToTwoDecimals()
    }

    /**
     * Calculates exit penalty for Investment Clubs.
     * Standard rule: 10% penalty if exiting before lock-in period (e.g., 12 months).
     */
    fun calculateInvestmentExitPenalty(
        member: Member,
        amountToWithdraw: Double,
        currentDate: LocalDate = DateProvider.getCurrentDate()
    ): Double {
        val joinedDate = try {
            LocalDate.parse(member.joinedAt?.substringBefore("T"))
        } catch (_: Exception) {
            currentDate
        }

        val monthsMembership = ChronoUnit.MONTHS.between(joinedDate, currentDate)
        return if (monthsMembership < 12) {
            (amountToWithdraw * 0.10).roundToTwoDecimals()
        } else {
            0.0
        }
    }
    
    fun calculateRealtime(
        inputAmount: Double,
        currentShortfall: Double,
        currentOverpayment: Double,
        lateFee: Double,
        monthlyContribution: Double,
        group: Group,
        joinedDate: LocalDate,
        totalPaidBefore: Double
    ): Triple<Double, Double, String> {
        // Subtract late fee from input amount first if they are overdue
        val netAmountForBalance = (inputAmount - lateFee).coerceAtLeast(0.0)
        
        // New Balance = (Net Amount) + (Existing Overpayment) - (Existing Shortfall)
        val newBalance = (netAmountForBalance + currentOverpayment - currentShortfall).roundToTwoDecimals()
        
        val newShortfall = if (newBalance < 0) -newBalance else 0.0
        val newOverpayment = if (newBalance > 0) newBalance else 0.0
        
        val newTotalPaid = totalPaidBefore + netAmountForBalance
        val nextDate = calculateNextDueDate(group, joinedDate, monthlyContribution, newTotalPaid)
        
        return Triple(newShortfall.roundToTwoDecimals(), newOverpayment.roundToTwoDecimals(), nextDate)
    }

}
