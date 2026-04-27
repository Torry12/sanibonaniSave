@file:OptIn(ExperimentalSerializationApi::class)

package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


// ─────────────────────────────────────────────────────────────────────────────
//  GROUP
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
@Parcelize
data class Group(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    val name: String = "",
    val type: GroupType = GroupType.OTHER,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val province: String? = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val city: String? = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val township: String? = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val description: String? = "",
    @SerialName("logo_emoji")             val logoEmoji: String = "🤝",
    @SerialName("joining_fee")            val joiningFee: Double = 0.0,
    @SerialName("monthly_contribution")   val monthlyContribution: Double = 0.0,
    @SerialName("late_fee")               val lateFee: Double = 0.0,
    @SerialName("late_fee_grace_days")    val lateFeeGraceDays: Int = 5,
    @SerialName("probation_months")       val probationMonths: Int = 3,
    @SerialName("payment_due_day")        val paymentDueDay: Int = 28,
    @SerialName("max_members")            val maxMembers: Int = 50,
    @SerialName("current_members")        val currentMembers: Int = 0,
    @SerialName("is_public")              val isPublic: Boolean = true,
    @SerialName("allow_partial_payment")  val allowPartialPayment: Boolean = false,
    @SerialName("auto_suspend_after")     val autoSuspendAfter: Int = 2,
    @SerialName("bank_name")              val bankName: String? = null,
    @SerialName("account_number")         val accountNumber: String? = null,
    @SerialName("branch_code")            val branchCode: String? = null,
    @SerialName("account_type")           val accountType: String = "Savings",
    @SerialName("yoco_public_key")        val yocoPublicKey: String? = null,
    val balance: Double = 0.0,
    @SerialName("goal_amount")            val goalAmount: Double = 0.0,
    @SerialName("period_months")          val periodMonths: Int = 12,
    @SerialName("admin_user_id")          val adminUserId: String? = null,
    @SerialName("fee_status")             val feeStatus: AdminFeeState = AdminFeeState.DUE,
    @SerialName("registration_paid")      val registrationPaid: Boolean = false,

    // Burial Society specific
    @SerialName("max_beneficiaries")      val maxBeneficiaries: Int? = null,
    @SerialName("beneficiary_increase_pct") val beneficiaryIncreasePct: Double? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")             val createdAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geohash: String? = null,
    
    // Platform control
    @SerialName("is_platform_suspended")  val isPlatformSuspended: Boolean = false,

    // Document management
    @SerialName("constitution_url")       val constitutionUrl: String? = null,
    @SerialName("constitution_status")    val constitutionStatus: DocumentStatus = DocumentStatus.PENDING,

    // Loan settings
    @SerialName("loan_interest_rate")     val loanInterestRate: Double? = null,
    @SerialName("loan_max_amount")        val loanMaxAmount: Double? = null,
    @SerialName("loan_max_months")        val loanMaxMonths: Int? = null
) : Parcelable {
    val platformFeeAmount: Double get() = currentMembers * PlatformFees.MONTHLY_PER_MEMBER
    val registrationFee: Double   get() = PlatformFees.REGISTRATION
}

/**
 * Central constants for the SanibonaniSave platform.
 * Moved from :data to :domain to break circular dependency.
 */
object PlatformFees {
    var MONTHLY_PER_MEMBER = 10.0  // R10/member/month (Dynamic)
    var REGISTRATION = 700.0       // One-time R700 registration fee (Dynamic)
}


@Serializable(with = GroupTypeSerializer::class)
enum class GroupType(val displayName: String) {
    BURIAL_SOCIETY("Burial Society"),
    STOKVEL("Stokvel"),
    ROSCA("ROSCA"),
    INVESTMENT_CLUB("Investment Club"),
    EMERGENCY_FUND("Emergency Fund"),
    COMMUNITY_SAVINGS("Community Savings"),
    TONTINE("Tontine"),
    OTHER("Other");

    companion object {
        fun all() = entries.toList()
    }
}

