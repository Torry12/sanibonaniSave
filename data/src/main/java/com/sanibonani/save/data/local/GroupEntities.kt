package com.sanibonani.save.data.local

import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "groups",
    indices = [
        Index("is_public"),
        Index("admin_user_id"),
        Index("fee_status"),
    ],
)
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: GroupType,
    val province: String,
    val city: String,
    val township: String,
    val description: String,
    @ColumnInfo(name = "logo_emoji") val logoEmoji: String?,
    @ColumnInfo(name = "late_fee_grace_days") val lateFeeGraceDays: Int,
    @ColumnInfo(name = "probation_months") val probationMonths: Int,
    @ColumnInfo(name = "payment_due_day") val paymentDueDay: Int,
    @ColumnInfo(name = "max_members") val maxMembers: Int,
    @ColumnInfo(name = "current_members") val currentMembers: Int,
    @ColumnInfo(name = "is_public") val isPublic: Boolean,
    @ColumnInfo(name = "allow_partial_payment") val allowPartialPayment: Boolean,
    @ColumnInfo(name = "auto_suspend_after") val autoSuspendAfter: Int,
    @ColumnInfo(name = "bank_name") val bankName: String?,
    @ColumnInfo(name = "account_number") val accountNumber: String?,
    @ColumnInfo(name = "branch_code") val branchCode: String?,
    @ColumnInfo(name = "account_type") val accountType: String,
    @ColumnInfo(name = "admin_user_id") val adminUserId: String?,
    @ColumnInfo(name = "fee_status") val feeStatus: AdminFeeState,
    @ColumnInfo(name = "registration_paid") val registrationPaid: Boolean,
    @ColumnInfo(name = "is_platform_suspended") val isPlatformSuspended: Boolean,
    @ColumnInfo(name = "period_months") val periodMonths: Int,

    @ColumnInfo(name = "constitution_url") val constitutionUrl: String?,
    @ColumnInfo(name = "constitution_status") val constitutionStatus: DocumentStatus,

    // Burial Society specific
    @ColumnInfo(name = "max_beneficiaries") val maxBeneficiaries: Int,
    @ColumnInfo(name = "beneficiary_increase_pct") val beneficiaryIncreasePct: Double,
    @ColumnInfo(name = "burial_claim_amount") val burialClaimAmount: Money = Money("10000.00"),
    @ColumnInfo(name = "min_members_for_claims") val minMembersForClaims: Int = 0,
    @ColumnInfo(name = "min_reserve_percentage") val minReservePercentage: Double = 0.0,

    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "investment_payout_date") val investmentPayoutDate: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geohash: String? = null,
    @ColumnInfo(name = "rosca_rotation_method") val rotationMethod: RoscaRotationMethod = RoscaRotationMethod.FIXED,

    // Loan settings
    @ColumnInfo(name = "loan_interest_rate") val loanInterestRate: Double? = null,
    @ColumnInfo(name = "loan_max_months") val loanMaxMonths: Int? = null,

    @ColumnInfo(name = "financial_provider_id") val financialProviderId: String? = null,

    @ColumnInfo(name = "joining_fee") val joiningFee: Money,
    @ColumnInfo(name = "monthly_contribution") val monthlyContribution: Money,
    @ColumnInfo(name = "late_fee") val lateFee: Money,
    @ColumnInfo(name = "balance") val balance: Money,
    @ColumnInfo(name = "goal_amount") val goalAmount: Money,
    @ColumnInfo(name = "loan_max_amount") val loanMaxAmount: Money?,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
)

/**
 * Data class representing a Group with its associated members.
 * Uses Room @Relation to fetch the relationship in a single transaction.
 */
data class GroupWithMembersEntity(
    @Embedded val group: GroupEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "group_id"
    )
    val members: List<MemberEntity>
)

@Dao
interface GroupDao {
    @RoomTransaction
    @Query("SELECT * FROM `groups` ORDER BY name ASC")
    fun observeAllGroupsWithMembers(): Flow<List<GroupWithMembersEntity>>

    @RoomTransaction
    @Query("SELECT * FROM `groups` WHERE id = :id")
    fun observeGroupWithMembers(id: String): Flow<GroupWithMembersEntity?>

