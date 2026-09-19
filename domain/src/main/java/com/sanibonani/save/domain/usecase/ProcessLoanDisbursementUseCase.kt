package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.AuditLogRepository
import com.sanibonani.save.domain.repository.LoanRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.LoanDisbursedEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject

/**
 * Orchestrates the final stage of a loan lifecycle: disbursement.
 * Enforces high-security requirements like MFA for large amounts.
 */
class ProcessLoanDisbursementUseCase @Inject constructor(
    private val loanRepository: LoanRepository,
    private val supabaseRepository: SupabaseRepository,
    private val auditLogRepository: AuditLogRepository
) {
    companion object {
        private val MFA_THRESHOLD = Money(10000.0)
    }

    suspend operator fun invoke(
        loanId: String,
        adminId: String,
        paymentMethod: PaymentMethod = PaymentMethod.BANK
    ): Result<Unit> = runCatching {
        // 1. Fetch loan details
        val loan = loanRepository.getLoanById(loanId).getOrThrow()

        // 2. Security Hardening: High-Value Disbursement MFA (AAL2) Requirement
        if (loan.amount >= MFA_THRESHOLD) {
            if (!supabaseRepository.isMfaVerified) {
                throw Exception("MFA_REQUIRED: Disbursement of ${loan.amount.format()} requires multi-factor authentication (AAL2).")
            }
        }

        // 3. Execute atomic disbursement in backend
        loanRepository.disburseLoan(loanId, paymentMethod, adminId).getOrThrow()

        // 4. Audit Trail
        auditLogRepository.logEvent(
            AuditLog(
                actorId = adminId,
                targetMemberId = loan.memberId,
                targetGroupId = loan.groupId,
                action = "LOAN_DISBURSED",
                details = Json.encodeToJsonElement(mapOf(
                    "loanId" to loanId,
                    "amount" to loan.amount.format(),
                    "method" to paymentMethod.name
                ))
            )
        )

        // 5. Emit Domain Event (Handled by DisbursementBroadcastHandler for group-wide notif)
        val updatedLoan = loanRepository.getLoanById(loanId).getOrThrow()
        EventBus.emit(LoanDisbursedEvent(updatedLoan))
    }
}
