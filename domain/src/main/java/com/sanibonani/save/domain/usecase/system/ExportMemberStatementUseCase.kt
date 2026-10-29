package com.sanibonani.save.domain.usecase.system

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.system.ExportRepository
import java.io.File
import javax.inject.Inject

/**
 * One member's contribution history as a document the admin can hand over.
 */
class ExportMemberStatementUseCase @Inject constructor(
    private val exportRepository: ExportRepository
) {
    suspend operator fun invoke(
        group: Group,
        member: Member,
        contributions: List<Contribution>,
        asPdf: Boolean
    ): Result<File> = if (asPdf) {
        exportRepository.exportContributionsToPdf(group, member, contributions)
    } else {
        exportRepository.exportContributionsToCsv(group, member, contributions)
    }
}
