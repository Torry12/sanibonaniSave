package com.sanibonani.save.data.local

import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import com.sanibonani.save.domain.model.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "loans",
    indices = [
        Index("member_id"),
        Index("group_id"),
        Index("status"),
    ],
)
data class LoanEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val amount: Money,
    @ColumnInfo(name = "interest_rate") val interestRate: Double,
    @ColumnInfo(name = "total_to_repay") val totalToRepay: Money,
    @ColumnInfo(name = "total_repaid") val totalRepaid: Money,
    @ColumnInfo(name = "monthly_repayment") val monthlyRepayment: Money,
    @ColumnInfo(name = "start_date") val startDate: String?,
    @ColumnInfo(name = "end_date") val endDate: String?,
    @ColumnInfo(name = "next_payment_date") val nextPaymentDate: String?,
    val status: LoanStatus,
    val purpose: String?,
    @ColumnInfo(name = "contract_url") val contractUrl: String? = null,
    @ColumnInfo(name = "surety_amount") val suretyAmount: Money? = null,
    @ColumnInfo(name = "reviewed_by") val reviewedBy: String? = null,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: String? = null,
    @ColumnInfo(name = "admin_notes") val adminNotes: String? = null,
    @ColumnInfo(name = "rejection_reason") val rejectionReason: String? = null,
    @ColumnInfo(name = "amortization_type") val amortizationType: AmortizationType = AmortizationType.FLAT,
    @ColumnInfo(name = "effective_apr") val effectiveApr: Double? = null,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
)

@Entity(
    tableName = "loan_repayments",
    indices = [
        Index("loan_id"),
        Index("member_id"),
        Index("group_id"),
    ],
)
data class LoanRepaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val amount: Money,
    @ColumnInfo(name = "paid_at") val paidAt: String?,
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod,
    @ColumnInfo(name = "transaction_id") val transactionId: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
)

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans WHERE member_id = :memberId ORDER BY created_at DESC")
    fun observeLoans(memberId: String): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE group_id = :groupId ORDER BY created_at DESC")
    fun observeGroupLoans(groupId: String): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE group_id = :groupId")
    suspend fun getGroupLoans(groupId: String): List<LoanEntity>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getLoanById(id: String): LoanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoans(loans: List<LoanEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoan(loan: LoanEntity): Long

    @Query("DELETE FROM loans WHERE group_id = :groupId")
    suspend fun deleteLoansByGroupId(groupId: String)

    @Query("DELETE FROM loans")
    suspend fun clearAllLoans()

    @RoomTransaction
    suspend fun syncGroupLoans(groupId: String, loans: List<LoanEntity>) {
        val current = getGroupLoans(groupId).map { it.id }.toSet()
        val incoming = loans.map { it.id }.toSet()
        (current - incoming).forEach { deleteLoan(it) }
        upsertLoans(loans)
    }

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoan(id: String)

    @Query("SELECT * FROM loan_repayments WHERE loan_id = :loanId ORDER BY paid_at DESC")
    fun observeRepayments(loanId: String): Flow<List<LoanRepaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRepayments(repayments: List<LoanRepaymentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRepayment(repayment: LoanRepaymentEntity): Long

    @Query("DELETE FROM loan_repayments WHERE loan_id = :loanId")
    suspend fun deleteRepaymentsByLoanId(loanId: String)

    @Query("DELETE FROM loan_repayments WHERE id = :id")
    suspend fun deleteRepaymentById(id: String)

    @Query("DELETE FROM loan_repayments")
    suspend fun clearAllRepayments()
}

// ── Mappers ──────────────────────────────────────────────────────────────────

fun LoanEntity.toModel() = Loan(
    id = id,
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    interestRate = interestRate,
    totalToRepay = totalToRepay,
    totalRepaid = totalRepaid,
    monthlyRepayment = monthlyRepayment,
    startDate = startDate,
    endDate = endDate,
    nextPaymentDate = nextPaymentDate,
    status = status,
    purpose = purpose,
    contractUrl = contractUrl,
    suretyAmount = suretyAmount,
    reviewedBy = reviewedBy,
    reviewedAt = reviewedAt,
    adminNotes = adminNotes,
    rejectionReason = rejectionReason,
    amortizationType = amortizationType,
    effectiveApr = effectiveApr,
    createdAt = createdAt
)

fun Loan.toEntity() = LoanEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    interestRate = interestRate,
    totalToRepay = totalToRepay,
    totalRepaid = totalRepaid,
    monthlyRepayment = monthlyRepayment,
    startDate = startDate,
    endDate = endDate,
    nextPaymentDate = nextPaymentDate,
    status = status,
    purpose = purpose,
    contractUrl = contractUrl,
    suretyAmount = suretyAmount,
    reviewedBy = reviewedBy,
    reviewedAt = reviewedAt,
    adminNotes = adminNotes,
    rejectionReason = rejectionReason,
    amortizationType = amortizationType,
    effectiveApr = effectiveApr,
    createdAt = createdAt
)

fun LoanRepaymentEntity.toModel() = LoanRepayment(
    id = id,
    loanId = loanId,
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    paidAt = paidAt,
    paymentMethod = paymentMethod,
    transactionId = transactionId,
    createdAt = createdAt
)

fun LoanRepayment.toEntity() = LoanRepaymentEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    loanId = loanId,
    memberId = memberId,
    groupId = groupId,
    amount = amount,
    paidAt = paidAt,
    paymentMethod = paymentMethod,
    transactionId = transactionId,
    createdAt = createdAt
)