/**
 * Backward/forward compatible serializer for GroupType.
 *
 * Why: legacy/mock data can store values like "Burial Society" (display name) instead of
 * the canonical snake_case value ("burial_society"). With `coerceInputValues = true`,
 * Kotlinx can silently coerce unknown enum values to the default (OTHER), which hides
 * burial-society features (beneficiaries, beneficiary forms, etc.).
 */
object GroupTypeSerializer : KSerializer<GroupType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("GroupType", PrimitiveKind.STRING)

    private val toWire: Map<GroupType, String> = mapOf(
        GroupType.BURIAL_SOCIETY to "burial_society",
        GroupType.STOKVEL to "stokvel",
        GroupType.ROSCA to "rosca",
        GroupType.INVESTMENT_CLUB to "investment_club",
        GroupType.EMERGENCY_FUND to "emergency_fund",
        GroupType.COMMUNITY_SAVINGS to "community_savings",
        GroupType.TONTINE to "tontine",
        GroupType.OTHER to "other"
    )

    private fun normalize(raw: String): String {
        return raw.trim()
            .lowercase()
            .replace(Regex("[\\s-]+"), "_")
    }

    override fun serialize(encoder: Encoder, value: GroupType) {
        encoder.encodeString(toWire[value] ?: "other")
    }

    override fun deserialize(decoder: Decoder): GroupType {
        val raw = decoder.decodeString()
        val normalized = normalize(raw)
        val collapsed = normalized.replace("_", "")

        // First handle common canonical values.
        val direct = when (normalized) {
            "burial_society" -> GroupType.BURIAL_SOCIETY
            "stokvel" -> GroupType.STOKVEL
            "rosca" -> GroupType.ROSCA
            "investment_club" -> GroupType.INVESTMENT_CLUB
            "emergency_fund" -> GroupType.EMERGENCY_FUND
            "community_savings" -> GroupType.COMMUNITY_SAVINGS
            "tontine" -> GroupType.TONTINE
            "other" -> GroupType.OTHER
            else -> null
        }
        if (direct != null) return direct

        // Then try matching on collapsed values (e.g., "burialsociety") or display names.
        GroupType.entries.firstOrNull { t ->
            val wire = toWire[t].orEmpty()
            val candidates = listOf(
                normalize(t.name),
                normalize(t.displayName),
                normalize(wire),
                normalize(wire).replace("_", "")
            )
            normalized in candidates || collapsed in candidates
        }?.let { return it }

        return GroupType.OTHER
    }
}

@Serializable
enum class AdminFeeState {
    @SerialName("paid")
    PAID,
    @SerialName("due")
    DUE,
    @SerialName("overdue")
    OVERDUE,
    @SerialName("warning")
    WARNING,
    @SerialName("suspended")
    SUSPENDED,
    @SerialName("pending_activation")
    PENDING_ACTIVATION
}

// ─────────────────────────────────────────────────────────────────────────────
//  MEMBER
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
@Parcelize
data class Member(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id")           val groupId: String = "",
    @SerialName("user_id")            val userId: String? = null,
    @SerialName("full_name")          val fullName: String = "",
    @SerialName("id_number")          val idNumber: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val phone: String = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val email: String? = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val street: String? = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val suburb: String? = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val city: String? = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val province: String? = "",
    @SerialName("notification_pref")  val notificationPref: NotificationPref = NotificationPref.BOTH,
    val status: MemberStatus = MemberStatus.PROBATION,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("joined_at")          val joinedAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("probation_end_at")   val probationEndAt: String? = null,
    @SerialName("profile_photo_url")  val profilePhotoUrl: String? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_1_url")     val document1Url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_1_type")    val document1Type: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_1_status")  val document1Status: DocumentStatus = DocumentStatus.PENDING,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_2_url")     val document2Url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_2_type")    val document2Type: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_2_status")  val document2Status: DocumentStatus = DocumentStatus.PENDING,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_3_url")     val document3Url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_3_type")    val document3Type: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_3_status")  val document3Status: DocumentStatus = DocumentStatus.PENDING,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_4_url")     val document4Url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_4_type")    val document4Type: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_4_status")  val document4Status: DocumentStatus = DocumentStatus.PENDING,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_5_url")     val document5Url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_5_type")    val document5Type: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("document_5_status")  val document5Status: DocumentStatus = DocumentStatus.PENDING,

    // Burial Society specific
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("beneficiary_count")         val beneficiaryCount: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("beneficiary_over_65_count") val beneficiaryOver65Count: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("monthly_contribution_override") val monthlyContributionOverride: Double? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("total_contributions")       val totalContributions: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("total_paid")                val totalPaid: Double? = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("fcm_token")                val fcmToken: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("member_key")               val memberKey: String? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")         val createdAt: String? = null,

    // Cross-linked data (Loaded optionally or via join)
    @Transient val beneficiaries: List<Beneficiary> = emptyList(),
    @Transient val documents: List<MemberDocument> = emptyList()
) : Parcelable

