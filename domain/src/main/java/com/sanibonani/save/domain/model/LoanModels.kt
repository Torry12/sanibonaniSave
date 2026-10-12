@file:OptIn(ExperimentalSerializationApi::class)

package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

// ─────────────────────────────────────────────────────────────────────────────
//  LOAN
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
enum class AmortizationType {
    @SerialName("flat")     FLAT,
    @SerialName("reducing") REDUCING
}

@Serializable
@Parcelize
data class LoanRepaymentScheduleEntry(
    val installmentNumber: Int,
    val dueDate: String,
    val principal: Money,
    val interest: Money,
    val totalInstallment: Money,
    val remainingBalance: Money
) : Parcelable

@Serializable
@Parcelize
data class Loan(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("member_id")      val memberId: String = "",
    @SerialName("group_id")       val groupId: String = "",
    val amount: Money = Money.ZERO,
    @SerialName("interest_rate")  val interestRate: Double = 0.0,
    @SerialName("total_to_repay") val totalToRepay: Money = Money.ZERO,
    @SerialName("total_repaid")   val totalRepaid: Money = Money.ZERO,
    @SerialName("monthly_repayment") val monthlyRepayment: Money = Money.ZERO,
    @SerialName("start_date")     val startDate: String? = null,
    @SerialName("end_date")       val endDate: String? = null,
    @SerialName("next_payment_date") val nextPaymentDate: String? = null,
    @SerialName("contract_url")      val contractUrl: String? = null,
    @SerialName("surety_amount")    val suretyAmount: Money? = null,
    val status: LoanStatus = LoanStatus.PENDING,
    @SerialName("purpose")        val purpose: String? = null,
    @SerialName("reviewed_by")     val reviewedBy: String? = null,
    @SerialName("reviewed_at")     val reviewedAt: String? = null,
    @SerialName("admin_notes")     val adminNotes: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("amortization_type") val amortizationType: AmortizationType = AmortizationType.FLAT,
    @SerialName("effective_apr")     val effectiveApr: Double? = null,
    @Transient val repaymentSchedule: List<LoanRepaymentScheduleEntry> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")     val createdAt: String? = null
) : Parcelable {
    val balanceRemaining: Money get() = (totalToRepay - totalRepaid).let { if (it.isNegative) Money.ZERO else it }
    val progress: Float get() = if (totalToRepay.isPositive) (totalRepaid.ratio(totalToRepay)).toFloat() else 0f
}

@Serializable
enum class LoanStatus(val displayName: String) {
    @SerialName("pending")   PENDING("Pending Approval"),
    @SerialName("approved")  APPROVED("Approved"),
    @SerialName("active")    ACTIVE("Active"),
    @SerialName("partially_paid") PARTIALLY_PAID("Partially Paid"),
    @SerialName("completed") COMPLETED("Completed"),
    @SerialName("rejected")  REJECTED("Rejected"),
    @SerialName("overdue")   OVERDUE("Overdue"),
    @SerialName("cancelled") CANCELLED("Cancelled"),
    @SerialName("defaulted") DEFAULTED("Defaulted")
}

@Serializable
@Parcelize
data class LoanRepayment(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("loan_id")        val loanId: String = "",
    @SerialName("member_id")      val memberId: String = "",
    @SerialName("group_id")       val groupId: String = "",
    val amount: Money = Money.ZERO,
    @SerialName("paid_at")        val paidAt: String? = null,
    @SerialName("payment_method") val paymentMethod: PaymentMethod = PaymentMethod.BANK,
    @SerialName("transaction_id") val transactionId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")     val createdAt: String? = null
) : Parcelable

@Serializable
data class RecordRepaymentResult(
    val repayment: LoanRepayment,
    @SerialName("new_balance") val newBalance: Money
)
