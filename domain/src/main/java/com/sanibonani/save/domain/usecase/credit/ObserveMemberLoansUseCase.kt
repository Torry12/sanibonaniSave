package com.sanibonani.save.domain.usecase.credit

import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.repository.credit.LoanRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The loans held by one member, across the groups they belong to.
 */
class ObserveMemberLoansUseCase @Inject constructor(
    private val loanRepository: LoanRepository
) {
    operator fun invoke(memberId: String): Flow<Result<List<Loan>>> =
        loanRepository.getMemberLoans(memberId)
}
