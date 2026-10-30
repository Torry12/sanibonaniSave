package com.sanibonani.save.domain.utils

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.service.GroupFinancialPolicy
import com.sanibonani.save.domain.service.PlatformFeeService
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

data class DueBreakdown(
    val contributions: Money = Money.ZERO,
    val loans: Money = Money.ZERO,
    val lateFees: Money = Money.ZERO,
    val platformFees: Money = Money.ZERO,
    val recoveryFee: Money = Money.ZERO
) {
    val total: Money get() = contributions + loans + lateFees + platformFees + recoveryFee
    val hasDebt: Boolean get() = total.isPositive
}

data class PaymentCalculation(
    val shortfall: Money,
    val overpayment: Money,
    val nextDueDate: String,
    val totalDueNow: Money,
    val paymentRate: Double = 100.0,
    val periodsAhead: Int = 0,
    val isOverdue: Boolean = false,
    val breakdown: DueBreakdown = DueBreakdown(),
    val totalOverdue: Money = totalDueNow,
    val nextContributionAmount: Money = Money.ZERO
) {
    companion object {
        val EMPTY = PaymentCalculation(Money.ZERO, Money.ZERO, "N/A", Money.ZERO)
    }
}

/**
 * Provides a system-synchronized current date.
 */
object DateProvider {
    fun getCurrentDate(): LocalDate = LocalDate.now()
}

data class RealtimePaymentPreview(
    val shortfall: Money,
    val overpayment: Money,
    val nextDueDate: String,
)

