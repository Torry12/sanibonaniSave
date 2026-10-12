@file:OptIn(ExperimentalSerializationApi::class)

package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

// ─────────────────────────────────────────────────────────────────────────────
//  PAYMENT
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
@Parcelize
data class Payment(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("member_id")      val memberId: String = "",
    @SerialName("group_id")       val groupId: String = "",
    val amount: Money = Money.ZERO,
    @SerialName("payment_type")   val paymentType: PaymentType = PaymentType.CONTRIBUTION,
    @SerialName("payment_method") val paymentMethod: PaymentMethod = PaymentMethod.BANK,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("status")         val status: PaymentStatus = PaymentStatus.PENDING,
    @SerialName("processed_at")   val processedAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")     val createdAt: String? = null
) : Parcelable

@Serializable
enum class PaymentType(val displayName: String) {
    @SerialName("joining_fee")    JOINING_FEE("Joining Fee"),
    @SerialName("contribution")   CONTRIBUTION("Monthly Contribution"),
    @SerialName("late_fee")       LATE_FEE("Late Fee"),
    @SerialName("platform_fee")   PLATFORM_FEE("Platform Fee"),
    @SerialName("claim")          CLAIM("Insurance Claim / Payout"),
    @SerialName("loan_repayment") LOAN_REPAYMENT("Loan Repayment"),
    @SerialName("loan_disbursement") LOAN_DISBURSEMENT("Loan Disbursement"),
    @SerialName("loan_settlement_extraction") LOAN_SETTLEMENT_EXTRACTION("Loan Settlement Extraction"),
    @SerialName("custom")         CUSTOM("Custom Payment"),
    @SerialName("registration")   REGISTRATION("Registration Fee")
}

@Serializable
enum class PaymentMethod(val displayName: String) {
    @SerialName("yoco")       YOCO("YoCo Card"),
    @SerialName("stitch")     STITCH("Instant EFT (Stitch)"),
    @SerialName("payfast")    PAYFAST("PayFast"),
    @SerialName("bank")       BANK("Bank Transfer"),
    @SerialName("sim_bank")   SIMULATED_BANK("Simulated Bank (Testing)"),
    @SerialName("cash")       CASH("Cash"),
    @SerialName("wallet")     WALLET("Group Wallet"),
    @SerialName("other")      OTHER("Other")
}

@Serializable
enum class PaymentStatus(val displayName: String) {
    @SerialName("pending")    PENDING("Pending"),
    @SerialName("processing") PROCESSING("Processing"),
    @SerialName("completed")  COMPLETED("Completed"),
    @SerialName("failed")     FAILED("Failed"),
    @SerialName("refunded")   REFUNDED("Refunded")
}

@Serializable
enum class PaymentDestination {
    @SerialName("group") GROUP,
    @SerialName("platform") PLATFORM
}

// ─────────────────────────────────────────────────────────────────────────────
//  PAYMENT ALLOCATION
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
@Parcelize
data class PaymentAllocation(
    val type: String, // 'member_fee', 'late_fee', 'loan_repayment', 'contribution', 'joining_fee'
    val amount: Money,
    @SerialName("due_date")  val dueDate: String,
    val description: String,
    @SerialName("loan_id")   val loanId: String? = null
) : Parcelable

