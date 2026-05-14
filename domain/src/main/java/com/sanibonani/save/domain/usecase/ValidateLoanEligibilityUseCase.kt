package com.sanibonani.save.domain.usecase

import com.sanibonani.save.data.utils.DateProvider
import com.sanibonani.save.data.utils.PaymentCalculator
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.MemberRepository
import kotlinx.coroutines.flow.first
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class ValidateLoanEligibilityUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    sealed class EligibilityResult {
        object Eligible : EligibilityResult()
        data class Ineligible(val reason: String) : EligibilityResult()
    }

    /**
     * @param member         The member requesting the loan.
     * @param group          The group the loan is against.
     * @param requestedAmount Optional loan amount; validated against [Group.loanMaxAmount] when set.
     */
    suspend operator fun invoke(
        member: Member,
        group: Group,
        requestedAmount: Double? = null
    ): EligibilityResult {
        // 1. Check Member Status
        if (member.status != MemberStatus.ACTIVE) {
            return EligibilityResult.Ineligible("Only active members can request loans.")
        }

        // 2. Check Membership Duration (> 6 months)
        val joinedDate = member.joinedAt?.let {
            try {
                java.time.LocalDate.parse(it.substringBefore("T"))
            } catch (_: Exception) {
                null
            }
        } ?: return EligibilityResult.Ineligible("Membership start date not found.")

        val monthsJoined = ChronoUnit.MONTHS.between(joinedDate, DateProvider.getCurrentDate())
        if (monthsJoined < 6) {
            return EligibilityResult.Ineligible(
                "You must be a member for at least 6 months to qualify for a loan (Current: $monthsJoined months)."
            )
        }

        // 3. Check Contributions Status (Must be up to date)
        val contributionsResult = memberRepository.getMemberContributions(member.id ?: "", group.id ?: "").first()
        val contributions = contributionsResult.getOrNull() ?: emptyList()

        val calculation = PaymentCalculator.calculateStatus(group, member, contributions, DateProvider.getCurrentDate())
        if (calculation.shortfall > 0 || calculation.isOverdue) {
            return EligibilityResult.Ineligible(
                "You must be up to date with your monthly contributions to qualify for a loan. (Shortfall: R${calculation.shortfall})"
            )
        }

        // 4. Loan amount cap check (only when group has configured a maximum)
        val maxLoan = group.loanMaxAmount
        if (requestedAmount != null && maxLoan != null && maxLoan > 0.0 && requestedAmount > maxLoan) {
            return EligibilityResult.Ineligible(
                "Requested amount R$requestedAmount exceeds the group's maximum loan of R$maxLoan."
            )
        }

        return EligibilityResult.Eligible
    }
}