// ─────────────────────────────────────────────────────────────────────────────
//  MEMBER DOCUMENT
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
@Parcelize
data class MemberDocument(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("member_id")      val memberId: String = "",
    @SerialName("group_id")       val groupId: String = "",
    val label: String = "", // e.g., 'ID/Passport'
    @SerialName("document_url")   val documentUrl: String = "",
    @SerialName("document_type")  val documentType: String? = null,
    val status: DocumentStatus = DocumentStatus.PENDING,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")     val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("updated_at")     val updatedAt: String? = null
) : Parcelable

// ─────────────────────────────────────────────────────────────────────────────
//  BENEFICIARY
// ─────────────────────────────────────────────────────────────────────────────
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
    @SerialName("document_status") val documentStatus: DocumentStatus = DocumentStatus.PENDING,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")     val createdAt: String? = null
) : Parcelable

@Serializable
enum class MemberStatus(val displayName: String) {
    @SerialName("active")           ACTIVE("Active"),
    @SerialName("probation")        PROBATION("Probation"),
    @SerialName("suspended")        SUSPENDED("Suspended"),
    @SerialName("pending_payment")  PENDING_PAYMENT("Pending Payment")
}

@Serializable
enum class DocumentStatus {
    @SerialName("pending")  PENDING,
    @SerialName("verified") VERIFIED,
    @SerialName("rejected") REJECTED
}

@Serializable
enum class NotificationPref(val displayName: String) {
    @SerialName("whatsapp") WHATSAPP("WhatsApp"),
    @SerialName("email")    EMAIL("Email"),
    @SerialName("both")     BOTH("Both")
}

// ─────────────────────────────────────────────────────────────────────────────
//  CONTRIBUTION
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
@Parcelize
data class Contribution(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("member_id")            val memberId: String = "",
    @SerialName("group_id")             val groupId: String = "",
    @SerialName("policy_id")            val policyId: String? = null,
    val amount: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")           val createdAt: String? = null,
    @SerialName("due_date")             val dueDate: String = "",
    @SerialName("paid_at")              val paidAt: String? = null,
    @SerialName("status")               val status: ContributionStatus = ContributionStatus.DUE,
    @SerialName("type")                 val type: String = "contribution",
    @SerialName("payment_method")       val paymentMethod: String = "yoco",
    @SerialName("yoco_transaction_id")  val yocoTransactionId: String? = null,
    @SerialName("late_fees_applied")    val lateFeesApplied: Boolean = false
) : Parcelable

@Serializable
enum class ContributionStatus(val displayName: String) {
    @SerialName("paid")    PAID("Paid"),
    @SerialName("due")     DUE("Due"),
    @SerialName("overdue") OVERDUE("Overdue"),
    @SerialName("partial") PARTIAL("Partial")
}

// ─────────────────────────────────────────────────────────────────────────────
//  PLATFORM FEE
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
@Parcelize
data class PlatformFee(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id")             val groupId: String = "",
    @SerialName("fee_type")             val feeType: String = "monthly", // 'registration' or 'monthly'
    val amount: Double = 0.0,
    @SerialName("due_date")             val dueDate: String? = null,
    @SerialName("status")               val status: AdminFeeState = AdminFeeState.DUE,
    @SerialName("paid_at")              val paidAt: String? = null,
    @SerialName("transaction_id")       val transactionId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")           val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("updated_at")           val updatedAt: String? = null
) : Parcelable

