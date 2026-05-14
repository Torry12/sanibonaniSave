package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.ExportRepository
import java.io.File
import javax.inject.Inject

class GenerateLoanContractUseCase @Inject constructor(
    private val exportRepo: ExportRepository
) {
    suspend operator fun invoke(loan: Loan, member: Member, group: Group): Result<File> {
        // Coherence: ensure loan belongs to the same member and group
        if (loan.memberId != member.id) {
            return Result.failure(
                IllegalArgumentException("Loan does not belong to the specified member.")
            )
        }
        if (loan.groupId != group.id) {
            return Result.failure(
                IllegalArgumentException("Loan does not belong to the specified group.")
            )
        }
        return exportRepo.exportLoanAgreement(loan, member, group)
    }
}
