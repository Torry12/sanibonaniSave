@file:OptIn(ExperimentalSerializationApi::class)

package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*
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
    @SerialName("joining_fee")            val joiningFee: Money = Money.ZERO,
    @SerialName("monthly_contribution")   val monthlyContribution: Money = Money.ZERO,
    @SerialName("late_fee")               val lateFee: Money = Money.ZERO,
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
    val balance: Money = Money.ZERO,
    @SerialName("goal_amount")            val goalAmount: Money = Money.ZERO,
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

    // Investment Club specific
    @SerialName("investment_payout_date") val investmentPayoutDate: String? = null,

    // Document management
    @SerialName("constitution_url")       val constitutionUrl: String? = null,
    @SerialName("constitution_status")    val constitutionStatus: DocumentStatus = DocumentStatus.PENDING,

    // Loan settings
    @SerialName("loan_interest_rate")     val loanInterestRate: Double? = null,
    @SerialName("loan_max_amount")        val loanMaxAmount: Money? = null,
    @SerialName("loan_max_months")        val loanMaxMonths: Int? = null,

    // Transient data (not stored in 'groups' table)
    @Transient val members: List<Member>? = null
) : Parcelable {
    val platformFeeAmount: Money get() = PlatformFees.MONTHLY_MEMBER_FEE * currentMembers.toDouble()
    val registrationFee: Money   get() = PlatformFees.REGISTRATION
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
    val rotationMethod: RoscaRotationMethod = RoscaRotationMethod.FIXED,
    val isSaving: Boolean = false,
    val savedSuccess: Boolean = false
)
