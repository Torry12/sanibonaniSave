package com.sanibonani.save.domain.usecase.credit

import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.repository.credit.LoanRepository
import javax.inject.Inject

/**
 * Loan applications still waiting on a platform decision.
 */
class GetPendingLoanRequestsUseCase @Inject constructor(
    private val loanRepository: LoanRepository
) {
    suspend operator fun invoke(): Result<List<Loan>> =
        loanRepository.getAllPendingLoansSync()
}
