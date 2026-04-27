package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.repository.ExportRepository
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.utils.FileDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class ExportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseRepo: SupabaseRepository
) : ExportRepository {

    override suspend fun exportPaymentsToCsv(group: Group, payments: List<Payment>, members: List<Member>): Result<File> = runCatching {
        val sanitizedGroupName = group.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Statement_${sanitizedGroupName}_${System.currentTimeMillis()}.csv"
        
        // Try external files dir first, fallback to internal files dir if needed
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir.let { File(it, "documents").apply { if (!exists()) mkdirs() } }
            
        val file = File(storageDir, fileName)
        
        val memberMap = members.associateBy { it.id }
        
        FileWriter(file).use { writer ->
            writer.append("Date,Member,Type,Amount,Method,Status,TransactionID\n")
            payments.sortedByDescending { it.createdAt }.forEach { p ->
                val memberName = memberMap[p.memberId]?.fullName ?: "Unknown"
                writer.append("${p.createdAt?.take(10)},")
                writer.append("\"$memberName\",")
                writer.append("${p.paymentType.name},")
                writer.append("${p.amount},")
                writer.append("${p.paymentMethod.name},")
                writer.append("${p.status.name},")
                writer.append("${p.transactionId ?: "N/A"}\n")
            }
        }
        file
    }

    override suspend fun exportPaymentsToPdf(
        group: Group,
        payments: List<Payment>,
        members: List<Member>
    ): Result<File> = runCatching {
        val sanitizedGroupName = group.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Group_Statement_${sanitizedGroupName}_${System.currentTimeMillis()}.pdf"

        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir.let { File(it, "documents").apply { if (!exists()) mkdirs() } }

        val file = File(storageDir, fileName)
        val memberMap = members.associateBy { it.id }

        val pageWidth = 595
        val pageHeight = 842
        val margin = 36
        val rowHeight = 16

        val titlePaint = Paint().apply {
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            textSize = 11f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            textSize = 9.5f
            isAntiAlias = true
        }

        val pdf = PdfDocument()
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        fun newPage(pageNo: Int): Pair<PdfDocument.Page, PdfDocument.PageInfo> {
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create()
            return pdf.startPage(info) to info
        }

        var pageNo = 1
        var (page, pageInfo) = newPage(pageNo)
        var canvas = page.canvas
        var y = margin

        fun drawHeader() {
            canvas.drawText("Group Payments Statement", margin.toFloat(), y.toFloat(), titlePaint)
            y += 22
            canvas.drawText("Group: ${group.name}", margin.toFloat(), y.toFloat(), subtitlePaint)
            y += rowHeight
            canvas.drawText("Generated: $generatedAt", margin.toFloat(), y.toFloat(), subtitlePaint)
            y += (rowHeight + 8)

            val c1 = margin
            val c2 = margin + 72
            val c3 = margin + 258
            val c4 = margin + 340
            val c5 = margin + 410
            val c6 = margin + 486

            canvas.drawText("Date", c1.toFloat(), y.toFloat(), headerPaint)
            canvas.drawText("Member", c2.toFloat(), y.toFloat(), headerPaint)
            canvas.drawText("Type", c3.toFloat(), y.toFloat(), headerPaint)
            canvas.drawText("Amount", c4.toFloat(), y.toFloat(), headerPaint)
            canvas.drawText("Method", c5.toFloat(), y.toFloat(), headerPaint)
            canvas.drawText("Status", c6.toFloat(), y.toFloat(), headerPaint)
            y += rowHeight
        }

        fun ensureSpace(required: Int) {
            if (y + required >= pageInfo.pageHeight - margin) {
                pdf.finishPage(page)
                pageNo += 1
                val next = newPage(pageNo)
                page = next.first
                pageInfo = next.second
                canvas = page.canvas
                y = margin
                drawHeader()
            }
        }

        drawHeader()
        val rows = payments.sortedByDescending { it.createdAt }
        if (rows.isEmpty()) {
            canvas.drawText("No payment records found.", margin.toFloat(), y.toFloat(), textPaint)
        } else {
            rows.forEach { p ->
                ensureSpace(rowHeight)
                val date = p.createdAt?.take(10) ?: "N/A"
                val memberName = (memberMap[p.memberId]?.fullName ?: "Unknown").take(24)
                val type = p.paymentType.displayName.take(12)
                val amount = "R%.2f".format(Locale.getDefault(), p.amount)
                val method = p.paymentMethod.displayName.take(10)
                val status = p.status.displayName.take(10)

                canvas.drawText(date, margin.toFloat(), y.toFloat(), textPaint)
                canvas.drawText(memberName, (margin + 72).toFloat(), y.toFloat(), textPaint)
                canvas.drawText(type, (margin + 258).toFloat(), y.toFloat(), textPaint)
                canvas.drawText(amount, (margin + 340).toFloat(), y.toFloat(), textPaint)
                canvas.drawText(method, (margin + 410).toFloat(), y.toFloat(), textPaint)
                canvas.drawText(status, (margin + 486).toFloat(), y.toFloat(), textPaint)
                y += rowHeight
            }
        }

        pdf.finishPage(page)
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()
        file
    }

    override suspend fun exportContributionsToCsv(group: Group, member: Member, contributions: List<Contribution>): Result<File> = runCatching {
        val sanitizedMemberName = member.fullName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Contributions_${sanitizedMemberName}_${System.currentTimeMillis()}.csv"
        
        // Try external files dir first, fallback to internal files dir if needed
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir.let { File(it, "documents").apply { if (!exists()) mkdirs() } }

        val file = File(storageDir, fileName)
        
        FileWriter(file).use { writer ->
            writer.append("Due Date,Paid Date,Amount,Status\n")
            contributions.sortedByDescending { it.dueDate }.forEach { c ->
                writer.append("${c.dueDate},")
                writer.append("${c.paidAt?.take(10) ?: "N/A"},")
                writer.append("${c.amount},")
                writer.append("${c.status.name}\n")
            }
        }
        file
    }

    override suspend fun exportContributionsToPdf(
        group: Group,
        member: Member,
        contributions: List<Contribution>
    ): Result<File> = runCatching {
        val sanitizedMemberName = member.fullName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Statement_${sanitizedMemberName}_${System.currentTimeMillis()}.pdf"

        // Try external files dir first, fallback to internal files dir if needed
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir.let { File(it, "documents").apply { if (!exists()) mkdirs() } }

        val file = File(storageDir, fileName)

        val pageWidth = 595 // A4 @ 72dpi (approx)
        val pageHeight = 842
        val margin = 40
        val lineHeight = 18
        val tableGap = 10

        val titlePaint = Paint().apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        val pdf = PdfDocument()

        fun newPage(pageNumber: Int): Pair<PdfDocument.Page, PdfDocument.PageInfo> {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            return pdf.startPage(pageInfo) to pageInfo
        }

        var pageNumber = 1
        var (page, pageInfo) = newPage(pageNumber)
        var canvas = page.canvas
        var y = margin

        fun ensureSpace(required: Int) {
            if (y + required >= pageInfo.pageHeight - margin) {
                pdf.finishPage(page)
                pageNumber += 1
                val next = newPage(pageNumber)
                page = next.first
                pageInfo = next.second
                canvas = page.canvas
                y = margin
            }
        }

        // Header
        canvas.drawText("Contribution Statement", margin.toFloat(), y.toFloat(), titlePaint)
        y += 26
        canvas.drawText("Group: ${group.name}", margin.toFloat(), y.toFloat(), subtitlePaint)
        y += lineHeight
        canvas.drawText("Member: ${member.fullName}", margin.toFloat(), y.toFloat(), subtitlePaint)
        y += lineHeight
        canvas.drawText("Generated: $now", margin.toFloat(), y.toFloat(), subtitlePaint)
        y += (lineHeight + tableGap)

        // Table header
        val col1 = margin
        val col2 = margin + 220
        val col3 = margin + 330
        val col4 = margin + 430

        canvas.drawText("Due Date", col1.toFloat(), y.toFloat(), headerPaint)
        canvas.drawText("Paid Date", col2.toFloat(), y.toFloat(), headerPaint)
        canvas.drawText("Amount", col3.toFloat(), y.toFloat(), headerPaint)
        canvas.drawText("Status", col4.toFloat(), y.toFloat(), headerPaint)
        y += (lineHeight)

        val sorted = contributions.sortedByDescending { it.dueDate }
        if (sorted.isEmpty()) {
            canvas.drawText("No contributions found.", margin.toFloat(), y.toFloat(), textPaint)
            y += lineHeight
        } else {
            sorted.forEach { c ->
                ensureSpace(lineHeight)
                val due = c.dueDate
                val paid = c.paidAt?.take(10) ?: "N/A"
                val amount = "R%.2f".format(Locale.getDefault(), c.amount)
                val status = c.status.displayName

                canvas.drawText(due, col1.toFloat(), y.toFloat(), textPaint)
                canvas.drawText(paid, col2.toFloat(), y.toFloat(), textPaint)
                canvas.drawText(amount, col3.toFloat(), y.toFloat(), textPaint)
                canvas.drawText(status, col4.toFloat(), y.toFloat(), textPaint)
                y += lineHeight
            }
        }

        pdf.finishPage(page)

        FileOutputStream(file).use { out ->
            pdf.writeTo(out)
        }
        pdf.close()

        file
    }

    override fun downloadStatementPdf(context: Context, groupId: String, memberId: String?) {
        var supabaseUrl = supabaseRepo.supabaseUrl
        if (!supabaseUrl.startsWith("http")) {
            supabaseUrl = "https://$supabaseUrl"
        }
        val baseUrl = "$supabaseUrl/functions/v1/generate-statement"
        val url = if (memberId != null) {
            "$baseUrl?group_id=$groupId&member_id=$memberId"
        } else {
            "$baseUrl?group_id=$groupId"
        }
        
        val fileName = if (memberId != null) "Member_Statement_${memberId.take(5)}.pdf" else "Group_Statement_${groupId.take(5)}.pdf"
        
        val headers = mutableMapOf<String, String>()
        supabaseRepo.accessToken?.let { token ->
            headers["Authorization"] = "Bearer $token"
        }

        FileDownloader.downloadFile(context, url, fileName, "application/pdf", headers)
    }

    override fun shareFile(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(intent, "Share Statement").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            com.sanibonani.save.data.logging.AppLogger.e("ExportRepo", "Share failed: ${e.message}", e)
        }
    }
}
