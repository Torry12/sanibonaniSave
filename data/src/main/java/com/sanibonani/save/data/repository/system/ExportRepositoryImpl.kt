package com.sanibonani.save.data.repository.system

import com.sanibonani.save.data.utils.PdfGenerator
import com.sanibonani.save.domain.repository.system.ExportRepository
import android.content.Context
import android.os.Environment
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching

class ExportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseRepo: SupabaseRepository
) : ExportRepository {

    override suspend fun exportPaymentsToCsv(group: Group, payments: List<Payment>, members: List<Member>): Result<File> = safeRunCatching {
        val sanitizedGroupName = group.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Statement_${sanitizedGroupName}_${System.currentTimeMillis()}.csv"

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
    ): Result<File> = safeRunCatching {
        val sanitizedGroupName = group.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Group_Statement_${sanitizedGroupName}_${System.currentTimeMillis()}.pdf"
        val file = File(getDocumentsDir(), fileName)

        val memberMap = members.associateBy { it.id }
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        val generator = PdfGenerator()
        generator.startNewPage()
        generator.drawText("Group Payments Statement", isTitle = true, spacing = 30f)
        generator.drawText("Group: ${group.name}", isBold = true)
        generator.drawText("Generated: $generatedAt", spacing = 30f)

        val offsets = listOf(40f, 112f, 298f, 380f, 450f, 526f)
        generator.drawRow(listOf("Date", "Member", "Type", "Amount", "Method", "Status"), offsets, isHeader = true)
        generator.drawLine()

        if (payments.isEmpty()) {
            generator.drawText("No payment records found.")
        } else {
            payments.sortedByDescending { it.createdAt }.forEach { p ->
                val date = p.createdAt?.take(10) ?: "N/A"
                val memberName = (memberMap[p.memberId]?.fullName ?: "Unknown").take(24)
                val amount = p.amount.format()

                generator.drawRow(
                    listOf(date, memberName, p.paymentType.displayName, amount, p.paymentMethod.displayName, p.status.displayName),
                    offsets
                )
            }
        }

        generator.save(file).getOrThrow()
    }

    private fun getDocumentsDir(): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
    }

    override suspend fun exportContributionsToCsv(group: Group, member: Member, contributions: List<Contribution>): Result<File> = safeRunCatching {
        val sanitizedMemberName = member.fullName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Contributions_${sanitizedMemberName}_${System.currentTimeMillis()}.csv"

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
    ): Result<File> = safeRunCatching {
        val sanitizedMemberName = member.fullName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Statement_${sanitizedMemberName}_${System.currentTimeMillis()}.pdf"
        val file = File(getDocumentsDir(), fileName)

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val generator = PdfGenerator()
        generator.startNewPage()
        generator.drawText("Contribution Statement", isTitle = true, spacing = 26f)
        generator.drawText("Group: ${group.name}")
        generator.drawText("Member: ${member.fullName}")
        generator.drawText("Generated: $now", spacing = 36f)

        val offsets = listOf(40f, 260f, 370f, 470f)
        generator.drawRow(listOf("Due Date", "Paid Date", "Amount", "Status"), offsets, isHeader = true)
        generator.drawLine()

        val sorted = contributions.sortedByDescending { it.dueDate }
        if (sorted.isEmpty()) {
            generator.drawText("No contributions found.")
        } else {
            sorted.forEach { c ->
                val amount = c.amount.format()
                generator.drawRow(
                    listOf(c.dueDate, c.paidAt?.take(10) ?: "N/A", amount, c.status.displayName),
                    offsets
                )
            }
        }

        generator.save(file).getOrThrow()
    }

    override suspend fun exportLoanAgreement(
        loan: Loan,
        member: Member,
        group: Group
    ): Result<File> = safeRunCatching {
        val sanitizedMemberName = member.fullName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Loan_Agreement_${sanitizedMemberName}_${System.currentTimeMillis()}.pdf"
        val file = File(getDocumentsDir(), fileName)

        val generator = PdfGenerator()
        generator.startNewPage()
        generator.drawText("LOAN AGREEMENT", isTitle = true, spacing = 40f)
        generator.drawText("Group: ${group.name}", isBold = true)
        generator.drawText("Borrower: ${member.fullName}", isBold = true)
        generator.drawText("ID Number: ${member.idNumber ?: "N/A"}", spacing = 40f)

        generator.drawText("Loan Details:", isBold = true)
        generator.drawText("Principal Amount: ${loan.amount.format()}")
        generator.drawText("Interest Rate: ${loan.interestRate}%")
        generator.drawText("Total to Repay: ${loan.totalToRepay.format()}")
        generator.drawText("Monthly Installment: ${loan.monthlyRepayment.format()}", spacing = 40f)

        generator.drawText("Terms and Conditions:", isBold = true)
        val terms = listOf(
            "1. The borrower agrees to repay the total amount in monthly installments.",
            "2. Repayments are due on the ${group.paymentDueDay}th of each month.",
            "3. Late payments may incur additional fees as per group policy.",
            "4. This loan is subject to the constitution of ${group.name}.",
            "5. The borrower acknowledges receipt of the funds and agrees to the interest rate specified."
        )
        terms.forEach { generator.drawParagraph(it) }

        generator.checkSpace(100f)
        generator.drawText("", spacing = 50f) // Spacing for signatures
        val x2 = 320f
        generator.drawText("__________________________", spacing = 0f)
        generator.drawText("__________________________", x = x2)
        generator.drawText("Borrower Signature", spacing = 0f)
        generator.drawText("Admin Signature", x = x2)

        generator.save(file).getOrThrow()
    }

    override suspend fun exportGroupConstitution(group: Group): Result<File> = safeRunCatching {
        val sanitizedGroupName = group.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Constitution_${sanitizedGroupName}_${System.currentTimeMillis()}.pdf"
        val file = File(getDocumentsDir(), fileName)

        val generator = PdfGenerator()
        generator.startNewPage()
        generator.drawText("CONSTITUTION OF", isBold = true, spacing = 30f)
        generator.drawText(group.name.uppercase(), isTitle = true, spacing = 50f)

        val sections = listOf(
            "1. NAME AND TYPE" to "The name of the group is ${group.name}, which is a ${group.type.displayName}.",
            "2. OBJECTIVES" to "The primary objective of the group is to promote collective savings and financial security for its members.",
            "3. MEMBERSHIP" to "Membership is open to individuals who meet the group criteria. Maximum members allowed: ${group.maxMembers}.",
            "4. CONTRIBUTIONS" to "Each member shall contribute ${group.monthlyContribution.format()} per month, due on the ${group.paymentDueDay}th of each month.",
            "5. LATE FEES" to "A late fee of ${group.lateFee.format()} will be applied if payments are not made within ${group.lateFeeGraceDays} days of the due date.",
            "6. LOANS" to (let {
                val maxAmount = group.loanMaxAmount
                if (maxAmount != null && maxAmount.isPositive)
                    "Members may apply for loans up to ${maxAmount.format()} at an interest rate of ${group.loanInterestRate}%."
                else "Loan facilities are subject to group approval and available funds."
            }),
            "7. AMENDMENTS" to "This constitution may be amended by a two-thirds majority vote of all active members."
        )

        sections.forEach { (title, content) ->
            generator.drawText(title, isBold = true)
            generator.drawParagraph(content, spacing = 20f)
            generator.drawText("", spacing = 20f) // Paragraph gap
        }

        generator.save(file).getOrThrow()
    }

    override suspend fun exportMembershipContract(
        member: Member,
        group: Group
    ): Result<File> = safeRunCatching {
        val sanitizedMemberName = member.fullName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Membership_Contract_${sanitizedMemberName}_${System.currentTimeMillis()}.pdf"
        val file = File(getDocumentsDir(), fileName)

        val generator = PdfGenerator()
        generator.startNewPage()
        generator.drawText("MEMBERSHIP CERTIFICATE", isTitle = true, spacing = 40f)
        generator.drawText("Group: ${group.name}", isBold = true)
        generator.drawText("Member: ${member.fullName}", isBold = true, spacing = 40f)

        val text = """
            This document confirms that ${member.fullName} is a registered member in good standing of the ${group.name} ${group.type.displayName}.

            Membership Details:
            - Joined Date: ${member.joinedAt?.take(10) ?: "N/A"}
            - ID Number: ${member.idNumber ?: "N/A"}
            - Monthly Contribution: ${group.monthlyContribution.format()}
            - Payment Due Day: ${group.paymentDueDay}th of each month

            By maintaining this membership, the member agrees to abide by the constitution and rules of the group.
        """.trimIndent()

        text.split("\n").forEach { line ->
            if (line.isBlank()) {
                generator.drawText("", spacing = 15f)
            } else {
                generator.drawParagraph(line)
            }
        }

        generator.checkSpace(80f)
        generator.drawText("", spacing = 60f)
        generator.drawText("__________________________")
        generator.drawText("Authorized Group Official")

        generator.save(file).getOrThrow()
    }

    override suspend fun exportLedgerToCsv(group: Group, entries: List<LedgerEntry>): Result<File> = safeRunCatching {
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

    override suspend fun exportLedgerToPdf(group: Group, entries: List<LedgerEntry>): Result<File> = safeRunCatching {
        val sanitizedGroupName = group.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "Ledger_${sanitizedGroupName}_${System.currentTimeMillis()}.pdf"
        val file = File(getDocumentsDir(), fileName)

        val generator = PdfGenerator()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        generator.startNewPage()
        generator.drawText("Group Financial Ledger", isTitle = true, spacing = 30f)
        generator.drawText("Group: ${group.name}")
        generator.drawText("Generated: $now", spacing = 40f)

        val offsets = listOf(40f, 120f, 320f, 400f, 480f)
        generator.drawRow(listOf("Date", "Description", "Category", "Amount", "Balance"), offsets, isHeader = true)
        generator.drawLine()

        if (entries.isEmpty()) {
            generator.drawText("No ledger entries found.")
        } else {
            entries.sortedByDescending { it.createdAt }.forEach { e ->
                val date = e.createdAt?.take(16)?.replace("T", " ") ?: "N/A"
                val amt = e.amount.format()
                val bal = e.balanceAfter.format()

                generator.drawRow(
                    listOf(date, e.description.take(35), e.category.take(12), amt, bal),
                    offsets
                )
            }
        }

        generator.save(file).getOrThrow()
    }
}
