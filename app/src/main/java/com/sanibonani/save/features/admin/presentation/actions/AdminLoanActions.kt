package com.sanibonani.save.features.admin.presentation.actions

import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDecisionUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import javax.inject.Inject

/**
 * Loan decision lifecycle: approval (with automatic disbursement) and rejection.
 */
class AdminLoanActions @Inject constructor(
    private val processLoanDecision: ProcessLoanDecisionUseCase,
    private val processLoanDisbursement: ProcessLoanDisbursementUseCase,
    private val getAttributableActorId: GetAttributableActorIdUseCase,
    private val adminContextCacheService: AdminGroupContextCacheService,
) {

    // The domain refuses an unattributable decision, so report a blank session
    // instead of silently dropping the tap.
    private fun adminIdOrReport(host: AdminFinancialActionHost, action: String): String? {
        val adminId = getAttributableActorId()
        if (adminId == null) {
            host.reportError(IllegalStateException("Sign in again to change a $action status."))
        }
        return adminId
    }

    fun approveLoan(host: AdminFinancialActionHost, loanId: String) {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        val adminId = adminIdOrReport(host, "loan") ?: return
        host.launchAction("approve_loan") {
            host.updateActionState { it.copy(isLoading = true, error = null, processingIds = it.processingIds + loanId) }
            processLoanDecision(
                loanId = loanId,
                decision = ProcessLoanDecisionUseCase.LoanDecision.Approve,
                adminId = adminId
            ).onSuccess {
                processLoanDisbursement(loanId, adminId, PaymentMethod.BANK).onSuccess {
                    host.updateActionState { s ->
                        s.copy(groupLoans = s.groupLoans.map { loan ->
                            if (loan.id == loanId) loan.copy(status = LoanStatus.ACTIVE) else loan
                        })
                    }
                    host.updateActionState { it.copy(isLoading = false, successMessage = "Loan approved and disbursed") }
                    adminContextCacheService.warmUpGroup(groupId, force = true)
                }.onFailure {
                    host.updateActionState { s ->
                        s.copy(groupLoans = s.groupLoans.map { loan ->
                            if (loan.id == loanId) loan.copy(status = LoanStatus.APPROVED) else loan
                        })
                    }
                    host.updateActionState { it.copy(isLoading = false, successMessage = "Loan approved") }
                    adminContextCacheService.warmUpGroup(groupId, force = true)
                }
            }.onFailure { e ->
                host.updateActionState { it.copy(isLoading = false) }
                host.reportActionError(e)
            }
            host.updateActionState { it.copy(processingIds = it.processingIds - loanId) }
        }
    }

    fun rejectLoan(host: AdminFinancialActionHost, loanId: String, reason: String) {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        val adminId = adminIdOrReport(host, "loan") ?: return
        host.launchAction("reject_loan") {
            host.updateActionState { it.copy(isLoading = true, error = null, processingIds = it.processingIds + loanId) }
            processLoanDecision(
                loanId = loanId,
                decision = ProcessLoanDecisionUseCase.LoanDecision.Reject(reason),
                adminId = adminId
            ).onSuccess { rejectedLoan ->
                host.updateActionState { s ->
                    s.copy(
                        groupLoans = s.groupLoans.map { loan -> if (loan.id == loanId) rejectedLoan else loan },
                        isLoading = false,
                        successMessage = "Loan request rejected"
                    )
                }
                adminContextCacheService.warmUpGroup(groupId, force = true)
            }.onFailure { e ->
                host.updateActionState { it.copy(isLoading = false) }
                host.reportActionError(e)
            }
            host.updateActionState { it.copy(processingIds = it.processingIds - loanId) }
        }
    }
}
