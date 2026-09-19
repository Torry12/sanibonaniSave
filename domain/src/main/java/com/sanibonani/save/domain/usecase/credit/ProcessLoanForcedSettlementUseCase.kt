package com.sanibonani.save.domain.usecase.credit

import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Handles the automatic extraction of remaining loan balances after the 3-month maximum term.
 */
class ProcessLoanForcedSettlementUseCase @Inject constructor(
    private val loanRepository: LoanRepository,
    private val memberRepository: MemberRepository,
    private val contributionRepository: ContributionRepository,
    private val groupRepository: GroupRepository,
    private val auditLogRepository: AuditLogRepository,
    private val notificationRepository: NotificationRepository
) {

    suspend operator fun invoke(): Result<Unit> = runCatching {
        val groups = groupRepository.getAllGroups().getOrThrow()

        for (group in groups) {
            val loansResult = loanRepository.getGroupLoans(group.id ?: "").first()
            val loans = loansResult.getOrNull() ?: continue

            val expiredLoans = loans.filter { loan ->
                (loan.status == LoanStatus.ACTIVE || loan.status == LoanStatus.PARTIALLY_PAID) &&
                isLoanTermExceeded(loan)
            }

            for (loan in expiredLoans) {
                settleLoanFromContributions(loan, group)
            }
        }
    }

    private fun isLoanTermExceeded(loan: Loan): Boolean {
        val startDate = loan.startDate?.let { LocalDate.parse(it.substringBefore("T")) } ?: return false
        val monthsElapsed = ChronoUnit.MONTHS.between(startDate, LocalDate.now())
        return monthsElapsed >= 3
    }

    private suspend fun settleLoanFromContributions(loan: Loan, group: Group) {
        val remainingBalance = loan.balanceRemaining
        if (!remainingBalance.isPositive) return

        val member = memberRepository.getMemberById(loan.memberId).getOrNull() ?: return

        memberRepository.recordLoanSettlementExtraction(
            memberId = loan.memberId,
            groupId = loan.groupId,
            amount = remainingBalance,
            loanId = loan.id ?: ""
        ).getOrThrow()

        auditLogRepository.logEvent(
            AuditLog(
                actorId = "SYSTEM",
                targetMemberId = loan.memberId,
                targetGroupId = loan.groupId,
                action = "LOAN_FORCED_SETTLEMENT",
                details = Json.encodeToJsonElement(mapOf(
                    "loanId" to (loan.id ?: ""),
                    "extractedAmount" to remainingBalance.format()
                ))
            )
        )

        if (group.type == GroupType.BURIAL_SOCIETY) {
            handleBurialSocietyProbationReset(member, group)
        }

        notificationRepository.sendNotification(
            AppNotification(
                groupId = loan.groupId,
                memberId = loan.memberId,
                message = "Loan term exceeded. Balance ${remainingBalance.format()} settled from contributions.",
                channel = NotifChannel.BOTH,
                triggerEvent = NotifEvent.LOAN_DEFAULTED
            )
        )
    }

    private suspend fun handleBurialSocietyProbationReset(member: Member, group: Group) {
        val updatedMember = memberRepository.getMemberById(member.id ?: "").getOrNull() ?: return
        val contributions = contributionRepository.observeMemberContributions(member.id ?: "", group.id ?: "").first().getOrNull() ?: emptyList()
        val loans = loanRepository.getMemberLoans(member.id ?: "").first().getOrNull() ?: emptyList()

        val status = PaymentCalculator.calculateStatus(group, updatedMember, contributions, loans, java.time.LocalDate.now())

        if (status.shortfall > (group.monthlyContribution * 2.0)) {
            memberRepository.updateMemberStatus(member.id ?: "", MemberStatus.PROBATION)
        }
    }
}
