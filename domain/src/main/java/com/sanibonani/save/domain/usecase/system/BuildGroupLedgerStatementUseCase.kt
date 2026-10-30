package com.sanibonani.save.domain.usecase.system

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.system.ExportRepository
import com.sanibonani.save.domain.repository.treasury.LedgerRepository
import com.sanibonani.save.domain.utils.safeRunCatching
import java.io.File
import javax.inject.Inject

/**
 * The group's full ledger as a PDF statement.
 *
 * Unlike a member statement this cannot use rows the screen already holds: the whole group ledger
 * is read here so the document matches the server rather than a partly synced cache. A failed
 * export is reported as a failure, so a download that produced nothing cannot leave the caller
 * waiting on a document that never arrived.
 */
class BuildGroupLedgerStatementUseCase @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val exportRepository: ExportRepository
) {
    suspend operator fun invoke(group: Group): Result<File> {
        val groupId = group.id
            ?: return Result.failure(
                IllegalStateException("This group is not saved on the server yet. Please sync and try again.")
            )

        return safeRunCatching {
            val entries = ledgerRepository.getGroupLedger(groupId).getOrThrow()
            exportRepository.exportLedgerToPdf(group, entries).getOrThrow()
        }
    }
}
