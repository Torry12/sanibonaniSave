package com.sanibonani.save.domain.usecase.credit

import com.sanibonani.save.domain.model.AmortizationType
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Prices and files a member's loan application.
 *
 * The configured rate and fee are the price of this loan, so a failed settings read
 * stops the request instead of billing an invented default. Eligibility is re-checked
 * here — including the group's per-loan cap — so an over-cap request never reaches
 * the approval queue.
 */
class RequestLoanUseCase @Inject constructor(
    private val loanRepository: LoanRepository,
    private val platformRepository: PlatformRepository,
    private val calculateSchedule: CalculateLoanRepaymentScheduleUseCase,
    private val validateEligibility: ValidateLoanEligibilityUseCase
) {
    data class Params(
        val group: Group,
        val member: Member,
        val amount: Money,
        val purpose: String,
        val termMonths: Int,
        val startDate: LocalDate = LocalDate.now()
    )

    suspend operator fun invoke(params: Params): Result<Loan> {
        val groupId = params.group.id
            ?: return Result.failure(IllegalStateException("No server id for group"))
        val memberId = params.member.id
            ?: return Result.failure(IllegalStateException("No server id for member"))

        val eligibility = validateEligibility(
            params.member,
            params.group,
            params.amount,
            params.termMonths
        )
        if (eligibility is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible) {
            return Result.failure(IllegalStateException(eligibility.reason))
        }

        val settings = platformRepository.getPlatformSettings()
            .getOrElse { return Result.failure(it) }

        val groupRate = params.group.loanInterestRate
        val effectiveInterestRate = if (groupRate != null && groupRate > 0.0) {
            groupRate
        } else {
            settings.loanInterestRate.toDouble()
        }

        val schedule = calculateSchedule(
            CalculateLoanRepaymentScheduleUseCase.Params(
                principal = params.amount,
                annualInterestRate = effectiveInterestRate,
                termMonths = params.termMonths,
                processingFee = settings.loanProcessingFee,
                startDate = params.startDate
            )
        )

        val loan = Loan(
            groupId = groupId,
            memberId = memberId,
            amount = params.amount,
            interestRate = effectiveInterestRate,
            totalToRepay = schedule.totalToRepay,
            monthlyRepayment = schedule.monthlyInstallment,
            startDate = params.startDate.toString(),
            endDate = params.startDate.plusMonths(params.termMonths.toLong()).toString(),
            status = LoanStatus.PENDING,
            purpose = params.purpose,
            amortizationType = AmortizationType.FLAT
        )

        return loanRepository.requestLoan(loan).map { loan }
    }
}
