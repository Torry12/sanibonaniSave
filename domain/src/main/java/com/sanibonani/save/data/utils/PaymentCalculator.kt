package com.sanibonani.save.data.utils

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.utils.roundMoneyToTwoDecimals
import com.sanibonani.save.domain.utils.toMoneyBigDecimal
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PaymentCalculation(
    val shortfall: Double,
    val overpayment: Double,
    val nextDueDate: String,
    val totalDueNow: Double,
    val periodsAhead: Int = 0,
    val isOverdue: Boolean = false
)

private val MONEY_ZERO: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
private val MONEY_CENT: BigDecimal = BigDecimal("0.01")
private val MONEY_HUNDRED: BigDecimal = BigDecimal("100")

private fun BigDecimal.money(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

private fun BigDecimal.moneyDouble(): Double = money().toDouble()

private fun Double.money(): BigDecimal = toMoneyBigDecimal()

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
            return group.monthlyContribution.roundMoneyToTwoDecimals()
        }

        // Use override if available (manually set by admin)
        member.monthlyContributionOverride?.let { return it.roundMoneyToTwoDecimals() }

        val baseContribution = group.monthlyContribution.money()
        val over65Count = member.beneficiaryOver65Count ?: 0
        val increasePct = group.beneficiaryIncreasePct
            ?.money()
            ?.divide(MONEY_HUNDRED, 6, RoundingMode.HALF_UP)
            ?: BigDecimal.ZERO

        // Calculation: Base + (Base * increasePct * no_of_beneficiaries_over_65)
        val increase = baseContribution
            .multiply(increasePct)
            .multiply(BigDecimal.valueOf(over65Count.toLong()))
        return baseContribution.add(increase).moneyDouble()
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
            .asSequence()
            .filter { it.status == ContributionStatus.PAID || it.status == ContributionStatus.PARTIAL }
            .map { it.amount.money() }
            .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }

        // 1. Calculate total expected up to now
        var totalExpectedNow = BigDecimal.ZERO
        var tempDate = joinedDate.withDayOfMonth(1)
        val endLimit = currentDate.withDayOfMonth(1)
        val monthlyAmountMoney = monthlyAmount.money()

        while (!tempDate.isAfter(endLimit)) {
             val dueDayThisMonth = try {
                tempDate.withDayOfMonth(group.paymentDueDay)
             } catch (_: Exception) {
                tempDate.withDayOfMonth(tempDate.lengthOfMonth())
            }

            if (!currentDate.isBefore(dueDayThisMonth)) {
                totalExpectedNow = totalExpectedNow.add(monthlyAmountMoney)
            } else if (tempDate.isBefore(endLimit)) {
                totalExpectedNow = totalExpectedNow.add(monthlyAmountMoney)
            }
            tempDate = tempDate.plusMonths(1)
        }

        val balance = totalPaid.subtract(totalExpectedNow).money()
        val shortfall = if (balance.signum() < 0) balance.abs().moneyDouble() else MONEY_ZERO.moneyDouble()
        val overpayment = if (balance.signum() > 0) balance.moneyDouble() else MONEY_ZERO.moneyDouble()

        // 2. Calculate the next due date (the first month that is not fully paid)
        val nextDueDate = calculateNextDueDate(group, joinedDate, monthlyAmount, totalPaid.moneyDouble())

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
        val totalDueNow = (shortfall + lateFeeToAdd).roundMoneyToTwoDecimals()

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
        var cumulativeExpected = BigDecimal.ZERO
        var tempDate = joinedDate.withDayOfMonth(1)
        val monthlyAmountMoney = monthlyAmount.money()
        val totalPaidMoney = totalPaid.money()

        // We iterate until we find a month that isn't fully paid for
        // or we reach a reasonable future limit (e.g. 2 years ahead)
        val limit = DateProvider.getCurrentDate().plusYears(2)

        while (tempDate.isBefore(limit)) {
            cumulativeExpected = cumulativeExpected.add(monthlyAmountMoney)
            if (cumulativeExpected > totalPaidMoney.add(MONEY_CENT)) {
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
        val share = memberTotalPaid.money().divide(groupBalance.money(), 6, RoundingMode.HALF_UP)
            .multiply(MONEY_HUNDRED)
        return share.moneyDouble()
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
            val dateStr = member.joinedAt?.substringBefore("T") ?: currentDate.toString()
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            currentDate
        }

        val monthsMembership = ChronoUnit.MONTHS.between(joinedDate, currentDate)
        return if (monthsMembership < 12) {
            amountToWithdraw.money().multiply(BigDecimal("0.10")).moneyDouble()
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
        val netAmountForBalance = (inputAmount.money() - lateFee.money()).max(MONEY_ZERO).moneyDouble()

        // New Balance = (Net Amount) + (Existing Overpayment) - (Existing Shortfall)
        val newBalance = (netAmountForBalance.money() + currentOverpayment.money() - currentShortfall.money()).moneyDouble()

        val newShortfall = if (newBalance < 0) -newBalance else 0.0
        val newOverpayment = if (newBalance > 0) newBalance else 0.0
        
        val newTotalPaid = totalPaidBefore.money().add(netAmountForBalance.money()).moneyDouble()
        val nextDate = calculateNextDueDate(group, joinedDate, monthlyContribution, newTotalPaid)
        
        return Triple(newShortfall.roundMoneyToTwoDecimals(), newOverpayment.roundMoneyToTwoDecimals(), nextDate)
    }

}