@Singleton
class PaymentCalculator @Inject constructor(
    private val feeService: PlatformFeeService,
    private val policy: GroupFinancialPolicy
) {

    /**
     * Calculates the total expected monthly outflow for a member.
     * Includes Group Contribution + Platform Member Fee + Surcharges.
     */
    fun calculateExpectedMonthlyOutflow(group: Group, member: Member): Money {
        val groupPart = calculateExpectedMemberGroupPart(group, member)
        val platformPart = feeService.calculateMonthlyMemberIndividual(group, member)
        return groupPart + platformPart
    }

    /**
     * Calculates ONLY the group's portion of the contribution (excluding platform fee).
     */
    fun calculateExpectedMemberGroupPart(group: Group, member: Member): Money {
        return policy.expectedMemberContribution(group, member)
    }

    /**
     * Projects the member payout based on group type and current status.
     */
    fun projectPayout(group: Group, members: List<Member>, member: Member? = null): Money {
        return policy.projectedPayout(group, members, member)
    }

    fun calculateStatus(
        group: Group,
        member: Member,
        contributions: List<Contribution>,
        loans: List<Loan> = emptyList(),
        currentDate: LocalDate = DateProvider.getCurrentDate()
    ): PaymentCalculation {
        val expectedSavingsPerMonth = calculateExpectedMemberGroupPart(group, member)
        val platformFeePerMonth = feeService.calculateMonthlyMemberIndividual(group, member)

        if (expectedSavingsPerMonth.isZero && loans.isEmpty() && platformFeePerMonth.isZero) {
            return PaymentCalculation(Money.ZERO, Money.ZERO, "N/A", Money.ZERO)
        }

        val joinedDate = resolveJoinedDate(member, currentDate, contributions)

        // 1. Calculate Totals Paid by Category
        val paidContributions = contributions.filter { it.status == ContributionStatus.PAID || it.status == ContributionStatus.PARTIAL }
        val totalPaidSavings = calculatePaidSavings(paidContributions, member)
        val totalPaidPlatform = paidPlatformFees(paidContributions)
        val servicingLoans = loans.filter { it.isBeingRepaid() }

        // 2. Calculate expectations and fees up to now
        val expectations = calculateDetailedExpectations(
            group = group,
            loans = servicingLoans,
            joinedDate = joinedDate,
            currentDate = currentDate,
            totalPaidSavings = totalPaidSavings,
            expectedSavingsPerMonth = expectedSavingsPerMonth,
            platformFeePerMonth = platformFeePerMonth
        )
        val totalExpectedSavings = expectations.expectedSavings
        val totalExpectedPlatform = expectations.expectedPlatform
        val loanShortfall = expectations.loanShortfall
        val totalLateFees = expectations.totalLateFees
        val isCurrentlyOverdue = expectations.isCurrentlyOverdue

        // 3. Category-specific shortfall calculation
        val savingsShortfall = (totalExpectedSavings - totalPaidSavings).max(Money.ZERO)
        val platformShortfall = (totalExpectedPlatform - totalPaidPlatform).max(Money.ZERO)
        // Savings overpayment (credit)
        val savingsBalance = totalPaidSavings - totalExpectedSavings
        val overpayment = if (savingsBalance.isPositive) savingsBalance else Money.ZERO

        val shortfallRecoveryFee = if (totalLateFees.isPositive || (savingsShortfall.isPositive && isCurrentlyOverdue)) {
            feeService.getShortfallRecoveryFee()
        } else Money.ZERO

        val breakdown = DueBreakdown(
            contributions = savingsShortfall,
            loans = loanShortfall,
            lateFees = totalLateFees,
            platformFees = platformShortfall,
            recoveryFee = shortfallRecoveryFee
        )

        val totalDueNow = breakdown.total

        // 4. Payment Rate
        val totalExpectedUpToNow = (totalPaidSavings + savingsShortfall - overpayment).max(Money.ZERO)
        val paymentRate = if (totalExpectedUpToNow.isPositive) {
            val rate = totalPaidSavings.ratio(totalExpectedUpToNow) * 100.0
            rate.coerceIn(0.0, 100.0)
        } else 100.0

        val loanMonthlyTotal = servicingLoans.fold(Money.ZERO) { acc, l -> acc + l.monthlyRepayment }
        val combinedMonthly = expectedSavingsPerMonth + loanMonthlyTotal

        val periodsAhead = if (combinedMonthly.isPositive && overpayment.isPositive) {
            overpayment.amount.divide(combinedMonthly.amount, 0, RoundingMode.DOWN).toInt()
        } else 0

        val nextDueDate = calculateNextDueDate(group, joinedDate, combinedMonthly, totalPaidSavings)

        return PaymentCalculation(
            shortfall = savingsShortfall,
            overpayment = overpayment,
            nextDueDate = nextDueDate,
            totalDueNow = totalDueNow,
            paymentRate = paymentRate,
            periodsAhead = periodsAhead,
            isOverdue = isCurrentlyOverdue,
            breakdown = breakdown,
            nextContributionAmount = expectedSavingsPerMonth
        )
    }

    private data class DetailedExpectations(
        val expectedSavings: Money,
        val expectedPlatform: Money,
        val loanShortfall: Money,
        val totalLateFees: Money,
        val isCurrentlyOverdue: Boolean
    )

    private fun calculateDetailedExpectations(
        group: Group,
        loans: List<Loan>,
        joinedDate: LocalDate,
        currentDate: LocalDate,
        totalPaidSavings: Money,
        expectedSavingsPerMonth: Money,
        platformFeePerMonth: Money
    ): DetailedExpectations {
        var totalExpectedSavings = Money.ZERO
        var totalExpectedPlatform = Money.ZERO
        val expectedLoanAmounts = MutableList(loans.size) { Money.ZERO }
        var totalLateFees = Money.ZERO
        var isCurrentlyOverdue = false

        var tempDate = joinedDate.withDayOfMonth(1)
        val endLimit = currentDate.withDayOfMonth(1)

        while (!tempDate.isAfter(endLimit)) {
            val dueDayThisMonth = dueDateIn(tempDate, group.paymentDueDay)

            if (!currentDate.isBefore(dueDayThisMonth) || tempDate.isBefore(endLimit)) {
                totalExpectedSavings += expectedSavingsPerMonth
                totalExpectedPlatform += platformFeePerMonth

                for ((index, loan) in loans.withIndex()) {
                    val loanStartDate = loan.startDate?.let { safeRunCatching { LocalDate.parse(it.substringBefore("T")) }.getOrNull() }
                    val loanEndDate = loan.endDate?.let { safeRunCatching { LocalDate.parse(it.substringBefore("T")) }.getOrNull() }

                    if (loanStartDate != null) {
                        val loanStartMonth = loanStartDate.withDayOfMonth(1)
                        if (!tempDate.isBefore(loanStartMonth)) {
                            val effectiveEndDate = loanEndDate ?: loanStartDate.plusMonths(12)
                            val isPastTerm = tempDate.isAfter(effectiveEndDate.withDayOfMonth(1))
                            if (!isPastTerm) {
                                expectedLoanAmounts[index] += loan.monthlyRepayment
                            }
                        }
                    }
                }

                val graceEnd = dueDayThisMonth.plusDays(group.lateFeeGraceDays.toLong())
                if (currentDate.isAfter(graceEnd)) {
                    if (totalPaidSavings < totalExpectedSavings) {
                        totalLateFees += group.lateFee
                        isCurrentlyOverdue = true
                    }
                }
            }
            tempDate = tempDate.plusMonths(1)
        }

        val loanShortfall = loans.indices.fold(Money.ZERO) { total, index ->
            total + (expectedLoanAmounts[index] - loans[index].totalRepaid).max(Money.ZERO)
        }

        return DetailedExpectations(totalExpectedSavings, totalExpectedPlatform, loanShortfall, totalLateFees, isCurrentlyOverdue)
    }

    fun calculateNextDueDate(
        group: Group,
        joinedDate: LocalDate,
        monthlyAmount: Money,
        totalPaid: Money
    ): String {
        val joinedMonth = joinedDate.withDayOfMonth(1)
        if (!monthlyAmount.isPositive) {
            return if (totalPaid.isNegative) dueDateIn(joinedMonth, group.paymentDueDay).toString() else "N/A"
        }
        // First month whose cumulative expectation exceeds what has been paid.
        val monthsCovered = totalPaid.amount
            .divide(monthlyAmount.amount, 0, RoundingMode.DOWN)
            .toLong()
            .coerceIn(0L, MAX_MONTHS_AHEAD)
        return dueDateIn(joinedMonth.plusMonths(monthsCovered), group.paymentDueDay).toString()
    }

    fun calculateRealtime(
        inputAmount: Money,
        currentShortfall: Money,
        currentOverpayment: Money,
        lateFee: Money,
        monthlyContribution: Money,
        group: Group,
        joinedDate: LocalDate,
        totalPaidBefore: Money
    ): RealtimePaymentPreview {
        if (inputAmount.isZero || inputAmount.isNegative) {
            return RealtimePaymentPreview(currentShortfall, currentOverpayment, calculateNextDueDate(group, joinedDate, monthlyContribution, totalPaidBefore))
        }

        val balanceAdjustment = inputAmount - lateFee
        val netAmountForBalance = if (balanceAdjustment.isNegative) Money.ZERO else balanceAdjustment
        val newBalance = netAmountForBalance + currentOverpayment - currentShortfall

        val newShortfall = if (newBalance.isNegative) newBalance.abs() else Money.ZERO
        val newOverpayment = if (newBalance.isPositive) newBalance else Money.ZERO

        val newTotalPaid = totalPaidBefore + netAmountForBalance
        val nextDate = calculateNextDueDate(group, joinedDate, monthlyContribution, newTotalPaid)

        return RealtimePaymentPreview(newShortfall, newOverpayment, nextDate)
    }

    fun calculatePaidSavings(paidContributions: List<Contribution>, member: Member): Money {
        // Note: For loan_settlement_extraction, the amount is negative so it reduces the total paid savings.
        val calculated = paidContributions
            .filter { it.type == "contribution" || it.type == "registration_contribution" || it.type == "loan_settlement_extraction" }
            .fold(Money.ZERO) { acc, c -> acc + c.amount }
        return if (calculated.isZero && member.totalPaid.isPositive) member.totalPaid else calculated
    }

    private fun paidPlatformFees(paidContributions: List<Contribution>): Money =
        paidContributions
            .filter { it.type == "member_fee" || it.type == "platform_fee" }
            .fold(Money.ZERO) { acc, c -> acc + c.amount }

    private fun Loan.isBeingRepaid(): Boolean =
        status == LoanStatus.ACTIVE || status == LoanStatus.PARTIALLY_PAID

    private fun dueDateIn(month: LocalDate, dueDay: Int): LocalDate =
        try {
            month.withDayOfMonth(dueDay)
        } catch (_: Exception) {
            month.withDayOfMonth(month.lengthOfMonth())
        }

    fun resolveJoinedDate(member: Member, fallback: LocalDate, contributions: List<Contribution> = emptyList()): LocalDate {
        return try {
            val rawStr = member.joinedAt ?: member.createdAt
            val parsedJoined = safeRunCatching {
                if (!rawStr.isNullOrBlank()) {
                    LocalDate.parse(rawStr.substringBefore("T").substringBefore(" "))
                } else null
            }.getOrNull()

            val earliestContrib = contributions.mapNotNull {
                safeRunCatching { LocalDate.parse(it.dueDate.substringBefore("T")) }.getOrNull()
            }.minOrNull()

            val effectiveDate = listOfNotNull(parsedJoined, earliestContrib)
                .filter { !it.isAfter(fallback) }
                .maxOrNull()
                ?: parsedJoined
                ?: earliestContrib
                ?: fallback

            effectiveDate.withDayOfMonth(1)
        } catch (_: Exception) {
            fallback.withDayOfMonth(1)
        }
    }

    private companion object {
        // Keeps LocalDate arithmetic in range for absurd inputs (cent-level amounts paid far ahead).
        const val MAX_MONTHS_AHEAD = 120_000L
    }
}
