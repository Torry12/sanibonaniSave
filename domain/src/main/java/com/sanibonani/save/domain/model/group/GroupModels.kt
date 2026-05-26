package com.sanibonani.save.domain.model.group

import android.os.Parcelable
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.PlatformFees
import com.sanibonani.save.domain.model.member.Member
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
enum class RoscaRotationMethod {
    @SerialName("fixed")        FIXED,
    @SerialName("random_draw")  RANDOM_DRAW,
    @SerialName("need_based")   NEED_BASED,
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

@OptIn(ExperimentalSerializationApi::class)
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
    @SerialName("rosca_rotation_method")  val rotationMethod: RoscaRotationMethod = RoscaRotationMethod.FIXED,
    @SerialName("max_beneficiaries")      val maxBeneficiaries: Int? = null,
    @SerialName("beneficiary_increase_pct") val beneficiaryIncreasePct: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("created_at") val createdAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geohash: String? = null,
    @SerialName("is_platform_suspended")  val isPlatformSuspended: Boolean = false,
    @SerialName("constitution_url")       val constitutionUrl: String? = null,
    @SerialName("constitution_status")    val constitutionStatus: DocumentStatus = DocumentStatus.PENDING,
    @SerialName("loan_interest_rate")     val loanInterestRate: Double? = null,
    @SerialName("loan_max_amount")        val loanMaxAmount: Double? = null,
    @SerialName("loan_max_months")        val loanMaxMonths: Int? = null,
    @Transient val members: List<Member>? = null
) : Parcelable {
    val platformFeeAmount: Double get() = currentMembers * PlatformFees.MONTHLY_PER_MEMBER
    val registrationFee: Double   get() = PlatformFees.REGISTRATION
}

object GroupTypeSerializer : KSerializer<GroupType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("GroupType", PrimitiveKind.STRING)
    private val toWire = mapOf(
        GroupType.BURIAL_SOCIETY to "burial_society", GroupType.STOKVEL to "stokvel",
        GroupType.ROSCA to "rosca", GroupType.INVESTMENT_CLUB to "investment_club",
        GroupType.EMERGENCY_FUND to "emergency_fund", GroupType.COMMUNITY_SAVINGS to "community_savings",
        GroupType.TONTINE to "tontine", GroupType.OTHER to "other"
    )
    private fun normalize(raw: String) = raw.trim().lowercase().replace(Regex("[\\s-]+"), "_")
    override fun serialize(encoder: Encoder, value: GroupType) = encoder.encodeString(toWire[value] ?: "other")
    override fun deserialize(decoder: Decoder): GroupType {
        val raw = decoder.decodeString()
        val normalized = normalize(raw)
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
        val collapsed = normalized.replace("_", "")
        return GroupType.entries.firstOrNull { t ->
            val wire = toWire[t].orEmpty()
            val candidates = listOf(normalize(t.name), normalize(t.displayName), normalize(wire), normalize(wire).replace("_", ""))
            normalized in candidates || collapsed in candidates
        } ?: GroupType.OTHER
    }
}
