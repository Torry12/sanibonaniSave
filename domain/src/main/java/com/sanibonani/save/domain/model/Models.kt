@file:OptIn(ExperimentalSerializationApi::class)

package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


// ─────────────────────────────────────────────────────────────────────────────
//  ROSCA ROTATION METHOD
// ─────────────────────────────────────────────────────────────────────────────
/**
 * How payout rotation order is determined in a ROSCA group.
 * Stored as a snake_case string in both Supabase and Room.
 */
@Serializable
enum class RoscaRotationMethod {
    /** Order is fixed by join date (default). */
    @SerialName("fixed")        FIXED,
    /** Order is randomised once per cycle using a deterministic seed. */
    @SerialName("random_draw")  RANDOM_DRAW,
    /** Admin assigns position based on member need/hardship. */
    @SerialName("need_based")   NEED_BASED,
    /** Members bid for early payout positions each cycle. */
    @SerialName("auction")      AUCTION;

    val displayName: String get() = when (this) {
        FIXED       -> "Fixed (join date)"
        RANDOM_DRAW -> "Random draw"
        NEED_BASED  -> "Need-based"
        AUCTION     -> "Auction"
    }
    val description: String get() = when (this) {
        FIXED       -> "Rotation order is set once at the start, by the date each member joined."
        RANDOM_DRAW -> "Each cycle a fair draw determines who receives the pot first."
        NEED_BASED  -> "The admin assigns slots based on financial hardship or urgency."
        AUCTION     -> "Members bid for early slots; highest bid wins earlier payout."
    }
}

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
    @SerialName("gateway_public_key")     val gatewayPublicKey: String? = null,
    val balance: Double = 0.0,
    @SerialName("goal_amount")            val goalAmount: Double = 0.0,
    @SerialName("period_months")          val periodMonths: Int = 12,
    @SerialName("admin_user_id")          val adminUserId: String? = null,
    @SerialName("fee_status")             val feeStatus: AdminFeeState = AdminFeeState.DUE,
    @SerialName("registration_paid")      val registrationPaid: Boolean = false,

    // ROSCA specific
    @SerialName("rosca_rotation_method")  val rotationMethod: RoscaRotationMethod = RoscaRotationMethod.FIXED,

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
    @SerialName("loan_max_months")        val loanMaxMonths: Int? = null,

    // Transient data (not stored in 'groups' table)
    @Transient val members: List<Member>? = null
) : Parcelable {
    val platformFeeAmount: Double get() = currentMembers * PlatformFees.MONTHLY_PER_MEMBER
    val registrationFee: Double   get() = PlatformFees.REGISTRATION
}

@Serializable
enum class PollStatus {
    @SerialName("draft") DRAFT,
    @SerialName("open") OPEN,
    @SerialName("closed") CLOSED,
    @SerialName("cancelled") CANCELLED
}

@Serializable
@Parcelize
data class GroupPoll(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id") val groupId: String = "",
    @SerialName("created_by_member_id") val createdByMemberId: String? = null,
    val title: String = "",
    val description: String? = null,
    val status: PollStatus = PollStatus.OPEN,
    @SerialName("allow_multiple_choice") val allowMultipleChoice: Boolean = false,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) : Parcelable

@Serializable
@Parcelize
data class GroupPollOption(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("poll_id") val pollId: String = "",
    val label: String = "",
    val position: Int = 1,
    @SerialName("created_at") val createdAt: String? = null
) : Parcelable

@Serializable
@Parcelize
data class GroupPollVote(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("poll_id") val pollId: String = "",
    @SerialName("option_id") val optionId: String = "",
    @SerialName("member_id") val memberId: String = "",
    @SerialName("group_id") val groupId: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) : Parcelable

@Serializable
@Parcelize
data class GroupPollWithOptions(
    val poll: GroupPoll,
    val options: List<GroupPollOption> = emptyList(),
    @SerialName("my_vote_option_id") val myVoteOptionId: String? = null
) : Parcelable

