package com.sanibonani.save.domain.usecase.credit
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
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

        // The confirmation read runs after the write verdict and cannot undo it: the funds had
        // already left the group, so a read that fails reports the last row actually held rather
        // than inviting the admin to disburse the same loan a second time.
        val updatedLoan = loanRepository.getLoanById(loanId).getOrNull() ?: loan

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
        // 5. LoanDisbursedEvent is announced by LoanRepositoryImpl.disburseLoan inside the
        // authoritative write, so it is not emitted a second time here.

        return@financialResult updatedLoan
    }
}
