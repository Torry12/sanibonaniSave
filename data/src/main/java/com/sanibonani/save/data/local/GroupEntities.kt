package com.sanibonani.save.data.local

import androidx.room.*
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
    @ColumnInfo(name = "logo_emoji") val logoEmoji: String,
    @ColumnInfo(name = "joining_fee") val joiningFee: Money,
    @ColumnInfo(name = "monthly_contribution") val monthlyContribution: Money,
    @ColumnInfo(name = "late_fee") val lateFee: Money,
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
    val balance: Money,
    @ColumnInfo(name = "admin_user_id") val adminUserId: String?,
    @ColumnInfo(name = "fee_status") val feeStatus: AdminFeeState,
    @ColumnInfo(name = "registration_paid", defaultValue = "0") val registrationPaid: Boolean,
    @ColumnInfo(name = "is_platform_suspended", defaultValue = "0") val isPlatformSuspended: Boolean,
    @ColumnInfo(name = "goal_amount", defaultValue = "0.0") val goalAmount: Money,
    @ColumnInfo(name = "period_months", defaultValue = "12") val periodMonths: Int,

    @ColumnInfo(name = "constitution_url") val constitutionUrl: String?,
    @ColumnInfo(name = "constitution_status", defaultValue = "PENDING") val constitutionStatus: DocumentStatus,

    // Burial Society specific
    @ColumnInfo(name = "max_beneficiaries", defaultValue = "0") val maxBeneficiaries: Int,
    @ColumnInfo(name = "beneficiary_increase_pct", defaultValue = "0.0") val beneficiaryIncreasePct: Double,

    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "investment_payout_date") val investmentPayoutDate: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geohash: String? = null,
    @ColumnInfo(name = "rosca_rotation_method") val rotationMethod: RoscaRotationMethod = RoscaRotationMethod.FIXED,

    // Loan settings
    @ColumnInfo(name = "loan_interest_rate") val loanInterestRate: Double? = null,
    @ColumnInfo(name = "loan_max_amount") val loanMaxAmount: Money? = null,
    @ColumnInfo(name = "loan_max_months") val loanMaxMonths: Int? = null,

    @ColumnInfo(name = "financial_provider_id") val financialProviderId: String? = null,
    @ColumnInfo(name = "financial_provider_metadata") val financialProviderMetadata: Map<String, String> = emptyMap(),

    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface GroupDao {
    @Query("SELECT * FROM `groups` WHERE is_public = 1 AND registration_paid = 1 ORDER BY name ASC")
    fun observePublicGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` WHERE id = :id")
    suspend fun getGroupById(id: String): GroupEntity?

    @Query("SELECT * FROM `groups` WHERE id = :id")
    fun observeGroupById(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM `groups` WHERE admin_user_id = :adminId ORDER BY name ASC")
    fun observeGroupsByAdmin(adminId: String): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` ORDER BY name ASC")
    fun observeAllGroups(): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroups(groups: List<GroupEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: GroupEntity): Long

    @Query("DELETE FROM `groups`")
    suspend fun clearAll()

    @Query("DELETE FROM `groups` WHERE id = :id")
    suspend fun deleteGroup(id: String)

    @Query("SELECT * FROM `groups`")
    fun getAllGroups(): List<GroupEntity>

    @Transaction
    suspend fun syncAllManagedGroups(groups: List<GroupEntity>) {
        val currentIds = getAllGroups().map { it.id }.toSet()
        val incomingIds = groups.map { it.id }.toSet()
        val orphans = currentIds - incomingIds
        orphans.forEach { deleteGroup(it) }
        upsertGroups(groups)
    }

    @Transaction
    suspend fun syncPublicGroups(groups: List<GroupEntity>) {
        val newIds = groups.map { it.id }
        deleteObsoletePublicGroups(newIds)
        groups.forEach { group ->
            insertGroupIfMissing(group)
            updateGroupMinimal(
                id = group.id,
                name = group.name,
                type = group.type.name,
                province = group.province,
                city = group.city,
                township = group.township,
                logoEmoji = group.logoEmoji,
                latitude = group.latitude,
                longitude = group.longitude,
                registrationPaid = if (group.registrationPaid) 1 else 0,
                isPublic = if (group.isPublic) 1 else 0,
                currentMembers = group.currentMembers,
                maxMembers = group.maxMembers,
            )
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroupIfMissing(group: GroupEntity)

    @Query(
        """
        UPDATE `groups` SET
            name = :name,
            type = :type,
            province = :province,
            city = :city,
            township = :township,
            logo_emoji = :logoEmoji,
            latitude = :latitude,
            longitude = :longitude,
            registration_paid = :registrationPaid,
            is_public = :isPublic,
            current_members = :currentMembers,
            max_members = :maxMembers
        WHERE id = :id
        """,
    )
    suspend fun updateGroupMinimal(
        id: String,
        name: String,
        type: String,
        province: String,
        city: String,
        township: String,
        logoEmoji: String,
        latitude: Double?,
        longitude: Double?,
        registrationPaid: Int,
        isPublic: Int,
        currentMembers: Int,
        maxMembers: Int,
    )

    @Query("DELETE FROM `groups` WHERE is_public = 1 AND registration_paid = 1 AND id NOT IN (:newIds)")
    suspend fun deleteObsoletePublicGroups(newIds: List<String>)
}