/**
 * Central constants for the SanibonaniSave platform.
 * Moved from :data to :domain to break circular dependency.
 *
 * ### Design Note — Global Mutable State
 * `PlatformFees` uses a mutable singleton so that runtime values fetched from
 * Supabase `platform_settings` are visible app-wide without threading the config
 * through every constructor.
 *
 * **Known limitation**: mutable global state makes unit tests order-dependent.
 * **Migration path**: use the injectable `PlatformConfigRepository` as the primary
 * source of truth (`StateFlow<PlatformConfig>`). `PlatformFees` is retained as a
 * compatibility bridge for existing read sites during rollout.
 *
 * Until that migration is complete, use [PlatformFees.update] as the **single**
 * write site; never assign to the properties directly.
 */
object PlatformFees {
    // Member-level monthly platform fee configured by platform admin.
    var MONTHLY_PER_MEMBER = 0.0   // Backward-compatible alias (set from platform settings)
    var MONTHLY_MEMBER_FEE: Double
        get() = MONTHLY_PER_MEMBER
        set(value) { MONTHLY_PER_MEMBER = value }
    var REGISTRATION = 700.0       // One-time R700 registration fee (Dynamic)

    /**
     * Single write-site for updating platform fee configuration.
     * Prefer this over direct property assignment to make mutation explicit and
     * easily searchable.
     *
     * @param monthlyPerMember Monthly fee charged per active member (can be zero).
     * @param registrationFee  One-time group registration fee.
     */
    fun update(monthlyPerMember: Double, registrationFee: Double) {
        MONTHLY_PER_MEMBER = monthlyPerMember
        REGISTRATION = registrationFee
    }
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
    @SerialName("payment_method")       val paymentMethod: String = "bank",
    @SerialName("transaction_id")       val transactionId: String? = null,
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
    @SerialName("payout_requested")      PAYOUT_REQUESTED,
    @SerialName("payout_processed")       PAYOUT_PROCESSED,
    @SerialName("payout_failed")          PAYOUT_FAILED,
    @SerialName("payout_cancelled")       PAYOUT_CANCELLED,
    @SerialName("loan_requested")        LOAN_REQUESTED,
    @SerialName("loan_approved")         LOAN_APPROVED,
    @SerialName("loan_rejected")         LOAN_REJECTED,
    @SerialName("loan_defaulted")        LOAN_DEFAULTED,
    @SerialName("actuarial_alert")       ACTUARIAL_ALERT,
    @SerialName("investment_payout")     INVESTMENT_PAYOUT,
    @SerialName("member_message")        MEMBER_MESSAGE,
    @SerialName("custom")                CUSTOM
}

// ─────────────────────────────────────────────────────────────────────────────
//  ACTUARIAL MODELS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Risk classification aligned with FSCA / SA prudential standards.
 *
 * Only domain-meaningful state is kept here.  UI colour mappings live in the
 * presentation layer (see `RiskLevelUiExt.kt` in the app module).
 */
@Serializable
enum class RiskLevel(val displayName: String) {
    @SerialName("low")      LOW("Low Risk"),
    @SerialName("moderate") MODERATE("Moderate Risk"),
    @SerialName("high")     HIGH("High Risk"),
    @SerialName("critical") CRITICAL("Critical Risk")
}

/**
 * Single month cash-flow projection point for charting / forecasting.
 */
@Serializable
@Parcelize
data class MonthlyProjection(
    val month: Int = 0,
    val label: String = "",
    @SerialName("projected_balance") val projectedBalance: Double = 0.0,
    val inflow: Double = 0.0,
    val outflow: Double = 0.0,
    @SerialName("net_flow")   val netFlow: Double = 0.0,
    @SerialName("risk_flag")  val riskFlag: Boolean = false
) : Parcelable

/**
 * Industry benchmark data per group type.
 * Sources: NASASA (2024), FSCA, StatsSA, JSE Investment Club guidelines.
 */
