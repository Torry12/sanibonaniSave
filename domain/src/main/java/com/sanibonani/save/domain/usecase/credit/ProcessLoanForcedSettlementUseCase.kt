package com.sanibonani.save.domain.usecase.credit
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.LedgerEntryCreatedEvent
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import com.sanibonani.save.domain.utils.PaymentCalculator
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import com.sanibonani.save.domain.usecase.financialResult
import com.sanibonani.save.domain.usecase.firstValueOrNull

/**
 * Handles the automatic extraction of remaining loan balances after the 3-month maximum term.
 */
class ProcessLoanForcedSettlementUseCase @Inject constructor(
    private val loanRepository: LoanRepository,
    private val memberRepository: MemberRepository,
    private val contributionRepository: ContributionRepository,
    private val platformRepository: PlatformRepository,
    private val auditLogRepository: AuditLogRepository,
    private val notificationRepository: NotificationRepository,
    private val paymentCalculator: PaymentCalculator
) {
    suspend operator fun invoke(): Result<Unit> = financialResult {
        val groups = platformRepository.getAllGroups().getOrThrow()
        for (group in groups) {
            val loans = loanRepository.getGroupLoans(group.id ?: "").firstValueOrNull() ?: continue
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

        // 1. Extract from member's contributions to pay off the loan (negative contribution)
        memberRepository.recordContribution(
            Contribution(
                memberId = loan.memberId,
                groupId = loan.groupId,
                amount = -remainingBalance,
                type = "loan_settlement_extraction",
                status = ContributionStatus.PAID,
                dueDate = LocalDate.now().toString(),
                transactionId = "forced_settlement_${loan.id}"
            )
        ).getOrThrow()

        // 2. Update loan status to COMPLETED
        loanRepository.updateLoanStatus(loan.id ?: "", LoanStatus.COMPLETED).getOrThrow()

        // 3. Create LedgerEntry for internal settlement
        EventBus.emit(
            LedgerEntryCreatedEvent(
                LedgerEntry(
                    groupId = loan.groupId,
                    amount = remainingBalance,
                    category = "Loan Settlement",
                    description = "Forced extraction for expired loan ${loan.id}",
                    memberId = loan.memberId
                )
            )
        )

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
        val contributions = contributionRepository.observeMemberContributions(member.id ?: "", group.id ?: "").firstValueOrNull() ?: emptyList()
        val loans = loanRepository.getMemberLoans(member.id ?: "").firstValueOrNull() ?: emptyList()
        val status = paymentCalculator.calculateStatus(group, updatedMember, contributions, loans, LocalDate.now())
        if (status.shortfall > (group.monthlyContribution * 2.0)) {
            memberRepository.updateMemberStatus(member.id ?: "", MemberStatus.PROBATION)
        }
    }
}
