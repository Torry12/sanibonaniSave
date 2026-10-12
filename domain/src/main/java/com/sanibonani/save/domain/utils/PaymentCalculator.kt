package com.sanibonani.save.domain.utils

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.service.PlatformFeeService
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

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
    private val feeService: PlatformFeeService
) {

    private val strategies = mapOf(
        GroupType.BURIAL_SOCIETY to BurialSocietyAccounting(),
        GroupType.INVESTMENT_CLUB to InvestmentClubAccounting(),
        GroupType.STOKVEL to StokvelAccounting(),
        GroupType.ROSCA to RoscaAccounting()
    )

    private fun getStrategy(type: GroupType): AccountingStrategy {
        return strategies[type] ?: DefaultAccounting()
    }

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
        return getStrategy(group.type).calculateExpectedMemberGroupPart(group, member)
    }

    /**
     * Projects the member payout based on group type and current status.
     */
    fun projectPayout(group: Group, members: List<Member>, member: Member? = null): Money {
        return getStrategy(group.type).calculateProjectedPayout(group, members, member)
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

        val joinedDate = resolveJoinedDate(member, currentDate, group.paymentDueDay, contributions)

        // 1. Calculate Totals Paid by Category
        val paidContributions = contributions.filter { it.status == ContributionStatus.PAID || it.status == ContributionStatus.PARTIAL }

        // Note: For loan_settlement_extraction, the amount is negative so it reduces the totalPaidSavings
        val calculatedPaidSavings = paidContributions.filter { it.type == "contribution" || it.type == "registration_contribution" || it.type == "loan_settlement_extraction" }.fold(Money.ZERO) { acc, c -> acc + c.amount }
        val totalPaidSavings = if (calculatedPaidSavings.isZero && member.totalPaid.isPositive) member.totalPaid else calculatedPaidSavings
        val totalPaidPlatform = paidContributions.filter { it.type == "member_fee" || it.type == "platform_fee" }.fold(Money.ZERO) { acc, c -> acc + c.amount }
        val totalPaidLoans = loans.fold(Money.ZERO) { acc, l -> acc + l.totalRepaid }

        // 2. Calculate expectations and fees up to now
        val (totalExpectedSavings, totalExpectedPlatform, totalExpectedLoans, totalLateFees, isCurrentlyOverdue) = calculateDetailedExpectations(
            group = group,
            loans = loans,
            joinedDate = joinedDate,
            currentDate = currentDate,
            totalPaidSavings = totalPaidSavings,
            expectedSavingsPerMonth = expectedSavingsPerMonth,
            platformFeePerMonth = platformFeePerMonth
        )

        // 3. Category-specific shortfall calculation
        val savingsShortfall = (totalExpectedSavings - totalPaidSavings).max(Money.ZERO)
        val platformShortfall = (totalExpectedPlatform - totalPaidPlatform).max(Money.ZERO)
        val loanShortfall = (totalExpectedLoans - totalPaidLoans).max(Money.ZERO)

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

        val loanMonthlyTotal = loans.filter { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID }
            .fold(Money.ZERO) { acc, l -> acc + l.monthlyRepayment }
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
        val expectedLoans: Money,
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
        var totalExpectedLoans = Money.ZERO
        var totalLateFees = Money.ZERO
        var isCurrentlyOverdue = false

        var tempDate = joinedDate.withDayOfMonth(1)
        val endLimit = currentDate.withDayOfMonth(1)

        while (!tempDate.isAfter(endLimit)) {
            val dueDayThisMonth = try {
                tempDate.withDayOfMonth(group.paymentDueDay)
            } catch (_: Exception) {
                tempDate.withDayOfMonth(tempDate.lengthOfMonth())
            }

            if (!currentDate.isBefore(dueDayThisMonth) || tempDate.isBefore(endLimit)) {
                totalExpectedSavings += expectedSavingsPerMonth
                totalExpectedPlatform += platformFeePerMonth

                for (loan in loans.filter { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID }) {
                    val loanStartDate = loan.startDate?.let { runCatching { LocalDate.parse(it.substringBefore("T")) }.getOrNull() }
                    val loanEndDate = loan.endDate?.let { runCatching { LocalDate.parse(it.substringBefore("T")) }.getOrNull() }

                    if (loanStartDate != null) {
                        val loanStartMonth = loanStartDate.withDayOfMonth(1)
                        if (!tempDate.isBefore(loanStartMonth)) {
                            val effectiveEndDate = loanEndDate ?: loanStartDate.plusMonths(12)
                            val isPastTerm = tempDate.isAfter(effectiveEndDate.withDayOfMonth(1))
                            if (!isPastTerm) {
                                totalExpectedLoans += loan.monthlyRepayment
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

        return DetailedExpectations(totalExpectedSavings, totalExpectedPlatform, totalExpectedLoans, totalLateFees, isCurrentlyOverdue)
    }

    fun calculateNextDueDate(
        group: Group,
        joinedDate: LocalDate,
        monthlyAmount: Money,
        totalPaid: Money
    ): String {
        var cumulativeExpected = Money.ZERO
        var tempDate = joinedDate.withDayOfMonth(1)
        val limit = DateProvider.getCurrentDate().plusYears(2)

        while (tempDate.isBefore(limit)) {
            cumulativeExpected += monthlyAmount
            if (cumulativeExpected > totalPaid) {
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

    private fun resolveJoinedDate(member: Member, fallback: LocalDate, dueDay: Int, contributions: List<Contribution> = emptyList()): LocalDate {
        return try {
            val rawStr = member.joinedAt ?: member.createdAt
            val parsedJoined = runCatching {
                if (!rawStr.isNullOrBlank()) {
                    LocalDate.parse(rawStr.substringBefore("T").substringBefore(" "))
                } else null
            }.getOrNull()

            val earliestContrib = contributions.mapNotNull {
                runCatching { LocalDate.parse(it.dueDate.substringBefore("T")) }.getOrNull()
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
}
