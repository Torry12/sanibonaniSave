package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Member
import java.io.File
import javax.inject.Inject

class GenerateLoanContractUseCase @Inject constructor() {
    suspend operator fun invoke(loan: Loan, member: Member, group: Group): Result<File> = runCatching {
        // This would normally use a PDF library or the ExportRepository.
        // For now, I'll create a placeholder text file or a mock PDF path.
        // Real implementation would likely go into ExportRepository.
        throw UnsupportedOperationException("Loan contract generation not yet implemented in ExportRepository")
    }
}