// ─────────────────────────────────────────────────────────────────────────────
//  NOTIFICATION
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
@Parcelize
data class AppNotification(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id")       val groupId: String = "",
    @SerialName("member_id")      val memberId: String? = null,
    val message: String = "",
    @SerialName("channel")        val channel: NotifChannel = NotifChannel.BOTH,
    @SerialName("trigger_event")  val triggerEvent: NotifEvent = NotifEvent.CUSTOM,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")     val createdAt: String? = null
) : Parcelable

@Serializable
enum class NotifChannel {
    @SerialName("whatsapp") WHATSAPP,
    @SerialName("email")    EMAIL,
    @SerialName("both")     BOTH
}

@Serializable
enum class NotifEvent {
    @SerialName("payment_due")           PAYMENT_DUE,
    @SerialName("payment_confirmed")     PAYMENT_CONFIRMED,
    @SerialName("payment_overdue")       PAYMENT_OVERDUE,
    @SerialName("deposit_received")      DEPOSIT_RECEIVED,
    @SerialName("document_verified")     DOCUMENT_VERIFIED,
    @SerialName("probation_ended")       PROBATION_ENDED,
    @SerialName("new_member")            NEW_MEMBER,
    @SerialName("fee_settings_changed")  FEE_SETTINGS_CHANGED,
    @SerialName("platform_fee_due")      PLATFORM_FEE_DUE,
    @SerialName("platform_fee_warning")  PLATFORM_FEE_WARNING,
    @SerialName("group_suspended")       GROUP_SUSPENDED,
    @SerialName("group_restored")        GROUP_RESTORED,
    @SerialName("actuarial_alert")       ACTUARIAL_ALERT,
    @SerialName("investment_payout")     INVESTMENT_PAYOUT,
    @SerialName("member_message")        MEMBER_MESSAGE,
    @SerialName("custom")                CUSTOM
}

// ─────────────────────────────────────────────────────────────────────────────
//  ACTUARIAL MODELS
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
@Parcelize
data class ActuarialMetrics(
    @SerialName("pure_premium")                 val purePremium: Double = 0.0,
    @SerialName("gross_premium")                val grossPremium: Double = 0.0,
    @SerialName("reserve_adequacy_pct")         val reserveAdequacyPct: Double = 0.0,
    @SerialName("solvency_margin_pct")          val solvencyMarginPct: Double = 0.0,
    @SerialName("loss_ratio_pct")               val lossRatioPct: Double = 0.0,
    @SerialName("contribution_sufficiency_pct") val contributionSufficiencyPct: Double = 0.0,
    @SerialName("break_even_members")           val breakEvenMembers: Int = 0,
    @SerialName("actuarial_present_value")      val actuarialPresentValue: Double = 0.0,
    @SerialName("funding_ratio_pct")            val fundingRatioPct: Double = 0.0,
    @SerialName("payment_rate_pct")             val paymentRatePct: Double = 0.0,
    @SerialName("composite_risk_score")         val compositeRiskScore: Int = 0,
    @SerialName("insolvency_months")            val insolvencyMonths: Int = 0,
    @SerialName("expected_annual_claims")       val expectedAnnualClaims: Double = 0.0
) : Parcelable

@Serializable
@Parcelize
data class ViabilityPlan(
    val initialContribution: Double,
    val suggestedMonthlyContribution: Double,
    val projectedValue: Double,
    val isViable: Boolean,
    val goalAmount: Double = 0.0,
    val periodMonths: Int = 12,
    val messages: List<String> = emptyList()
) : Parcelable

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
    val amount: Double = 0.0,
    @SerialName("payment_type")   val paymentType: PaymentType = PaymentType.CONTRIBUTION,
    @SerialName("payment_method") val paymentMethod: PaymentMethod = PaymentMethod.YOCO,
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
    @SerialName("custom")         CUSTOM("Custom Payment"),
    @SerialName("registration")   REGISTRATION("Registration Fee")
}

