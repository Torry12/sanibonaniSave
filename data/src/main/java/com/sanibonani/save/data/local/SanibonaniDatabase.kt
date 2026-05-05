package com.sanibonani.save.data.local

import androidx.room.*
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

// ── Converters for complex types ──────────────────────────────────────────────
class Converters {
    @TypeConverter fun fromGroupType(v: GroupType): String = v.name
    @TypeConverter fun toGroupType(v: String): GroupType = try { 
        GroupType.entries.find { it.name == v || it.displayName.replace(" ", "_").lowercase() == v.lowercase() } ?: GroupType.OTHER 
    } catch (e: Exception) { GroupType.OTHER }

    @TypeConverter fun fromAdminFeeState(v: AdminFeeState): String = v.name
    @TypeConverter fun toAdminFeeState(v: String): AdminFeeState = try { 
        AdminFeeState.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: AdminFeeState.DUE 
    } catch (e: Exception) { AdminFeeState.DUE }

    @TypeConverter fun fromMemberStatus(v: MemberStatus): String = v.name
    @TypeConverter fun toMemberStatus(v: String): MemberStatus = try { 
        MemberStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: MemberStatus.PROBATION 
    } catch (e: Exception) { MemberStatus.PROBATION }

    @TypeConverter fun fromDocStatus(v: DocumentStatus): String = v.name
    @TypeConverter fun toDocStatus(v: String): DocumentStatus = try { 
        DocumentStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: DocumentStatus.PENDING 
    } catch (e: Exception) { DocumentStatus.PENDING }

    @TypeConverter fun fromNotifPref(v: NotificationPref): String = v.name
    @TypeConverter fun toNotifPref(v: String): NotificationPref = try { 
        NotificationPref.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: NotificationPref.BOTH 
    } catch (e: Exception) { NotificationPref.BOTH }

    @TypeConverter fun fromContribStatus(v: ContributionStatus): String = v.name
    @TypeConverter fun toContribStatus(v: String): ContributionStatus = try { 
        ContributionStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: ContributionStatus.DUE 
    } catch (e: Exception) { ContributionStatus.DUE }

    @TypeConverter fun fromPaymentType(v: PaymentType): String = v.name
    @TypeConverter fun toPaymentType(v: String): PaymentType = try { 
        PaymentType.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: PaymentType.CONTRIBUTION 
    } catch (e: Exception) { PaymentType.CONTRIBUTION }

    @TypeConverter fun fromPaymentMethod(v: PaymentMethod): String = v.name
    @TypeConverter fun toPaymentMethod(v: String): PaymentMethod = try { 
        PaymentMethod.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: PaymentMethod.YOCO 
    } catch (e: Exception) { PaymentMethod.YOCO }

    @TypeConverter fun fromPaymentStatus(v: PaymentStatus): String = v.name
    @TypeConverter fun toPaymentStatus(v: String): PaymentStatus = try { 
        PaymentStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: PaymentStatus.PENDING 
    } catch (e: Exception) { PaymentStatus.PENDING }

    @TypeConverter fun fromNotifChannel(v: NotifChannel): String = v.name
    @TypeConverter fun toNotifChannel(v: String): NotifChannel = try { 
        NotifChannel.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: NotifChannel.BOTH 
    } catch (e: Exception) { NotifChannel.BOTH }

    @TypeConverter fun fromNotifEvent(v: NotifEvent): String = v.name
    @TypeConverter fun toNotifEvent(v: String): NotifEvent = try { 
        NotifEvent.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: NotifEvent.CUSTOM 
    } catch (e: Exception) { NotifEvent.CUSTOM }

    @TypeConverter fun fromPayoutStatus(v: PayoutStatus): String = v.name
    @TypeConverter fun toPayoutStatus(v: String): PayoutStatus = try { 
        PayoutStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: PayoutStatus.PENDING 
    } catch (e: Exception) { PayoutStatus.PENDING }

    @TypeConverter fun fromLoanStatus(v: LoanStatus): String = v.name
    @TypeConverter fun toLoanStatus(v: String): LoanStatus = try { 
        LoanStatus.entries.find { it.name == v || it.name.lowercase() == v.lowercase() } ?: LoanStatus.PENDING 
    } catch (e: Exception) { LoanStatus.PENDING }
}

// ── Room Entities (mirrors Supabase tables for offline use) ──────────────────

