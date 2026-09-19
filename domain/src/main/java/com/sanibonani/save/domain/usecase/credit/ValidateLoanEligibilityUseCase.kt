package com.sanibonani.save.domain.usecase.credit

import com.sanibonani.save.domain.utils.DateProvider
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.BehaviorTrackingRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.MemberRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ValidateLoanEligibilityUseCase @Inject constructor(
    private val contributionRepository: ContributionRepository,
    private val loanRepository: LoanRepository,
    private val behaviorRepo: BehaviorTrackingRepository
) {
    sealed class EligibilityResult {
        object Eligible : EligibilityResult()
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
        if (termMonths != null && termMonths > 3) return EligibilityResult.Ineligible("Term exceeds 3 months.")

        val activeLoans = loanRepository.getMemberLoans(member.id ?: "").first().getOrNull()?.filter {
            it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID || it.status == LoanStatus.APPROVED
        } ?: emptyList()

        if (activeLoans.isNotEmpty()) return EligibilityResult.Ineligible("Existing active loan.")

        val contributions = contributionRepository.observeMemberContributions(member.id ?: "", group.id ?: "").first().getOrNull() ?: emptyList()

        val sixMonthsAgo = DateProvider.getCurrentDate().minusMonths(6)
        val last6MonthsContributions = contributions.filter {
            runCatching {
                val paidAt = it.paidAt?.substringBefore("T")?.let { date -> java.time.LocalDate.parse(date) }
                paidAt != null && !paidAt.isBefore(sixMonthsAgo)
            }.getOrDefault(false)
        }

        val totalLast6Months = last6MonthsContributions.fold(Money.ZERO) { acc, c -> acc + c.amount }
        val maxBorrowingPower = totalLast6Months * 0.3

        if (requestedAmount != null && requestedAmount > maxBorrowingPower) {
            return EligibilityResult.Ineligible("Exceeds 30% of last 6 month contributions.")
        }

        val status = PaymentCalculator.calculateStatus(group, member, contributions, activeLoans, DateProvider.getCurrentDate())
        if (status.shortfall.isPositive || status.isOverdue) {
            return EligibilityResult.Ineligible("Not in good standing. Shortfall: ${status.shortfall.format()}")
        }

        return EligibilityResult.Eligible
    }
}
