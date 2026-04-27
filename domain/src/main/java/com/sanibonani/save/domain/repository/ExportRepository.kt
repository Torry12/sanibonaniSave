package com.sanibonani.save.domain.repository

import android.content.Context
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Group
import java.io.File

/**
 * Repository for exporting data to CSV or PDF formats.
 */
interface ExportRepository {
    suspend fun exportPaymentsToCsv(group: Group, payments: List<Payment>, members: List<Member>): Result<File>
    suspend fun exportPaymentsToPdf(group: Group, payments: List<Payment>, members: List<Member>): Result<File>
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
    ): Result<File> = Result.failure(UnsupportedOperationException("PDF export not implemented"))

    fun downloadStatementPdf(context: Context, groupId: String, memberId: String? = null)
    fun shareFile(context: Context, file: File)
}