@Serializable
@Parcelize
data class IndustryBenchmark(
    @SerialName("benchmark_type")              val benchmarkType: String = "",
    @SerialName("industry_avg_contribution")   val industryAvgContribution: Double = 0.0,
    @SerialName("industry_avg_balance")        val industryAvgBalance: Double = 0.0,
    @SerialName("industry_payment_rate_pct")   val industryPaymentRatePct: Double = 0.0,
    @SerialName("group_vs_benchmark_pct")      val groupVsBenchmarkPct: Double = 0.0,
    @SerialName("benchmark_notes")             val benchmarkNotes: String = ""
) : Parcelable

/**
 * Comprehensive group-type-specific financial insight.
 * Aggregates all industry-standard actuarial analytics for each group type.
 * Produced by the `ActuarialRepository` implementation.
 */
@Serializable
@Parcelize
data class GroupFinancialInsight(
    @SerialName("group_id")          val groupId: String = "",
    @SerialName("group_type")        val groupType: GroupType = GroupType.OTHER,
    @SerialName("risk_level")        val riskLevel: RiskLevel = RiskLevel.MODERATE,
    @SerialName("status_summary")    val statusSummary: String = "",
    val recommendations: List<String> = emptyList(),
    @SerialName("key_findings")      val keyFindings: List<String> = emptyList(),
    @SerialName("monthly_projections") val monthlyProjections: List<MonthlyProjection> = emptyList(),
    @SerialName("industry_benchmark") val industryBenchmark: IndustryBenchmark = IndustryBenchmark(),

    // ── Burial Society (FSCA Friendly Societies Act) ───────────────────────
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("expected_annual_claims_count") val expectedAnnualClaimsCount: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("solvency_ratio")              val solvencyRatio: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("capital_adequacy_pct")        val capitalAdequacyPct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("required_reserve_amount")     val requiredReserveAmount: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("reserve_coverage_months")     val reserveCoverageMonths: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("benefit_adequacy_pct")        val benefitAdequacyPct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("is_solvent")                  val isSolvent: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("is_capital_adequate")         val isCapitalAdequate: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("years_to_insolvency")         val yearsToInsolvency: Double = -1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("pure_premium")                val purePremiumInsight: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("gross_premium")               val grossPremiumInsight: Double = 0.0,

    // ── Investment Club (JSE / NASAA) ──────────────────────────────────────
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("nav_per_unit")                val navPerUnit: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("total_return_pct")            val totalReturn: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("annualised_return_pct")       val annualisedReturn: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_cagr_pct")          val projectedCagr: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("capital_at_risk_per_member")  val capitalAtRiskPerMember: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("sharpe_ratio")                val sharpeRatio: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_fv")                val projectedFv: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("requires_return_pct")         val requiresReturnPct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("is_on_track_for_goal")        val isOnTrackForGoal: Boolean = false,

    // ── ROSCA (Besley-Coate-Loury model) ──────────────────────────────────
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("monthly_pot")                 val monthlyPot: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("cycle_length")                val cycleLength: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("current_cycle_month")         val currentCycleMonth: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("pot_completion_pct")          val potCompletionPct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("default_risk_score")          val defaultRiskScore: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("cycle_completion_probability") val cycleCompletionProbability: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("current_payout_member")       val currentPayoutMember: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("next_payout_member")          val nextPayoutMember: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("welfare_gain_early_receiver") val welfareGainEarlyReceiver: Double = 0.0,

    // ── Stokvel (NASASA standard) ──────────────────────────────────────────
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("total_projected_fund")        val totalProjectedFund: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_payout_per_member") val projectedPayoutPerMember: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("pot_milestone_pct")           val potMilestonePct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("payment_compliance_pct")      val paymentCompliancePct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("benchmark_vs_nasasa_avg")     val benchmarkVsNasasaAvg: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("savings_efficiency_score")    val savingsEfficiencyScore: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("annual_contribution_target")  val annualContributionTarget: Double = 0.0,

    // ── Emergency Fund (SA Financial Planning standard) ────────────────────
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("coverage_months")             val coverageMonths: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("target_coverage_months")      val targetCoverageMonths: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("coverage_gap")                val coverageGap: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("is_meeting_target")           val isMeetingTarget: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("months_to_emergency_target")  val monthsToEmergencyTarget: Int = 0,

    // ── Tontine ────────────────────────────────────────────────────────────
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("current_share_per_member")    val currentSharePerMember: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_share_at_end")      val projectedShareAtEnd: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("expected_survivors")          val expectedSurvivors: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("mortality_adjusted_yield")    val mortalityAdjustedYield: Double = 0.0,

    // ── Community Savings / Other ──────────────────────────────────────────
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("savings_per_member")          val savingsPerMember: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("annual_dividend_projection")  val annualDividendProjection: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("growth_rate_pct")             val growthRatePct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("goal_progress_pct")           val goalProgressPct: Double = 0.0
) : Parcelable

