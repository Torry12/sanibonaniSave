package com.sanibonani.save.domain.usecase.credit

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.repository.AuditLogRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject

/**
 * Centralizes the logic for transitioning a loan from one status to another.
 * Handles database updates, audit logging, and member notifications.
 */
class TransitionLoanStatusUseCase @Inject constructor(
    private val loanRepository: LoanRepository,
    private val auditLogRepo: AuditLogRepository,
    private val notificationRepo: NotificationRepository
) {

    suspend operator fun invoke(
        loanId: String,
        newStatus: LoanStatus,
        actorId: String,
        adminNotes: String? = null,
        rejectionReason: String? = null
    ): Result<Unit> = runCatching {
        val loan = loanRepository.getLoanById(loanId).getOrThrow()

        when (newStatus) {
            LoanStatus.APPROVED -> {
                loanRepository.approveLoan(loanId).getOrThrow()
                notifyMember(loan, NotifEvent.LOAN_APPROVED, "Your loan request for ${loan.amount.format()} has been approved.")
            }
            LoanStatus.REJECTED -> {
                loanRepository.rejectLoan(loanId, rejectionReason ?: "Requirements not met").getOrThrow()
                notifyMember(loan, NotifEvent.LOAN_REJECTED, "Your loan request was declined. Reason: ${rejectionReason ?: "See admin notes"}")
            }
            LoanStatus.ACTIVE -> {
                notifyMember(loan, NotifEvent.LOAN_REQUESTED, "Loan funds of ${loan.amount.format()} have been disbursed to your account.")
            }
            LoanStatus.COMPLETED -> {
                notifyMember(loan, NotifEvent.LOAN_DEFAULTED, "Congratulations! Your loan has been fully repaid.")
            }
            else -> { /* No-op */ }
        }

        auditLogRepo.logEvent(
            AuditLog(
                actorId = actorId,
                targetMemberId = loan.memberId,
                targetGroupId = loan.groupId,
                action = "LOAN_STATUS_TRANSITION_${newStatus.name}",
                details = Json.encodeToJsonElement(mapOf(
                    "loanId" to loanId,
                    "previousStatus" to loan.status.name,
                    "newStatus" to newStatus.name,
                    "adminNotes" to (adminNotes ?: ""),
                    "rejectionReason" to (rejectionReason ?: "")
                ))
            )
        )
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
}
