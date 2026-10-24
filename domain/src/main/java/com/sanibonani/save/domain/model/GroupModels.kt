@file:OptIn(ExperimentalSerializationApi::class)

package com.sanibonani.save.domain.model

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
    @SerialName("burial_claim_amount")    val burialClaimAmount: Money = Money("10000.00"),
    @SerialName("min_members_for_claims") val minMembersForClaims: Int = 0,
    @SerialName("min_reserve_percentage") val minReservePercentage: Double = 0.0,
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

    @SerialName("financial_provider_id") val financialProviderId: String? = null,

    // Transient data (not stored in 'groups' table)
    @Transient val members: List<Member>? = null
) {
    /**
     * Monthly platform fee for the group's current size.
     *
     * Reads the singleton in `domain.config` — the same object `PlatformConfigRepository`
     * writes to. An earlier second copy of `PlatformFees` lived in this package, so the
     * unqualified reference silently resolved to it and **never observed fee-config
     * updates** (an admin changing the monthly rate appeared to have no effect here).
     */
    val platformFeeAmount: Money get() = com.sanibonani.save.domain.config.PlatformFees.MONTHLY_MEMBER_FEE * currentMembers.toLong()
    val registrationFee: Money get() = com.sanibonani.save.domain.config.PlatformFees.REGISTRATION
    val isRegistrationFeeOutstanding: Boolean get() = !registrationPaid || feeStatus == AdminFeeState.DUE || feeStatus == AdminFeeState.PENDING_ACTIVATION
    val isActiveGroup: Boolean get() = !isRegistrationFeeOutstanding && !isPlatformSuspended && feeStatus == AdminFeeState.PAID
}

@Serializable(with = GroupTypeSerializer::class)
enum class GroupType(
    val displayName: String,
    val icon: String,
    val description: String,
    val payoutLabel: String
) {
    BURIAL_SOCIETY(
        displayName = "Burial Society",
        icon = "🕊️",
        description = "Dedicated funds for funeral expenses and family support.",
        payoutLabel = "Burial Claims"
    ),
    STOKVEL(
        displayName = "Stokvel",
        icon = "🤝",
        description = "Traditional savings and social event support.",
        payoutLabel = "Savings Payouts"
    ),
    ROSCA(
        displayName = "ROSCA",
        icon = "🔄",
        description = "Rotating Savings and Credit Association with assigned pot cycles.",
        payoutLabel = "Rotation Payouts"
    ),
    INVESTMENT_CLUB(
        displayName = "Investment Club",
        icon = "📈",
        description = "Pooling capital for long-term equity growth and asset acquisition.",
        payoutLabel = "Disinvestments"
    ),
    EMERGENCY_FUND(
        displayName = "Emergency Fund",
        icon = "🚨",
        description = "Savings reserved for unexpected community and family emergencies.",
        payoutLabel = "Emergency Withdrawals"
    ),
    COMMUNITY_SAVINGS(
        displayName = "Community Savings",
        icon = "🏛️",
        description = "General community-driven cooperative savings group.",
        payoutLabel = "Disbursements"
    ),
    HYBRID_SAVINGS_GROUP(
        displayName = "Hybrid Savings Group",
        icon = "🪴",
        description = "Integrated multi-pot cooperative model combining savings, lending, and social funds.",
        payoutLabel = "Multi-Pot Disbursements"
    ),
    OTHER(
        displayName = "Other",
        icon = "💰",
        description = "Other specialized type of savings or cooperative group.",
        payoutLabel = "Payouts"
    );

    val isBurialSociety: Boolean get() = this == BURIAL_SOCIETY
    val isStokvel: Boolean get() = this == STOKVEL
    val isRosca: Boolean get() = this == ROSCA
    val isInvestmentClub: Boolean get() = this == INVESTMENT_CLUB
    val isEmergencyFund: Boolean get() = this == EMERGENCY_FUND
    val isCommunitySavings: Boolean get() = this == COMMUNITY_SAVINGS
    val isHybridSavings: Boolean get() = this == HYBRID_SAVINGS_GROUP

    val supportsLoans: Boolean get() = this != INVESTMENT_CLUB
    val supportsBeneficiaries: Boolean get() = this == BURIAL_SOCIETY || this == STOKVEL
    val supportsRotation: Boolean get() = this == ROSCA

    companion object {
        fun all() = entries.toList()
    }
}

@Serializable
data class HybridPotAllocation(
    val personalSavingsPct: Double = 60.0,
    val lendingPoolPct: Double = 20.0,
    val socialFundPct: Double = 10.0,
    val investmentPct: Double = 10.0
) {
    val isValidAllocation: Boolean
        get() = Math.abs((personalSavingsPct + lendingPoolPct + socialFundPct + investmentPct) - 100.0) < 0.01

    fun calculateSplit(total: Money): HybridPotSplit {
        val personal = total * (personalSavingsPct / 100.0)
        val lending = total * (lendingPoolPct / 100.0)
        val social = total * (socialFundPct / 100.0)
        val investment = (total - personal - lending - social).let { if (it.isNegative) Money.ZERO else it }
        return HybridPotSplit(
            personalSavings = personal,
            lendingPool = lending,
            socialFund = social,
            investment = investment
        )
    }
}

@Serializable
data class HybridPotSplit(
    val personalSavings: Money,
    val lendingPool: Money,
    val socialFund: Money,
    val investment: Money
) {
    fun toFormulaBreakdowns(allocation: HybridPotAllocation = HybridPotAllocation()): List<ActuarialFormulaBreakdown> {
        return listOf(
            ActuarialFormulaBreakdown(
                title = "1. Personal Savings Pot (${allocation.personalSavingsPct.toInt()}%)",
                formula = "PersonalSavings = Total × ${allocation.personalSavingsPct / 100.0}",
                resultText = personalSavings.format(),
                explanation = "Direct member accumulated capital reserve.",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "2. Lending Pool Pot (${allocation.lendingPoolPct.toInt()}%)",
                formula = "LendingPool = Total × ${allocation.lendingPoolPct / 100.0}",
                resultText = lendingPool.format(),
                explanation = "Active loan capital reserve for member borrowing.",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "3. Social Emergency Fund (${allocation.socialFundPct.toInt()}%)",
                formula = "SocialFund = Total × ${allocation.socialFundPct / 100.0}",
                resultText = socialFund.format(),
                explanation = "Hardship and emergency member relief fund.",
                status = "OK"
            ),
            ActuarialFormulaBreakdown(
                title = "4. Group Investment Pot (${allocation.investmentPct.toInt()}%)",
                formula = "InvestmentPot = Total × ${allocation.investmentPct / 100.0}",
                resultText = investment.format(),
                explanation = "Long-term market growth and dividend pool.",
                status = "OK"
            )
        )
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
        GroupType.HYBRID_SAVINGS_GROUP to "hybrid_savings_group",
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
            "hybrid_savings_group", "hybrid" -> GroupType.HYBRID_SAVINGS_GROUP
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
    val burialClaimAmount: String = "10000",
    val minMembersForClaims: String = "10",
    val minReservePercentage: String = "20",
    val goalAmount: String = "10000",
    val periodMonths: String = "12",
    val loanInterestRate: String = "0",
    val loanMaxAmount: String = "0",
    val loanMaxMonths: String = "0",
    val financialProviderId: String? = null,
    val rotationMethod: RoscaRotationMethod = RoscaRotationMethod.FIXED,
    val isSaving: Boolean = false,
    val savedSuccess: Boolean = false
)
