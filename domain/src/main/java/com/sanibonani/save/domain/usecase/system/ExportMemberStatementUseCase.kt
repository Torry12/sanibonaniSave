package com.sanibonani.save.domain.usecase.system

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.system.ExportRepository
import com.sanibonani.save.domain.utils.safeRunCatching
import java.io.File
import javax.inject.Inject

/**
 * One member's contribution history as a document the admin can hand over.
 */
class ExportMemberStatementUseCase @Inject constructor(
    private val exportRepository: ExportRepository,
    private val contributionRepository: ContributionRepository
) {
    /**
     * The book is read here, from the server, rather than passed in: a statement is presented as proof
     * of what a member paid, and cached rows cannot say which contributions the server has since booked
     * or whether the last one ever landed. A refusal fails the export instead of printing the cache.
     */
    suspend operator fun invoke(
        group: Group,
        member: Member,
        asPdf: Boolean
    ): Result<File> = safeRunCatching("ExportMemberStatement") {
        val memberId = requireNotNull(member.id) { "Cannot export a statement for a member the server has not saved." }
        val groupId = requireNotNull(group.id) { "Cannot export a statement for a group the server has not saved." }

        val contributions = contributionRepository.syncMemberContributions(memberId, groupId).getOrThrow()

        (if (asPdf) {
            exportRepository.exportContributionsToPdf(group, member, contributions)
        } else {
            exportRepository.exportContributionsToCsv(group, member, contributions)
        }).getOrThrow()
    }
}
