package com.sanibonani.save.domain.usecase.credit
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.LoanDisbursedEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject
import com.sanibonani.save.domain.usecase.financialResult
/**
 * Orchestrates the final stage of a loan lifecycle: disbursement.
 * Enforces high-security requirements like MFA for large amounts.
 */
class ProcessLoanDisbursementUseCase @Inject constructor(
    private val loanRepository: LoanRepository,
    private val securityService: com.sanibonani.save.domain.service.FinancialSecurityService,
    private val auditLogRepository: AuditLogRepository
) {
    suspend operator fun invoke(
        loanId: String,
        adminId: String,
        paymentMethod: PaymentMethod = PaymentMethod.BANK
    ): Result<Loan> = financialResult {
        // 1. Fetch loan details
        val loan = loanRepository.getLoanById(loanId).getOrThrow()

        // 2. Security Hardening: High-Value Disbursement MFA & Liquidity check
        securityService.assertMfaRequirement(loan.amount).getOrThrow()
        securityService.validateLiquidity(loan.groupId, loan.amount).getOrThrow()

        // 3. Execute atomic disbursement in backend
        loanRepository.disburseLoan(loanId, paymentMethod, adminId).getOrThrow()

        val updatedLoan = loanRepository.getLoanById(loanId).getOrThrow()

        // 4. Audit Trail
        auditLogRepository.logEvent(
            AuditLog(
                actorId = adminId,
                targetMemberId = updatedLoan.memberId,
                targetGroupId = updatedLoan.groupId,
                action = "LOAN_DISBURSED",
                details = Json.encodeToJsonElement(mapOf(
                    "loanId" to loanId,
                    "amount" to updatedLoan.amount.format(),
                    "method" to paymentMethod.name
                ))
            )
        )
        // 5. Emit Domain Event
        EventBus.emit(LoanDisbursedEvent(updatedLoan))

        return@financialResult updatedLoan
    }
}
