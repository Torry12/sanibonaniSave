package com.sanibonani.save.domain.repository.system

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Member
import java.io.File

/**
 * Repository for exporting data to CSV or PDF formats.
 */
interface ExportRepository {
    suspend fun exportContributionsToCsv(group: Group, member: Member, contributions: List<Contribution>): Result<File>

    /**
     * Generates a simple PDF statement locally (offline-capable) and returns the file.
     *
     * This avoids depending on a Supabase Edge Function for PDF generation.
     */
    suspend fun exportContributionsToPdf(
        group: Group,
        member: Member,
        contributions: List<Contribution>
    ): Result<File>

    /**
     * Generates a PDF loan agreement locally.
     *
     * Kept as the producer for [com.sanibonani.save.domain.repository.credit.LoanRepository.uploadLoanContract]:
     * the member UI can download `loan.contractUrl`, but nothing in the app generates it yet.
     */
    suspend fun exportLoanAgreement(
        loan: Loan,
        member: Member,
        group: Group
    ): Result<File>

    /**
     * Generates a PDF group constitution locally.
     */
    suspend fun exportGroupConstitution(
        group: Group
    ): Result<File>

    /**
     * Professional Ledger Exports
     */
    suspend fun exportLedgerToCsv(group: Group, entries: List<LedgerEntry>): Result<File>
    suspend fun exportLedgerToPdf(group: Group, entries: List<LedgerEntry>): Result<File>

    /**
     * Generates a PDF membership certificate/contract locally.
     */
    suspend fun exportMembershipContract(
        member: Member,
        group: Group
    ): Result<File>
}
