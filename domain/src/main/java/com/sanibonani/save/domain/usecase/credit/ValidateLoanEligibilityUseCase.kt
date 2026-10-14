package com.sanibonani.save.domain.usecase.credit
import com.sanibonani.save.domain.utils.DateProvider
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import java.time.LocalDate
import javax.inject.Inject
import com.sanibonani.save.domain.usecase.firstValueOrNull
import com.sanibonani.save.domain.utils.safeRunCatching

class ValidateLoanEligibilityUseCase @Inject constructor(
    private val contributionRepository: ContributionRepository,
    private val loanRepository: LoanRepository,
    private val behaviorRepo: BehaviorTrackingRepository,
    private val paymentCalculator: PaymentCalculator
) {
    sealed class EligibilityResult {
        data class Eligible(
            val maxBorrowingPower: Money = Money.ZERO,
            val currentActiveLoanTotal: Money = Money.ZERO,
            val availableBorrowingPower: Money = Money.ZERO
        ) : EligibilityResult()

        data class Ineligible(val reason: String) : EligibilityResult()
    }

    suspend operator fun invoke(
        member: Member,
        group: Group,
        requestedAmount: Money? = null,
        termMonths: Int? = null
    ): EligibilityResult {
        if (group.type == GroupType.INVESTMENT_CLUB) return EligibilityResult.Ineligible("No loans for Investment Clubs.")
        if (member.status != MemberStatus.ACTIVE) return EligibilityResult.Ineligible("Member not active.")
        if (termMonths != null && termMonths > 3) return EligibilityResult.Ineligible("Loan term exceeds the 3-month maximum limit.")

        val contributions = contributionRepository.observeMemberContributions(member.id ?: "", group.id ?: "").firstValueOrNull() ?: emptyList()

        // Enforce joining fee rule: Member must have paid their joining fee to be fully active/eligible
        val hasPaidJoiningFee = group.joiningFee.isZero || contributions.any { it.type == "registration_contribution" && it.status == ContributionStatus.PAID }
        if (!hasPaidJoiningFee) {
            return EligibilityResult.Ineligible("Joining fee of ${group.joiningFee.format()} is outstanding. Please settle your joining fee first.")
        }

        val activeLoans = loanRepository.getMemberLoans(member.id ?: "").firstValueOrNull()?.filter {
            it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID || it.status == LoanStatus.APPROVED || it.status == LoanStatus.PENDING
        } ?: emptyList()

        val currentActiveLoanTotal = activeLoans.fold(Money.ZERO) { acc, l -> acc + l.amount }

        val sixMonthsAgo = DateProvider.getCurrentDate().minusMonths(6)
        val last6MonthsContributions = contributions.filter {
            safeRunCatching {
                val paidAt = it.paidAt?.substringBefore("T")?.let { date -> LocalDate.parse(date) }
                paidAt != null && !paidAt.isBefore(sixMonthsAgo)
            }.getOrDefault(false)
        }
        val totalLast6Months = last6MonthsContributions.fold(Money.ZERO) { acc, c -> acc + c.amount }
        val maxBorrowingPower = totalLast6Months * 0.3
        val availableBorrowingPower = (maxBorrowingPower - currentActiveLoanTotal).max(Money.ZERO)

        if (!availableBorrowingPower.isPositive && requestedAmount != null) {
            return EligibilityResult.Ineligible("You have reached your maximum borrowing limit (${maxBorrowingPower.format()}). Active loans total: ${currentActiveLoanTotal.format()}.")
        }

        if (requestedAmount != null && requestedAmount > availableBorrowingPower) {
            return EligibilityResult.Ineligible("Requested amount (${requestedAmount.format()}) exceeds remaining available borrowing power (${availableBorrowingPower.format()}). Total limit: ${maxBorrowingPower.format()}.")
        }

        val status = paymentCalculator.calculateStatus(group, member, contributions, activeLoans, DateProvider.getCurrentDate())
        if (status.shortfall.isPositive || status.isOverdue) {
            return EligibilityResult.Ineligible("Not in good standing. Shortfall: ${status.shortfall.format()}")
        }

        return EligibilityResult.Eligible(
            maxBorrowingPower = maxBorrowingPower,
            currentActiveLoanTotal = currentActiveLoanTotal,
            availableBorrowingPower = availableBorrowingPower
        )
    }
}
