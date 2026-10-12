package com.sanibonani.save.domain.usecase.credit
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject
import com.sanibonani.save.domain.usecase.financialResult

/**
 * Orchestrates Admin loan decisions (Approval/Rejection).
 * Integrates eligibility re-validation and behavior-based fraud checks.
 */
class ProcessLoanDecisionUseCase @Inject constructor(
    private val loanRepo: LoanRepository,
    private val memberRepo: MemberRepository,
    private val groupRepo: GroupRepository,
    private val behaviorRepo: BehaviorTrackingRepository,
    private val validateEligibility: ValidateLoanEligibilityUseCase,
    private val auditLogRepo: AuditLogRepository,
    private val notificationRepo: NotificationRepository
) {
    suspend operator fun invoke(
        loanId: String,
        decision: LoanDecision,
        adminId: String,
        adminNotes: String? = null
    ): Result<Loan> = financialResult {
        // 1. Fetch data
        val loan = loanRepo.getLoanById(loanId).getOrThrow()
        val member = memberRepo.getMemberById(loan.memberId).getOrThrow()
        val group = groupRepo.getGroupById(loan.groupId).getOrThrow()

        if (decision is LoanDecision.Approve) {
            // 2. Fraud Check: Ensure member isn't high risk
            val behavior = behaviorRepo.getMemberBehavior(loan.memberId).getOrNull()
            if (behavior != null && behavior.fraudRiskLevel == FraudRiskLevel.CRITICAL) {
                throw IllegalStateException("Member has CRITICAL fraud risk. Approval blocked.")
            }

            // 3. Re-validate Eligibility
            val termMonths = runCatching {
                val start = java.time.LocalDate.parse(loan.startDate ?: java.time.LocalDate.now().toString())
                val end = java.time.LocalDate.parse(loan.endDate ?: start.plusMonths(3).toString())
                java.time.temporal.ChronoUnit.MONTHS.between(start, end).toInt().coerceAtLeast(1)
            }.getOrDefault(3)

            val eligibility = validateEligibility(member, group, loan.amount, termMonths)
            if (eligibility is ValidateLoanEligibilityUseCase.EligibilityResult.Ineligible) {
                throw IllegalStateException("Eligibility re-validation failed: ${eligibility.reason}")
            }

            // 4. Approve
            loanRepo.approveLoan(loanId, adminId).getOrThrow()
            notifyMember(loan, NotifEvent.LOAN_APPROVED, "Your loan request for ${loan.amount.format()} has been approved.")
        } else if (decision is LoanDecision.Reject) {
            // 5. Reject
            val reason = decision.reason
            loanRepo.rejectLoan(loanId, reason, adminId).getOrThrow()
            notifyMember(loan, NotifEvent.LOAN_REJECTED, "Your loan request was declined. Reason: $reason")
        }

        // 6. Audit Trail
        auditLogRepo.logEvent(
            AuditLog(
                actorId = adminId,
                targetMemberId = loan.memberId,
                targetGroupId = loan.groupId,
                action = "LOAN_DECISION_${if (decision is LoanDecision.Approve) "APPROVED" else "REJECTED"}",
                details = Json.encodeToJsonElement(mapOf(
                    "loanId" to loanId,
                    "decision" to decision.toString(),
                    "adminNotes" to (adminNotes ?: "")
                ))
            )
        )

        // 7. Return updated loan
        return@financialResult loanRepo.getLoanById(loanId).getOrThrow()
    }

    private suspend fun notifyMember(loan: Loan, event: NotifEvent, message: String) {
        notificationRepo.sendNotification(
            AppNotification(
                groupId = loan.groupId,
                memberId = loan.memberId,
                message = message,
                channel = NotifChannel.BOTH,
                triggerEvent = event
            )
        )
    }

    sealed class LoanDecision {
        object Approve : LoanDecision()
        data class Reject(val reason: String) : LoanDecision()
    }
}
