package com.sanibonani.save.features.admin.presentation.actions

import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDecisionUseCase
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import com.sanibonani.save.service.AdminGroupContextCacheService
import javax.inject.Inject

/**
 * Loan decision lifecycle: approval (with automatic disbursement) and rejection.
 */
class AdminLoanActions @Inject constructor(
    private val processLoanDecisionUseCase: ProcessLoanDecisionUseCase,
    private val processLoanDisbursementUseCase: ProcessLoanDisbursementUseCase,
    private val supabaseRepo: SupabaseRepository,
    private val adminContextCacheService: AdminGroupContextCacheService,
) {

    fun approveLoan(host: AdminFinancialActionHost, loanId: String) {
        val groupId = adminContextCacheService.selectedGroupId.value ?: return
        val adminId = supabaseRepo.currentUserId ?: return
        host.launchAction("approve_loan") {
            host.updateActionState { it.copy(isLoading = true, error = null, processingIds = it.processingIds + loanId) }
            processLoanDecisionUseCase(
                loanId = loanId,
                decision = ProcessLoanDecisionUseCase.LoanDecision.Approve,
                adminId = adminId
            ).onSuccess {
                processLoanDisbursementUseCase(loanId, adminId, PaymentMethod.BANK).onSuccess {
                    host.updateActionState { s ->
                        s.copy(groupLoans = s.groupLoans.map { loan ->
                            if (loan.id == loanId) loan.copy(status = LoanStatus.ACTIVE) else loan
                        })
                    }
                    host.updateActionState { it.copy(isLoading = false, successMessage = "Loan approved and disbursed") }
                    adminContextCacheService.warmUpGroup(groupId)
                }.onFailure {
                    host.updateActionState { s ->
                        s.copy(groupLoans = s.groupLoans.map { loan ->
                            if (loan.id == loanId) loan.copy(status = LoanStatus.APPROVED) else loan
                        })
                    }
                    host.updateActionState { it.copy(isLoading = false, successMessage = "Loan approved") }
                    adminContextCacheService.warmUpGroup(groupId)
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
        val adminId = supabaseRepo.currentUserId ?: return
        host.launchAction("reject_loan") {
            host.updateActionState { it.copy(isLoading = true, error = null, processingIds = it.processingIds + loanId) }
            processLoanDecisionUseCase(
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
                adminContextCacheService.warmUpGroup(groupId)
            }.onFailure { e ->
                host.updateActionState { it.copy(isLoading = false) }
                host.reportActionError(e)
            }
            host.updateActionState { it.copy(processingIds = it.processingIds - loanId) }
        }
    }
}
