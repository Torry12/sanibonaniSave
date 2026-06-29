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
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.utils.FileDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import com.sanibonani.save.data.utils.logAndGetMessage

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

        var pageNo = 1
        var currentPage: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y = 0f

        fun drawHeader(cv: android.graphics.Canvas) {
            var localY = margin.toFloat()
            cv.drawText("Group Payments Statement", margin.toFloat(), localY, titlePaint)
            localY += 22
            cv.drawText("Group: ${group.name}", margin.toFloat(), localY, subtitlePaint)
            localY += rowHeight
            cv.drawText("Generated: $generatedAt", margin.toFloat(), localY, subtitlePaint)
            localY += (rowHeight + 8)

            val c1 = margin
            val c2 = margin + 72
            val c3 = margin + 258
            val c4 = margin + 340
            val c5 = margin + 410
            val c6 = margin + 486

            cv.drawText("Date", c1.toFloat(), localY, headerPaint)
            cv.drawText("Member", c2.toFloat(), localY, headerPaint)
            cv.drawText("Type", c3.toFloat(), localY, headerPaint)
            cv.drawText("Amount", c4.toFloat(), localY, headerPaint)
            cv.drawText("Method", c5.toFloat(), localY, headerPaint)
            cv.drawText("Status", c6.toFloat(), localY, headerPaint)
            y = localY + rowHeight
        }

        fun startNewPage() {
            currentPage?.let { pdf.finishPage(it) }
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo++).create()
            val page = pdf.startPage(pageInfo)
            currentPage = page
            canvas = page.canvas
            drawHeader(page.canvas)
        }

        startNewPage()
        val rows = payments.sortedByDescending { it.createdAt }
        if (rows.isEmpty()) {
            canvas?.drawText("No payment records found.", margin.toFloat(), y, textPaint)
        } else {
            rows.forEach { p ->
                if (y + rowHeight >= pageHeight - margin) {
                    startNewPage()
                }
                
                val date = p.createdAt?.take(10) ?: "N/A"
                val memberName = (memberMap[p.memberId]?.fullName ?: "Unknown").take(24)
                val type = p.paymentType.displayName.take(12)
                val amount = "R%.2f".format(Locale.getDefault(), p.amount)
                val method = p.paymentMethod.displayName.take(10)
                val status = p.status.displayName.take(10)

                canvas?.drawText(date, margin.toFloat(), y, textPaint)
                canvas?.drawText(memberName, (margin + 72).toFloat(), y, textPaint)
                canvas?.drawText(type, (margin + 258).toFloat(), y, textPaint)
                canvas?.drawText(amount, (margin + 340).toFloat(), y, textPaint)
                canvas?.drawText(method, (margin + 410).toFloat(), y, textPaint)
                canvas?.drawText(status, (margin + 486).toFloat(), y, textPaint)
                y += rowHeight
            }
        }

        currentPage?.let { pdf.finishPage(it) }
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

        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir.let { File(it, "documents").apply { if (!exists()) mkdirs() } }

        val file = File(storageDir, fileName)

        val pageWidth = 595
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
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            textSize = 11f
            isAntiAlias = true
        }

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val pdf = PdfDocument()

        var pageNumber = 1
        var currentPage: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y = 0f

        fun drawHeader(cv: android.graphics.Canvas) {
            var localY = margin.toFloat()
            cv.drawText("Contribution Statement", margin.toFloat(), localY, titlePaint)
            localY += 26
            cv.drawText("Group: ${group.name}", margin.toFloat(), localY, subtitlePaint)
            localY += lineHeight
            cv.drawText("Member: ${member.fullName}", margin.toFloat(), localY, subtitlePaint)
            localY += lineHeight
            cv.drawText("Generated: $now", margin.toFloat(), localY, subtitlePaint)
            localY += (lineHeight + tableGap)

            val col1 = margin
            val col2 = margin + 220
            val col3 = margin + 330
            val col4 = margin + 430

            cv.drawText("Due Date", col1.toFloat(), localY, headerPaint)
            cv.drawText("Paid Date", col2.toFloat(), localY, headerPaint)
            cv.drawText("Amount", col3.toFloat(), localY, headerPaint)
            cv.drawText("Status", col4.toFloat(), localY, headerPaint)
            y = localY + lineHeight
        }

        fun startNewPage() {
            currentPage?.let { pdf.finishPage(it) }
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
            val page = pdf.startPage(pageInfo)
            currentPage = page
            canvas = page.canvas
            drawHeader(page.canvas)
        }

        startNewPage()
        val sorted = contributions.sortedByDescending { it.dueDate }
        if (sorted.isEmpty()) {
            canvas?.drawText("No contributions found.", margin.toFloat(), y, textPaint)
        } else {
            sorted.forEach { c ->
                if (y + lineHeight >= pageHeight - margin) {
                    startNewPage()
                }
                
                val due = c.dueDate
                val paid = c.paidAt?.take(10) ?: "N/A"
                val amount = "R%.2f".format(Locale.getDefault(), c.amount)
                val status = c.status.displayName

                canvas?.drawText(due, margin.toFloat(), y, textPaint)
                canvas?.drawText(paid, (margin + 220).toFloat(), y, textPaint)
                canvas?.drawText(amount, (margin + 330).toFloat(), y, textPaint)
                canvas?.drawText(status, (margin + 430).toFloat(), y, textPaint)
                y += lineHeight
            }
        }

        currentPage?.let { pdf.finishPage(it) }
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()
        file
    }

    override suspend fun exportLoanAgreement(
        loan: Loan,
        member: Member,
        group: Group
    ): Result<File> = runCatching {
        val sanitizedMemberName = member.fullName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Loan_Agreement_${sanitizedMemberName}_${System.currentTimeMillis()}.pdf"

        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir.let { File(it, "documents").apply { if (!exists()) mkdirs() } }

        val file = File(storageDir, fileName)

        val pageWidth = 595
        val pageHeight = 842
        val margin = 50
        val lineHeight = 20

        val titlePaint = Paint().apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val pdf = PdfDocument()
        var pageNo = 1
        var currentPage: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y = 0f
        
        fun drawHeader(cv: android.graphics.Canvas) {
            var localY = margin.toFloat()
            cv.drawText("LOAN AGREEMENT", margin.toFloat(), localY, titlePaint)
            localY += 40

            cv.drawText("Group: ${group.name}", margin.toFloat(), localY, headerPaint)
            localY += lineHeight
            cv.drawText("Borrower: ${member.fullName}", margin.toFloat(), localY, headerPaint)
            localY += lineHeight
            cv.drawText("ID Number: ${member.idNumber ?: "N/A"}", margin.toFloat(), localY, textPaint)
            y = localY + 30
        }

        fun startNewPage() {
            currentPage?.let { pdf.finishPage(it) }
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo++).create()
            val page = pdf.startPage(pageInfo)
            currentPage = page
            canvas = page.canvas
            drawHeader(page.canvas)
        }

        startNewPage()

        canvas?.drawText("Loan Details:", margin.toFloat(), y, headerPaint)
        y += lineHeight
        canvas?.drawText("Principal Amount: R${"%.2f".format(loan.amount)}", margin.toFloat(), y, textPaint)
        y += lineHeight
        canvas?.drawText("Interest Rate: ${loan.interestRate}%", margin.toFloat(), y, textPaint)
        y += lineHeight
        canvas?.drawText("Total to Repay: R${"%.2f".format(loan.totalToRepay)}", margin.toFloat(), y, textPaint)
        y += lineHeight
        canvas?.drawText("Monthly Installment: R${"%.2f".format(loan.monthlyRepayment)}", margin.toFloat(), y, textPaint)
        y += 30

        val terms = listOf(
            "1. The borrower agrees to repay the total amount in monthly installments.",
            "2. Repayments are due on the ${group.paymentDueDay}th of each month.",
            "3. Late payments may incur additional fees as per group policy.",
            "4. This loan is subject to the constitution of ${group.name}.",
            "5. The borrower acknowledges receipt of the funds and agrees to the interest rate specified."
        )

        canvas?.drawText("Terms and Conditions:", margin.toFloat(), y, headerPaint)
        y += lineHeight
        terms.forEach { term ->
            if (y + lineHeight >= pageHeight - margin) {
                startNewPage()
            }
            canvas?.drawText(term, margin.toFloat(), y, textPaint)
            y += lineHeight
        }

        y += 50
        if (y + lineHeight * 2 >= pageHeight - margin) {
            startNewPage()
        }
        canvas?.drawText("__________________________", margin.toFloat(), y, textPaint)
        canvas?.drawText("__________________________", (pageWidth / 2 + 20).toFloat(), y, textPaint)
        y += lineHeight
        canvas?.drawText("Borrower Signature", margin.toFloat(), y, textPaint)
        canvas?.drawText("Admin Signature", (pageWidth / 2 + 20).toFloat(), y, textPaint)

        currentPage?.let { pdf.finishPage(it) }
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()
        file
    }

    override suspend fun exportGroupConstitution(group: Group): Result<File> = runCatching {
        val sanitizedGroupName = group.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Constitution_${sanitizedGroupName}_${System.currentTimeMillis()}.pdf"

        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir.let { File(it, "documents").apply { if (!exists()) mkdirs() } }

        val file = File(storageDir, fileName)

        val pageWidth = 595
        val pageHeight = 842
        val margin = 50
        val lineHeight = 20

        val titlePaint = Paint().apply {
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val pdf = PdfDocument()
        var pageNo = 1
        var currentPage: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y = 0f

        fun drawHeader(cv: android.graphics.Canvas) {
            var localY = margin.toFloat()
            cv.drawText("CONSTITUTION OF", margin.toFloat(), localY, headerPaint)
            localY += 30
            cv.drawText(group.name.uppercase(), margin.toFloat(), localY, titlePaint)
            y = localY + 50
        }

        fun startNewPage() {
            currentPage?.let { pdf.finishPage(it) }
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo++).create()
            val page = pdf.startPage(pageInfo)
            currentPage = page
            canvas = page.canvas
            drawHeader(page.canvas)
        }

        startNewPage()

        val sections = listOf(
            "1. NAME AND TYPE" to "The name of the group is ${group.name}, which is a ${group.type.displayName}.",
            "2. OBJECTIVES" to "The primary objective of the group is to promote collective savings and financial security for its members.",
            "3. MEMBERSHIP" to "Membership is open to individuals who meet the group criteria. Maximum members allowed: ${group.maxMembers}.",
            "4. CONTRIBUTIONS" to "Each member shall contribute R${"%.2f".format(group.monthlyContribution)} per month, due on the ${group.paymentDueDay}th of each month.",
            "5. LATE FEES" to "A late fee of R${"%.2f".format(group.lateFee)} will be applied if payments are not made within ${group.lateFeeGraceDays} days of the due date.",
            "6. LOANS" to (let {
                val maxAmount = group.loanMaxAmount
                if (maxAmount != null && maxAmount > 0) 
                    "Members may apply for loans up to R${"%.2f".format(maxAmount)} at an interest rate of ${group.loanInterestRate}%." 
                else "Loan facilities are subject to group approval and available funds."
            }),
            "7. AMENDMENTS" to "This constitution may be amended by a two-thirds majority vote of all active members."
        )

        sections.forEach { (title, content) ->
            if (y + lineHeight * 2 >= pageHeight - margin) {
                startNewPage()
            }
            canvas?.drawText(title, margin.toFloat(), y, headerPaint)
            y += lineHeight
            
            val words = content.split(" ")
            var line = ""
            words.forEach { word ->
                val testLine = if (line.isEmpty()) word else "$line $word"
                if (textPaint.measureText(testLine) < pageWidth - (margin * 2)) {
                    line = testLine
                } else {
                    if (y + lineHeight >= pageHeight - margin) {
                        startNewPage()
                    }
                    canvas?.drawText(line, margin.toFloat(), y, textPaint)
                    y += lineHeight
                    line = word
                }
            }
            if (y + lineHeight >= pageHeight - margin) {
                startNewPage()
            }
            canvas?.drawText(line, margin.toFloat(), y, textPaint)
            y += 40
        }

        currentPage?.let { pdf.finishPage(it) }
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()
        file
    }

    override suspend fun exportLedgerToCsv(group: Group, entries: List<com.sanibonani.save.domain.model.LedgerEntry>): Result<File> = runCatching {
        val sanitizedGroupName = group.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Ledger_${sanitizedGroupName}_${System.currentTimeMillis()}.csv"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir.let { File(it, "documents").apply { if (!exists()) mkdirs() } }
        val file = File(storageDir, fileName)

        FileWriter(file).use { writer ->
            writer.append("Date,Description,Category,Amount,Balance After\n")
            entries.sortedByDescending { it.createdAt }.forEach { e ->
                writer.append("${e.createdAt?.take(16)?.replace("T", " ")},")
                writer.append("\"${e.description}\",")
                writer.append("${e.category},")
                writer.append("${e.amount},")
                writer.append("${e.balanceAfter}\n")
            }
        }
        file
    }

    override suspend fun exportLedgerToPdf(group: Group, entries: List<com.sanibonani.save.domain.model.LedgerEntry>): Result<File> = runCatching {
        val sanitizedGroupName = group.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Ledger_${sanitizedGroupName}_${System.currentTimeMillis()}.pdf"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir.let { File(it, "documents").apply { if (!exists()) mkdirs() } }
        val file = File(storageDir, fileName)

        val pageWidth = 595
        val pageHeight = 842
        val margin = 40
        val rowHeight = 20

        val titlePaint = Paint().apply { textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        val subtitlePaint = Paint().apply { textSize = 11f; isAntiAlias = true }
        val headerPaint = Paint().apply { textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        val textPaint = Paint().apply { textSize = 9f; isAntiAlias = true }

        val pdf = PdfDocument()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        var pageNo = 1
        var currentPage: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y = 0f

        fun drawHeader(cv: android.graphics.Canvas) {
            var localY = margin.toFloat()
            cv.drawText("Group Financial Ledger", margin.toFloat(), localY, titlePaint)
            localY += 24
            cv.drawText("Group: ${group.name}", margin.toFloat(), localY, subtitlePaint)
            localY += 16
            cv.drawText("Generated: $now", margin.toFloat(), localY, subtitlePaint)
            localY += 30

            val col1 = margin
            val col2 = margin + 80
            val col3 = margin + 280
            val col4 = margin + 360
            val col5 = margin + 440

            cv.drawText("Date", col1.toFloat(), localY, headerPaint)
            cv.drawText("Description", col2.toFloat(), localY, headerPaint)
            cv.drawText("Category", col3.toFloat(), localY, headerPaint)
            cv.drawText("Amount", col4.toFloat(), localY, headerPaint)
            cv.drawText("Balance", col5.toFloat(), localY, headerPaint)
            y = localY + rowHeight
        }

        fun startNewPage() {
            currentPage?.let { pdf.finishPage(it) }
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo++).create()
            val page = pdf.startPage(pageInfo)
            currentPage = page
            canvas = page.canvas
            drawHeader(page.canvas)
        }

        startNewPage()
        if (entries.isEmpty()) {
            canvas?.drawText("No ledger entries found.", margin.toFloat(), y, textPaint)
        } else {
            entries.sortedByDescending { it.createdAt }.forEach { e ->
                if (y + rowHeight >= pageHeight - margin) startNewPage()
                
                val date = e.createdAt?.take(16)?.replace("T", " ") ?: "N/A"
                val desc = e.description.take(40)
                val cat = e.category.take(15)
                val amt = "R%.2f".format(Locale.getDefault(), e.amount)
                val bal = "R%.2f".format(Locale.getDefault(), e.balanceAfter)

                canvas?.drawText(date, margin.toFloat(), y, textPaint)
                canvas?.drawText(desc, (margin + 80).toFloat(), y, textPaint)
                canvas?.drawText(cat, (margin + 280).toFloat(), y, textPaint)
                canvas?.drawText(amt, (margin + 360).toFloat(), y, textPaint)
                canvas?.drawText(bal, (margin + 440).toFloat(), y, textPaint)
                y += rowHeight
            }
        }

        currentPage?.let { pdf.finishPage(it) }
        FileOutputStream(file).use { pdf.writeTo(it) }
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
            
            val extension = file.extension.lowercase()
            val mimeType = when (extension) {
                "csv" -> "text/csv"
                "pdf" -> "application/pdf"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                else -> "application/octet-stream"
            }

            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(intent, "Share Document").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            val userMsg = e.logAndGetMessage("ExportRepo")
            com.sanibonani.save.data.logging.AppLogger.e("ExportRepo", "Share failed: $userMsg", e)
        }
    }
}
