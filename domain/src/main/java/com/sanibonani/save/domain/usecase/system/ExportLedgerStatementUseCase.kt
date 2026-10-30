package com.sanibonani.save.domain.usecase.system

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.repository.system.ExportRepository
import java.io.File
import javax.inject.Inject

/**
 * Turns a ledger into the statement document a member downloads. Generated on the device, so an
 * offline member still gets a statement.
 */
class ExportLedgerStatementUseCase @Inject constructor(
    private val exportRepository: ExportRepository
) {
    enum class Format { PDF, CSV }

    suspend operator fun invoke(
        group: Group,
        entries: List<LedgerEntry>,
        format: Format
    ): Result<File> = when (format) {
        Format.PDF -> exportRepository.exportLedgerToPdf(group, entries)
        Format.CSV -> exportRepository.exportLedgerToCsv(group, entries)
    }
}
