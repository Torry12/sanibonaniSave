package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.LoanEntity
import com.sanibonani.save.data.local.LoanRepaymentEntity
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.LoanRepository
import com.sanibonani.save.domain.repository.StorageRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import kotlin.math.pow

class LoanRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase,
    private val storageRepo: StorageRepository
) : BaseRepository("LoanRepository"), LoanRepository {

    private val LOAN_COLUMNS = "id,member_id,group_id,amount,interest_rate,total_to_repay,total_repaid,monthly_repayment,start_date,end_date,next_payment_date,status,purpose,created_at"
    private val REPAYMENT_COLUMNS = "id,loan_id,member_id,group_id,amount,paid_at,payment_method,transaction_id,created_at"

    override suspend fun requestLoan(loan: Loan): Result<String> = runCatching {
        val inserted = supabase.postgrest["loans"].insert(loan) {
            select(columns = Columns.raw(LOAN_COLUMNS))
        }.decodeSingle<Loan>()
        db.loanDao().upsertLoan(inserted.toEntity())
        inserted.id ?: throw IllegalStateException("Loan ID missing after insert")
    }

    override suspend fun getLoanById(loanId: String): Result<Loan> = runCatching {
        val loan = supabase.postgrest["loans"].select(columns = Columns.raw(LOAN_COLUMNS)) {
            filter { eq("id", loanId) }
        }.decodeSingle<Loan>()
        db.loanDao().upsertLoan(loan.toEntity())
        loan
    }.recoverCatching { exception ->
        db.loanDao().getLoanById(loanId)?.toModel() ?: throw exception
    }

    override fun getMemberLoans(memberId: String): Flow<Result<List<Loan>>> = observeAndSync(
        dbFlow = db.loanDao().observeLoans(memberId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["loans"].select(columns = Columns.raw(LOAN_COLUMNS)) {
                filter { eq("member_id", memberId) }
            }.decodeList<Loan>()
        },
        cacheSync = { list -> 
            // We don't have a syncMemberLoans yet, but we can clear and insert for now
            // Or just use upsertLoans if we don't care about deletions for now
            db.loanDao().upsertLoans(list)
        }
    )

    override fun getGroupLoans(groupId: String): Flow<Result<List<Loan>>> = observeAndSync(
        dbFlow = db.loanDao().observeGroupLoans(groupId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["loans"].select(columns = Columns.raw(LOAN_COLUMNS)) {
                filter { eq("group_id", groupId) }
            }.decodeList<Loan>()
        },
        cacheSync = { list -> db.loanDao().syncGroupLoans(groupId, list) }
    )

    override suspend fun approveLoan(loanId: String): Result<Unit> = runCatching {
        supabase.postgrest["loans"].update(mapOf(
            "status" to LoanStatus.APPROVED.name.lowercase(),
            "reviewed_by" to supabase.auth.currentUserOrNull()?.id,
            "reviewed_at" to java.time.Instant.now().toString()
        )) {
            filter { eq("id", loanId) }
        }
        // Sync local
        getLoanById(loanId)
        Unit
    }

    override suspend fun rejectLoan(loanId: String, reason: String): Result<Unit> = runCatching {
        supabase.postgrest["loans"].update(mapOf(
            "status" to LoanStatus.REJECTED.name.lowercase(),
            "rejection_reason" to reason,
            "reviewed_by" to supabase.auth.currentUserOrNull()?.id,
            "reviewed_at" to java.time.Instant.now().toString()
        )) {
            filter { eq("id", loanId) }
        }
        // Sync local
        getLoanById(loanId)
        Unit
    }

    override suspend fun disburseLoan(loanId: String, paymentMethod: PaymentMethod): Result<Unit> = runCatching {
        val adminId = supabase.auth.currentUserOrNull()?.id ?: throw IllegalStateException("Not authenticated")
        
        supabase.postgrest.rpc("disburse_loan_v1", buildJsonObject {
            put("p_loan_id", loanId)
            put("p_admin_id", adminId)
            put("p_payment_method", paymentMethod.name.lowercase())
        })
        
        // Sync local
        getLoanById(loanId)
        Unit
    }

    override suspend fun updateLoanContract(loanId: String, contractUrl: String): Result<Unit> = runCatching {
        supabase.postgrest["loans"].update(mapOf("contract_url" to contractUrl)) {
            filter { eq("id", loanId) }
        }
        getLoanById(loanId)
        Unit
    }

    override suspend fun uploadLoanContract(loanId: String, byteArray: ByteArray, fileName: String): Result<String> = runCatching {
        val path = "contracts/$loanId/$fileName"
        val uploadedPath = storageRepo.uploadFile("loan_contracts", path, byteArray).getOrThrow()
        val publicUrl = storageRepo.getPublicUrl("loan_contracts", uploadedPath)
        updateLoanContract(loanId, publicUrl).getOrThrow()
        publicUrl
    }

    override suspend fun acceptLoanAgreement(loanId: String): Result<Unit> = runCatching {
        // Status might already be APPROVED, this could be for recording member's acceptance
        // For now, let's just ensure it stays APPROVED or moves to it.
        supabase.postgrest["loans"].update(mapOf("status" to LoanStatus.APPROVED.name.lowercase())) {
            filter { eq("id", loanId) }
        }
        getLoanById(loanId)
        Unit
    }

    override suspend fun recordRepayment(repayment: LoanRepayment): Result<Unit> = runCatching {
        val inserted = supabase.postgrest["loan_repayments"].insert(repayment) {
            select(columns = Columns.raw(REPAYMENT_COLUMNS))
        }.decodeSingle<LoanRepayment>()
        
        db.loanDao().upsertRepayment(inserted.toEntity())
        
        // The balance update on the loan itself should ideally be handled by a DB trigger,
        // but let's sync the loan record too.
        getLoanById(repayment.loanId)
        Unit
    }

    override suspend fun calculateInterest(amount: Double, rate: Double, months: Int): Double {
        // Simple monthly interest: Total = P * (1 + r*t)
        // Or Compound: Total = P * (1 + r)^t
        // Let's go with simple for now as it's common in stokvels
        return amount * (rate / 100) * (months / 12.0)
    }

    override suspend fun getRepayments(loanId: String): Flow<Result<List<LoanRepayment>>> = observeAndSync(
        dbFlow = db.loanDao().observeRepayments(loanId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["loan_repayments"].select(columns = Columns.raw(REPAYMENT_COLUMNS)) {
                filter { eq("loan_id", loanId) }
            }.decodeList<LoanRepayment>()
        },
        cacheSync = { list -> 
            db.loanDao().deleteRepaymentsByLoanId(loanId)
            db.loanDao().upsertRepayments(list)
        }
    )
}
