package com.sanibonani.save.domain.usecase.credit
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.system.ExportRepository
import java.io.File
import javax.inject.Inject
class GenerateLoanContractUseCase @Inject constructor(
    private val exportRepo: ExportRepository
) {
    suspend operator fun invoke(loan: Loan, member: Member, group: Group): Result<File> {
        if (loan.memberId != member.id || loan.groupId != group.id) {
            return Result.failure(IllegalArgumentException("Inconsistent loan context"))
        }
        return exportRepo.exportLoanAgreement(loan, member, group)
    }
}