@Entity(
    tableName = "groups",
    indices = [
        Index("is_public"),
        Index("admin_user_id"),
        Index("fee_status")
    ]
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
    @ColumnInfo(name = "joining_fee") val joiningFee: Double,
    @ColumnInfo(name = "monthly_contribution") val monthlyContribution: Double,
    @ColumnInfo(name = "late_fee") val lateFee: Double,
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
    @ColumnInfo(name = "yoco_public_key") val yocoPublicKey: String?,
    val balance: Double,
    @ColumnInfo(name = "admin_user_id") val adminUserId: String?,
    @ColumnInfo(name = "fee_status") val feeStatus: AdminFeeState,
    @ColumnInfo(name = "registration_paid") val registrationPaid: Boolean,
    @ColumnInfo(name = "is_platform_suspended") val isPlatformSuspended: Boolean,
    @ColumnInfo(name = "goal_amount") val goalAmount: Double,
    @ColumnInfo(name = "period_months") val periodMonths: Int,

    @ColumnInfo(name = "constitution_url") val constitutionUrl: String?,
    @ColumnInfo(name = "constitution_status") val constitutionStatus: DocumentStatus,

    // Burial Society specific
    @ColumnInfo(name = "max_beneficiaries") val maxBeneficiaries: Int,
    @ColumnInfo(name = "beneficiary_increase_pct") val beneficiaryIncreasePct: Double,

    @ColumnInfo(name = "created_at") val createdAt: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geohash: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "members", 
    indices = [
        Index("group_id"),
        Index("user_id"),
        Index("status"),
        Index("member_key", unique = true) 
    ]
)
data class MemberEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "user_id") val userId: String?,
    @ColumnInfo(name = "member_key") val memberKey: String?,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "id_number") val idNumber: String,
    val phone: String,
    val email: String,
    val street: String,
    val suburb: String,
    val city: String,
    val province: String,
    val status: MemberStatus,
    @ColumnInfo(name = "joined_at") val joinedAt: String,
    @ColumnInfo(name = "probation_end_at") val probationEndAt: String,
    @ColumnInfo(name = "profile_photo_url") val profilePhotoUrl: String?,
    @ColumnInfo(name = "document_1_url") val document1Url: String?,
    @ColumnInfo(name = "document_1_type") val document1Type: String?,
    @ColumnInfo(name = "document_1_status") val document1Status: DocumentStatus,
    @ColumnInfo(name = "document_2_url") val document2Url: String?,
    @ColumnInfo(name = "document_2_type") val document2Type: String?,
    @ColumnInfo(name = "document_2_status") val document2Status: DocumentStatus,
    @ColumnInfo(name = "document_3_url") val document3Url: String?,
    @ColumnInfo(name = "document_3_type") val document3Type: String?,
    @ColumnInfo(name = "document_3_status") val document3Status: DocumentStatus,
    @ColumnInfo(name = "document_4_url") val document4Url: String?,
    @ColumnInfo(name = "document_4_type") val document4Type: String?,
    @ColumnInfo(name = "document_4_status") val document4Status: DocumentStatus,
    @ColumnInfo(name = "document_5_url") val document5Url: String?,
    @ColumnInfo(name = "document_5_type") val document5Type: String?,
    @ColumnInfo(name = "document_5_status") val document5Status: DocumentStatus,

    // Burial Society specific
    @ColumnInfo(name = "beneficiary_count") val beneficiaryCount: Int?,
    @ColumnInfo(name = "beneficiary_over_65_count") val beneficiaryOver65Count: Int?,
    @ColumnInfo(name = "monthly_contribution_override") val monthlyContributionOverride: Double?,

    @ColumnInfo(name = "total_contributions") val totalContributions: Int?,
    @ColumnInfo(name = "total_paid") val totalPaid: Double = 0.0,
    @ColumnInfo(name = "fcm_token") val fcmToken: String?,
    @ColumnInfo(name = "notification_pref") val notificationPref: NotificationPref,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "beneficiaries",
    primaryKeys = ["id", "group_id", "member_id"],
    indices = [
        Index("group_id"),
        Index("member_id")
    ]
)
data class BeneficiaryEntity(
    val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "id_number") val idNumber: String?,
    val relationship: String?,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: String?,
    @ColumnInfo(name = "is_over_65") val isOver65: Boolean,
    @ColumnInfo(name = "document_url") val documentUrl: String? = null,
    @ColumnInfo(name = "document_status") val documentStatus: String = "pending",
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "contributions", 
    indices = [
        Index("member_id"), 
        Index("group_id"),
        Index("status"),
        Index("due_date")
    ]
)
data class ContributionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "policy_id") val policyId: String?,
    val amount: Double,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "due_date") val dueDate: String,
    @ColumnInfo(name = "paid_at") val paidAt: String?,
    val status: ContributionStatus,
    val type: String = "contribution",
    @ColumnInfo(name = "payment_method") val paymentMethod: String = "yoco",
    @ColumnInfo(name = "late_fees_applied") val lateFeesApplied: Boolean,
    @ColumnInfo(name = "yoco_transaction_id") val yocoTransactionId: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payments", 
    indices = [
        Index("member_id"), 
        Index("group_id"),
        Index("status"),
        Index("payment_type"),
        Index("created_at")
    ]
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val amount: Double,
    @ColumnInfo(name = "payment_type") val paymentType: PaymentType,
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod,
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    val status: PaymentStatus,
    @ColumnInfo(name = "processed_at") val processedAt: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notifications",
    indices = [
        Index("group_id"),
        Index("member_id"),
        Index("trigger_event"),
        Index("created_at")
    ]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "member_id") val memberId: String?,
    val message: String,
    val channel: NotifChannel,
    @ColumnInfo(name = "trigger_event") val triggerEvent: NotifEvent,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "member_documents",
    indices = [
        Index("member_id"),
        Index("group_id")
    ]
)
data class MemberDocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val label: String,
    @ColumnInfo(name = "document_url") val documentUrl: String,
    @ColumnInfo(name = "document_type") val documentType: String?,
    val status: DocumentStatus,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payouts",
    indices = [
        Index("group_id"),
        Index("status")
    ]
)
data class PayoutEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val amount: Double,
    @ColumnInfo(name = "bank_name") val bankName: String,
    @ColumnInfo(name = "account_no") val accountNo: String,
    @ColumnInfo(name = "branch_code") val branchCode: String,
    val status: PayoutStatus,
    @ColumnInfo(name = "processed_by") val processedBy: String?,
    @ColumnInfo(name = "processed_at") val processedAt: String?,
    @ColumnInfo(name = "yoco_payout_id") val yocoPayoutId: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "loans",
    indices = [
        Index("member_id"),
        Index("group_id"),
        Index("status")
    ]
)
data class LoanEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val amount: Double,
    @ColumnInfo(name = "interest_rate") val interestRate: Double,
    @ColumnInfo(name = "total_to_repay") val totalToRepay: Double,
    @ColumnInfo(name = "total_repaid") val totalRepaid: Double,
    @ColumnInfo(name = "monthly_repayment") val monthlyRepayment: Double,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    @ColumnInfo(name = "next_payment_date") val nextPaymentDate: String?,
    val status: LoanStatus,
    val purpose: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "loan_repayments",
    indices = [
        Index("loan_id"),
        Index("member_id"),
        Index("group_id")
    ]
)
data class LoanRepaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val amount: Double,
    @ColumnInfo(name = "paid_at") val paidAt: String?,
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod,
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "group_health_scores",
    indices = [
        Index("group_id", unique = true),
        Index("generated_at")
    ]
)
data class GroupHealthScoreEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "overall_score") val overallScore: Int,
    val zone: String,
    @ColumnInfo(name = "components_json") val componentsJson: String,
    @ColumnInfo(name = "recommendations_json") val recommendationsJson: String,
    @ColumnInfo(name = "generated_at") val generatedAt: String,
    @ColumnInfo(name = "expires_at") val expiresAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