    @Query("SELECT * FROM `groups` ORDER BY name ASC")
    fun observeAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` WHERE id = :id")
    fun observeGroupById(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM `groups` WHERE is_public = 1")
    fun observePublicGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` WHERE admin_user_id = :adminId")
    fun observeGroupsByAdmin(adminId: String): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` WHERE admin_user_id = :adminId")
    suspend fun getGroupsByAdminSync(adminId: String): List<GroupEntity>

    @Query("SELECT * FROM `groups` WHERE id = :id")
    suspend fun getGroupById(id: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroups(groups: List<GroupEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: GroupEntity): Long

    @Query("DELETE FROM `groups` WHERE id = :id")
    suspend fun deleteGroupById(id: String)

    @Query("DELETE FROM `groups`")
    suspend fun clearAll()
}

// ── Mappers ──────────────────────────────────────────────────────────────────

fun GroupWithMembersEntity.toModel() = group.toModel().copy(
    members = members.map { it.toModel() }
)

fun GroupEntity.toModel() = Group(
    id = id,
    name = name,
    type = type,
    province = province,
    city = city,
    township = township,
    description = description ?: "",
    logoEmoji = logoEmoji ?: "🤝",
    joiningFee = joiningFee,
    monthlyContribution = monthlyContribution,
    lateFee = lateFee,
    lateFeeGraceDays = lateFeeGraceDays,
    probationMonths = probationMonths,
    paymentDueDay = paymentDueDay,
    maxMembers = maxMembers,
    currentMembers = currentMembers,
    isPublic = isPublic,
    allowPartialPayment = allowPartialPayment,
    autoSuspendAfter = autoSuspendAfter,
    bankName = bankName,
    accountNumber = accountNumber,
    branchCode = branchCode,
    accountType = accountType,
    balance = balance,
    adminUserId = adminUserId,
    feeStatus = feeStatus,
    registrationPaid = registrationPaid,
    isPlatformSuspended = isPlatformSuspended,
    goalAmount = goalAmount,
    periodMonths = periodMonths,
    constitutionUrl = constitutionUrl,
    constitutionStatus = constitutionStatus,
    maxBeneficiaries = maxBeneficiaries,
    beneficiaryIncreasePct = beneficiaryIncreasePct,
    burialClaimAmount = burialClaimAmount,
    minMembersForClaims = minMembersForClaims,
    minReservePercentage = minReservePercentage,
    createdAt = createdAt,
    investmentPayoutDate = investmentPayoutDate,
    latitude = latitude,
    longitude = longitude,
    geohash = geohash,
    rotationMethod = rotationMethod,
    loanInterestRate = loanInterestRate,
    loanMaxAmount = loanMaxAmount,
    loanMaxMonths = loanMaxMonths,
    financialProviderId = financialProviderId
)

fun Group.toEntity() = GroupEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    name = name,
    type = type,
    province = province ?: "",
    city = city ?: "",
    township = township ?: "",
    description = description ?: "",
    logoEmoji = logoEmoji,
    joiningFee = joiningFee,
    monthlyContribution = monthlyContribution,
    lateFee = lateFee,
    lateFeeGraceDays = lateFeeGraceDays,
    probationMonths = probationMonths,
    paymentDueDay = paymentDueDay,
    maxMembers = maxMembers,
    currentMembers = currentMembers,
    isPublic = isPublic,
    allowPartialPayment = allowPartialPayment,
    autoSuspendAfter = autoSuspendAfter,
    bankName = bankName,
    accountNumber = accountNumber,
    branchCode = branchCode,
    accountType = accountType,
    balance = balance,
    adminUserId = adminUserId,
    feeStatus = feeStatus,
    registrationPaid = registrationPaid,
    isPlatformSuspended = isPlatformSuspended,
    goalAmount = goalAmount,
    periodMonths = periodMonths,
    constitutionUrl = constitutionUrl,
    constitutionStatus = constitutionStatus,
    maxBeneficiaries = maxBeneficiaries ?: 0,
    beneficiaryIncreasePct = beneficiaryIncreasePct ?: 0.0,
    burialClaimAmount = burialClaimAmount,
    minMembersForClaims = minMembersForClaims,
    minReservePercentage = minReservePercentage,
    createdAt = createdAt,
    investmentPayoutDate = investmentPayoutDate,
    latitude = latitude,
    longitude = longitude,
    geohash = geohash,
    rotationMethod = rotationMethod,
    loanInterestRate = loanInterestRate,
    loanMaxAmount = loanMaxAmount,
    loanMaxMonths = loanMaxMonths,
    financialProviderId = financialProviderId
)
