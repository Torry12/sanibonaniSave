package com.sanibonani.save.domain.model.financial

import android.os.Parcelable
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.DocumentStatus
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

@Serializable
enum class PaymentStatus(val displayName: String) {
    @SerialName("pending")    PENDING("Pending"),
    @SerialName("processing") PROCESSING("Processing"),
    @SerialName("completed")  COMPLETED("Completed"),
    @SerialName("failed")     FAILED("Failed"),
    @SerialName("refunded")   REFUNDED("Refunded")
}

@Serializable
enum class PaymentType(val displayName: String) {
    @SerialName("joining_fee")    JOINING_FEE("Joining Fee"),
    @SerialName("contribution")   CONTRIBUTION("Monthly Contribution"),
    @SerialName("late_fee")       LATE_FEE("Late Fee"),
    @SerialName("platform_fee")   PLATFORM_FEE("Platform Fee"),
    @SerialName("claim")          CLAIM("Insurance Claim / Payout"),
    @SerialName("loan_repayment") LOAN_REPAYMENT("Loan Repayment"),
    @SerialName("loan_disbursement") LOAN_DISBURSEMENT("Loan Disbursement"),
    @SerialName("custom")         CUSTOM("Custom Payment"),
    @SerialName("registration")   REGISTRATION("Registration Fee")
}

@Serializable
enum class PaymentMethod(val displayName: String) {
    @SerialName("yoco")       YOCO("YoCo Card"),
    @SerialName("stitch")     STITCH("Instant EFT (Stitch)"),
    @SerialName("payfast")    PAYFAST("PayFast"),
    @SerialName("bank")       BANK("Bank Transfer"),
    @SerialName("cash")       CASH("Cash"),
    @SerialName("wallet")     WALLET("Group Wallet"),
    @SerialName("other")      OTHER("Other")
}

@Serializable
enum class ContributionStatus(val displayName: String) {
    @SerialName("paid")    PAID("Paid"),
    @SerialName("due")     DUE("Due"),
    @SerialName("overdue") OVERDUE("Overdue"),
    @SerialName("partial") PARTIAL("Partial")
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Parcelize
data class Contribution(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("member_id")            val memberId: String = "",
    @SerialName("group_id")             val groupId: String = "",
    @SerialName("policy_id")            val policyId: String? = null,
    val amount: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("created_at") val createdAt: String? = null,
    @SerialName("due_date")             val dueDate: String = "",
    @SerialName("paid_at")              val paidAt: String? = null,
    @SerialName("status")               val status: ContributionStatus = ContributionStatus.DUE,
    @SerialName("type")                 val type: String = "contribution",
    @SerialName("payment_method")       val paymentMethod: String = "bank",
    @SerialName("transaction_id")       val transactionId: String? = null,
    @SerialName("late_fees_applied")    val lateFeesApplied: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null
) : Parcelable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Parcelize
data class Payment(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("member_id")      val memberId: String = "",
    @SerialName("group_id")       val groupId: String = "",
    val amount: Double = 0.0,
    @SerialName("payment_type")   val paymentType: PaymentType = PaymentType.CONTRIBUTION,
    @SerialName("payment_method") val paymentMethod: PaymentMethod = PaymentMethod.BANK,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("status")         val status: PaymentStatus = PaymentStatus.PENDING,
    @SerialName("processed_at")   val processedAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null
) : Parcelable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Parcelize
data class PlatformFee(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id")             val groupId: String = "",
    @SerialName("fee_type")             val feeType: String = "monthly",
    val amount: Double = 0.0,
    @SerialName("due_date")             val dueDate: String? = null,
    @SerialName("status")               val status: AdminFeeState = AdminFeeState.DUE,
    @SerialName("paid_at")              val paidAt: String? = null,
    @SerialName("transaction_id")       val transactionId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null
) : Parcelable

@Serializable
@Parcelize
data class Beneficiary(
    @SerialName("group_id")       val groupId: String = "",
    @SerialName("member_id")      val memberId: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("full_name")      val fullName: String = "",
    @SerialName("id_number")      val idNumber: String? = null,
    val relationship: String? = null,
    @SerialName("date_of_birth")  val dateOfBirth: String? = null,
    @SerialName("is_over_65")     val isOver65: Boolean = false,
    @SerialName("document_url")   val documentUrl: String? = null,
    @SerialName("face_photo_url") val facePhotoUrl: String? = null,
    @SerialName("document_status") val documentStatus: DocumentStatus = DocumentStatus.PENDING,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null
) : Parcelable