@Serializable
enum class PaymentMethod(val displayName: String) {
    @SerialName("yoco")       YOCO("YoCo Card"),
    @SerialName("bank")       BANK("Bank Transfer"),
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

// ─────────────────────────────────────────────────────────────────────────────
//  GROUP SETTINGS
// ─────────────────────────────────────────────────────────────────────────────
data class GroupSettings(
    val joiningFee: String = "150",
    val monthlyContribution: String = "250",
    val lateFee: String = "50",
    val lateFeeGraceDays: String = "5",
    val probationMonths: String = "3",
    val paymentDueDay: String = "28",
    val maxMembers: String = "50",
    val allowPartialPayment: Boolean = false,
    val joiningFeeWaiver: Boolean = false,
    val autoSuspendAfter: String = "2",
    val bankName: String = "FNB",
    val accountNumber: String = "",
    val branchCode: String = "",
    val accountType: String = "Savings",
    val maxBeneficiaries: String = "0",
    val beneficiaryIncreasePct: String = "0",
    val goalAmount: String = "10000",
    val periodMonths: String = "12",
    val isSaving: Boolean = false,
    val savedSuccess: Boolean = false
)

@Serializable
@Parcelize
data class UserProfile(
    val id: String,
    @SerialName("full_name") val fullName: String?,
    val email: String?,
    val role: String
) : Parcelable

// ─────────────────────────────────────────────────────────────────────────────
//  LOAN
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
@Parcelize
data class Loan(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("member_id")      val memberId: String = "",
    @SerialName("group_id")       val groupId: String = "",
    val amount: Double = 0.0,
    @SerialName("interest_rate")  val interestRate: Double = 0.0,
    @SerialName("total_to_repay") val totalToRepay: Double = 0.0,
    @SerialName("total_repaid")   val totalRepaid: Double = 0.0,
    @SerialName("monthly_repayment") val monthlyRepayment: Double = 0.0,
    @SerialName("start_date")     val startDate: String = "",
    @SerialName("end_date")       val endDate: String = "",
    @SerialName("next_payment_date") val nextPaymentDate: String? = null,
    val status: LoanStatus = LoanStatus.PENDING,
    @SerialName("purpose")        val purpose: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")     val createdAt: String? = null
) : Parcelable {
    val balanceRemaining: Double get() = (totalToRepay - totalRepaid).coerceAtLeast(0.0)
    val progress: Float get() = if (totalToRepay > 0) (totalRepaid / totalToRepay).toFloat() else 0f
}

@Serializable
enum class LoanStatus(val displayName: String) {
    @SerialName("pending")   PENDING("Pending Approval"),
    @SerialName("approved")  APPROVED("Approved"),
    @SerialName("active")    ACTIVE("Active"),
    @SerialName("partially_paid") PARTIALLY_PAID("Partially Paid"),
    @SerialName("completed") COMPLETED("Completed"),
    @SerialName("rejected")  REJECTED("Rejected"),
    @SerialName("overdue")   OVERDUE("Overdue")
}

@Serializable
@Parcelize
data class LoanRepayment(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("loan_id")        val loanId: String = "",
    @SerialName("member_id")      val memberId: String = "",
    @SerialName("group_id")       val groupId: String = "",
    val amount: Double = 0.0,
    @SerialName("paid_at")        val paidAt: String? = null,
    @SerialName("payment_method") val paymentMethod: PaymentMethod = PaymentMethod.YOCO,
    @SerialName("transaction_id") val transactionId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")     val createdAt: String? = null
) : Parcelable

val SA_PROVINCES = listOf("Gauteng", "Western Cape", "KwaZulu-Natal", "Eastern Cape", "Limpopo", "Mpumalanga", "North West", "Free State", "Northern Cape")
val SA_BANKS = listOf("ABSA", "African Bank", "Capitec", "FNB", "Nedbank", "Postbank", "Standard Bank", "TymeBank")