// ── DAOs ──────────────────────────────────────────────────────────────────────

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
    suspend fun syncPublicGroups(groups: List<GroupEntity>) {
        // Clear ALL groups first, then upsert incoming
        // This ensures no stale data from previous test runs
        clearAll()
        upsertGroups(groups)
    }
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE group_id = :groupId ORDER BY full_name ASC")
    suspend fun getMembersSync(groupId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE group_id = :groupId ORDER BY full_name ASC")
    fun observeMembers(groupId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE user_id = :userId AND group_id = :groupId LIMIT 1")
    suspend fun getMemberByUserId(userId: String, groupId: String): MemberEntity?

    @Query("SELECT * FROM members WHERE user_id = :userId AND group_id = :groupId")
    fun observeMemberByUserId(userId: String, groupId: String): Flow<MemberEntity?>

    @Query("SELECT * FROM members WHERE user_id = :userId")
    suspend fun getAllMemberships(userId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE user_id = :userId")
    fun observeAllMemberships(userId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE member_key = :memberKey LIMIT 1")
    suspend fun getMemberByMemberKey(memberKey: String): MemberEntity?

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getMemberById(id: String): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<MemberEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: MemberEntity): Long

    @Query("DELETE FROM members WHERE group_id = :groupId")
    suspend fun deleteMembersByGroupId(groupId: String)

    @Query("DELETE FROM members")
    suspend fun clearAll()

    @Transaction
    suspend fun syncMembers(groupId: String, members: List<MemberEntity>) {
        // Get current members
        val current = getMembersSync(groupId).map { it.id }.toSet()
        val incoming = members.map { it.id }.toSet()

        // Only delete members that are NO LONGER in the incoming list
        val toDelete = current - incoming
        toDelete.forEach { memberId ->
            deleteMember(memberId)
        }

        // Upsert all incoming members
        upsertMembers(members)
    }

    @Query("DELETE FROM members WHERE id = :id")
    suspend fun deleteMember(id: String)
}

@Dao
interface ContributionDao {
    @Query("SELECT * FROM contributions WHERE member_id = :memberId ORDER BY due_date DESC")
    fun observeContributions(memberId: String): Flow<List<ContributionEntity>>

    @Query("SELECT * FROM contributions WHERE group_id = :groupId ORDER BY due_date DESC")
    fun observeGroupContributions(groupId: String): Flow<List<ContributionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContributions(contributions: List<ContributionEntity>): List<Long>

    @Query("DELETE FROM contributions WHERE group_id = :groupId")
    suspend fun deleteContributionsByGroupId(groupId: String)

    @Query("DELETE FROM contributions WHERE member_id = :memberId")
    suspend fun deleteContributionsByMemberId(memberId: String)

    @Query("DELETE FROM contributions")
    suspend fun clearAll()

    @Transaction
    suspend fun syncGroupContributions(groupId: String, contributions: List<ContributionEntity>) {
        deleteContributionsByGroupId(groupId)
        upsertContributions(contributions)
    }

    @Transaction
    suspend fun syncMemberContributions(memberId: String, contributions: List<ContributionEntity>) {
        deleteContributionsByMemberId(memberId)
        upsertContributions(contributions)
    }
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observeGroupPayments(groupId: String): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayments(payments: List<PaymentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayment(payment: PaymentEntity): Long

    @Query("DELETE FROM payments WHERE group_id = :groupId")
    suspend fun deletePaymentsByGroupId(groupId: String)

    @Query("DELETE FROM payments")
    suspend fun clearAll()

    @Transaction
    suspend fun syncPayments(groupId: String, payments: List<PaymentEntity>) {
        deletePaymentsByGroupId(groupId)
        upsertPayments(payments)
    }
}

@Dao
interface BeneficiaryDao {
    @Query("SELECT * FROM beneficiaries WHERE member_id = :memberId")
    fun observeBeneficiaries(memberId: String): Flow<List<BeneficiaryEntity>>

    @Query("SELECT * FROM beneficiaries WHERE group_id = :groupId")
    fun observeGroupBeneficiaries(groupId: String): Flow<List<BeneficiaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBeneficiaries(beneficiaries: List<BeneficiaryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBeneficiary(beneficiary: BeneficiaryEntity): Long

    @Query("SELECT * FROM beneficiaries WHERE id = :id LIMIT 1")
    suspend fun getBeneficiaryById(id: String): BeneficiaryEntity?

    @Query("DELETE FROM beneficiaries WHERE group_id = :groupId AND member_id = :memberId AND id = :id")
    suspend fun deleteBeneficiary(groupId: String, memberId: String, id: String)

    @Query("DELETE FROM beneficiaries WHERE member_id = :memberId")
    suspend fun deleteBeneficiariesByMemberId(memberId: String)

    @Query("DELETE FROM beneficiaries")
    suspend fun clearAll()

    @Transaction
    suspend fun syncBeneficiaries(memberId: String, beneficiaries: List<BeneficiaryEntity>) {
        deleteBeneficiariesByMemberId(memberId)
        upsertBeneficiaries(beneficiaries)
    }
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observeNotifications(groupId: String): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotifications(notifications: List<NotificationEntity>): List<Long>

    @Query("DELETE FROM notifications WHERE group_id = :groupId")
    suspend fun deleteNotificationsByGroupId(groupId: String)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()

    @Transaction
    suspend fun syncNotifications(groupId: String, notifications: List<NotificationEntity>) {
        deleteNotificationsByGroupId(groupId)
        upsertNotifications(notifications)
    }
}

@Dao
interface PayoutDao {
    @Query("SELECT * FROM payouts WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observePayouts(groupId: String): Flow<List<PayoutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayouts(payouts: List<PayoutEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayout(payout: PayoutEntity): Long

    @Query("DELETE FROM payouts WHERE group_id = :groupId")
    suspend fun deletePayoutsByGroupId(groupId: String)

    @Query("DELETE FROM payouts")
    suspend fun clearAll()

    @Transaction
    suspend fun syncPayouts(groupId: String, payouts: List<PayoutEntity>) {
        deletePayoutsByGroupId(groupId)
        upsertPayouts(payouts)
    }
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans WHERE member_id = :memberId ORDER BY created_at DESC")
    fun observeLoans(memberId: String): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observeGroupLoans(groupId: String): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getLoanById(id: String): LoanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoans(loans: List<LoanEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoan(loan: LoanEntity): Long

    @Query("DELETE FROM loans WHERE group_id = :groupId")
    suspend fun deleteLoansByGroupId(groupId: String)

    @Query("DELETE FROM loans WHERE member_id = :memberId")
    suspend fun deleteLoansByMemberId(memberId: String)

    @Query("DELETE FROM loans")
    suspend fun clearAllLoans()

    @Transaction
    suspend fun syncGroupLoans(groupId: String, loans: List<LoanEntity>) {
        deleteLoansByGroupId(groupId)
        upsertLoans(loans)
    }

    @Query("SELECT * FROM loan_repayments WHERE loan_id = :loanId ORDER BY paid_at DESC")
    fun observeRepayments(loanId: String): Flow<List<LoanRepaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRepayments(repayments: List<LoanRepaymentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRepayment(repayment: LoanRepaymentEntity): Long

    @Query("DELETE FROM loan_repayments WHERE loan_id = :loanId")
    suspend fun deleteRepaymentsByLoanId(loanId: String)

    @Query("DELETE FROM loan_repayments")
    suspend fun clearAllRepayments()
}

@Dao
interface MemberDocumentDao {
    @Query("SELECT * FROM member_documents WHERE member_id = :memberId")
    fun observeDocuments(memberId: String): Flow<List<MemberDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocuments(documents: List<MemberDocumentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: MemberDocumentEntity): Long

    @Query("DELETE FROM member_documents WHERE member_id = :memberId")
    suspend fun deleteDocumentsByMemberId(memberId: String)

    @Query("DELETE FROM member_documents")
    suspend fun clearAll()

    @Transaction
    suspend fun syncDocuments(memberId: String, documents: List<MemberDocumentEntity>) {
        deleteDocumentsByMemberId(memberId)
        upsertDocuments(documents)
    }
}

@Dao
interface GroupHealthScoreDao {
    @Query("SELECT * FROM group_health_scores WHERE group_id = :groupId LIMIT 1")
    fun observeByGroupId(groupId: String): Flow<GroupHealthScoreEntity?>

    @Query("SELECT * FROM group_health_scores WHERE group_id = :groupId LIMIT 1")
    suspend fun getByGroupId(groupId: String): GroupHealthScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(score: GroupHealthScoreEntity): Long

    @Query("DELETE FROM group_health_scores WHERE group_id = :groupId")
    suspend fun deleteByGroupId(groupId: String)

    @Query("DELETE FROM group_health_scores")
    suspend fun clearAll()
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(
    entities = [
        GroupEntity::class, 
        MemberEntity::class, 
        ContributionEntity::class, 
        PaymentEntity::class, 
        BeneficiaryEntity::class, 
        NotificationEntity::class, 
        PayoutEntity::class, 
        MemberDocumentEntity::class,
        LoanEntity::class,
        LoanRepaymentEntity::class,
        GroupHealthScoreEntity::class
    ],
    version  = 35,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SanibonaniDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
    abstract fun contributionDao(): ContributionDao
    abstract fun paymentDao(): PaymentDao
    abstract fun beneficiaryDao(): BeneficiaryDao
    abstract fun notificationDao(): NotificationDao
    abstract fun payoutDao(): PayoutDao
    abstract fun memberDocumentDao(): MemberDocumentDao
    abstract fun loanDao(): LoanDao
    abstract fun groupHealthScoreDao(): GroupHealthScoreDao

    suspend fun clearAllData() {
        groupDao().clearAll()
        memberDao().clearAll()
        contributionDao().clearAll()
        paymentDao().clearAll()
        beneficiaryDao().clearAll()
        notificationDao().clearAll()
        payoutDao().clearAll()
        memberDocumentDao().clearAll()
        loanDao().clearAllLoans()
        loanDao().clearAllRepayments()
        groupHealthScoreDao().clearAll()
    }
}