@Serializable
@Parcelize
data class ActuarialMetrics(
    // ── Core insurance / savings metrics ─────────────────────────────────
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
    @SerialName("expected_annual_claims")       val expectedAnnualClaims: Double = 0.0,
    // ── Extended cross-cutting analytics (backward-compatible) ────────────
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("risk_level")               val riskLevel: RiskLevel = RiskLevel.MODERATE,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("cash_flow_risk_score")     val cashFlowRiskScore: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("collection_efficiency_pct") val collectionEfficiencyPct: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_balance_m3")     val projectedBalanceM3: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_balance_m6")     val projectedBalanceM6: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projected_balance_m12")    val projectedBalanceM12: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("type_specific_warnings")   val typeSpecificWarnings: List<String> = emptyList()
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
    val messages: List<String> = emptyList(),
    // ── Extended viability analytics ─────────────────────────────────────
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("required_monthly_to_meet_goal")  val requiredMonthlyToMeetGoal: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("shortfall_amount")                val shortfallAmount: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("break_even_months")               val breakEvenMonths: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("compounded_projected_value")      val compoundedProjectedValue: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("optimistic_projected_value")      val optimisticProjectedValue: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("pessimistic_projected_value")     val pessimisticProjectedValue: Double = 0.0,
    // ── Explicit viability factors for charting / diagnostics ─────────────
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("active_member_ratio")              val activeMemberRatio: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("inflation_adjustment_factor")      val inflationAdjustmentFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("projection_retention_factor")      val projectionRetentionFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("claim_readiness_factor")           val claimReadinessFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("mortality_buffer_factor")          val mortalityBufferFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("reserve_adequacy_factor")          val reserveAdequacyFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("market_return_premium_factor")     val marketReturnPremiumFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("volatility_haircut_factor")        val volatilityHaircutFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("collection_efficiency_factor")     val collectionEfficiencyFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("festive_payout_pressure_factor")   val festivePayoutPressureFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("default_risk_factor")              val defaultRiskFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("cycle_slippage_factor")            val cycleSlippageFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("withdrawal_pressure_factor")       val withdrawalPressureFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("inflation_safety_factor")          val inflationSafetyFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("survivor_uncertainty_factor")      val survivorUncertaintyFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("horizon_compounding_factor")       val horizonCompoundingFactor: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("goal_stretch_ratio")               val goalStretchRatio: Double = 1.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("growth_conservatism_factor")       val growthConservatismFactor: Double = 1.0
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
    val loanInterestRate: String = "0",
    val loanMaxAmount: String = "0",
    val loanMaxMonths: String = "0",
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
    @SerialName("contract_url")      val contractUrl: String? = null,
    @SerialName("surety_amount")    val suretyAmount: Double? = null,
    val status: LoanStatus = LoanStatus.PENDING,
    @SerialName("purpose")        val purpose: String? = null,
    @SerialName("reviewed_by")     val reviewedBy: String? = null,
    @SerialName("reviewed_at")     val reviewedAt: String? = null,
    @SerialName("admin_notes")     val adminNotes: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
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
    @SerialName("overdue")   OVERDUE("Overdue"),
    @SerialName("cancelled") CANCELLED("Cancelled")
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
    @SerialName("payment_method") val paymentMethod: PaymentMethod = PaymentMethod.BANK,
    @SerialName("transaction_id") val transactionId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")     val createdAt: String? = null
) : Parcelable

@Serializable
@Parcelize
data class MemberBehaviorInsight(
    @SerialName("member_id") val memberId: String = "",
    @SerialName("member_name") val memberName: String = "",
    @SerialName("group_id") val groupId: String = "",
    @SerialName("total_loan_requests") val totalLoanRequests: Int = 0,
    @SerialName("pending_requests") val pendingRequests: Int = 0,
    @SerialName("overdue_loans") val overdueLoans: Int = 0,
    @SerialName("total_requested_amount") val totalRequestedAmount: Double = 0.0,
    @SerialName("outstanding_amount") val outstandingAmount: Double = 0.0,
    @SerialName("completion_ratio") val completionRatio: Double = 0.0,
    @SerialName("risk_band") val riskBand: String = "Watch"
) : Parcelable

@Serializable
@Parcelize
data class AuditLog(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("actor_id") val actorId: String = "",
    @SerialName("target_member_id") val targetMemberId: String? = null,
    @SerialName("target_group_id") val targetGroupId: String? = null,
    val action: String = "",
    val details: Map<String, String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at") val createdAt: String? = null
) : Parcelable

// ─────────────────────────────────────────────────────────────────────────────
//  BURIAL SOCIETY CLAIM
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
enum class BeneficiaryClaimStatus(val displayName: String) {
    @SerialName("submitted")  SUBMITTED("Submitted"),
    @SerialName("under_review") UNDER_REVIEW("Under Review"),
    @SerialName("escalated")  ESCALATED("Escalated to Platform"),
    @SerialName("approved")   APPROVED("Approved"),
    @SerialName("rejected")   REJECTED("Rejected"),
    @SerialName("paid")       PAID("Paid Out"),
    @SerialName("cancelled")  CANCELLED("Cancelled")
}

@Serializable
@Parcelize
data class BeneficiaryPayoutClaim(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id")          val groupId: String = "",
    @SerialName("member_id")         val memberId: String = "",
    @SerialName("beneficiary_id")    val beneficiaryId: String = "",
    @SerialName("beneficiary_name")  val beneficiaryName: String = "",
    @SerialName("cause_of_death")    val causeOfDeath: String = "",
    @SerialName("date_of_death")     val dateOfDeath: String = "",
    @SerialName("claim_amount")      val claimAmount: Double = 0.0,
    @SerialName("bank_name")         val bankName: String = "",
    @SerialName("account_no")        val accountNo: String = "",
    @SerialName("branch_code")       val branchCode: String = "",
    @SerialName("account_holder")    val accountHolder: String = "",
    val notes: String? = null,
    val status: BeneficiaryClaimStatus = BeneficiaryClaimStatus.SUBMITTED,
    @SerialName("reviewed_by")       val reviewedBy: String? = null,
    @SerialName("reviewed_at")       val reviewedAt: String? = null,
    @SerialName("admin_notes")       val adminNotes: String? = null,
    @SerialName("rejection_reason")  val rejectionReason: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")        val createdAt: String? = null
) : Parcelable

// ─────────────────────────────────────────────────────────────────────────────
//  LEDGER
// ─────────────────────────────────────────────────────────────────────────────
@Serializable
@Parcelize
data class LedgerEntry(
    val id: String? = null,
    @SerialName("group_id")       val groupId: String,
    @SerialName("transaction_id") val transactionId: String? = null,
    val amount: Double,
    @SerialName("balance_after")  val balanceAfter: Double,
    val description: String,
    val category: String,
    @SerialName("created_at")     val createdAt: String? = null
) : Parcelable

