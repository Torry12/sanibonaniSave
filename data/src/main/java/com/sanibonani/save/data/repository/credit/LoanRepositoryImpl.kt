package com.sanibonani.save.data.repository.credit

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.data.repository.mappers.*
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.utils.logAndGetMessage
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.model.RecordRepaymentResult
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.StorageRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class LoanRepositoryImpl @Inject constructor(
    private val supabaseProvider: javax.inject.Provider<SupabaseClient>,
    private val dbProvider: javax.inject.Provider<SanibonaniDatabase>,
    private val storageRepo: StorageRepository
) : BaseRepository("LoanRepository"), LoanRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    private val LOAN_COLUMNS = "id,member_id,group_id,amount,interest_rate,total_to_repay,total_repaid,monthly_repayment,start_date,end_date,next_payment_date,status,purpose,created_at"
    private val REPAYMENT_COLUMNS = "id,loan_id,member_id,group_id,amount,paid_at,payment_method,transaction_id,created_at"

    override suspend fun requestLoan(loan: Loan): Result<String> = retryWithExponentialBackoff {
        runCatching {
            val loanId = loan.id ?: java.util.UUID.randomUUID().toString()
            val finalLoan = loan.copy(id = loanId)
            val inserted = supabase.postgrest["loans"].upsert(finalLoan) {
                onConflict = "id"
                select(columns = Columns.raw(LOAN_COLUMNS))
            }.decodeSingle<Loan>()
            db.loanDao().upsertLoan(inserted.toEntity())
            inserted.id ?: loanId
        }
    }

    override suspend fun getLoanById(loanId: String): Result<Loan> = retryWithExponentialBackoff {
        runCatching {
            val loan = supabase.postgrest["loans"].select(columns = Columns.raw(LOAN_COLUMNS)) {
                filter { eq("id", loanId) }
            }.decodeSingle<Loan>()
            db.loanDao().upsertLoan(loan.toEntity())
            loan
        }.recoverCatching { exception ->
            db.loanDao().getLoanById(loanId)?.toModel() ?: throw IllegalStateException(exception.logAndGetMessage(tag))
        }
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

    override suspend fun getGroupLoansSync(groupId: String): Result<List<Loan>> = retryWithExponentialBackoff {
        runCatching {
            val loans = supabase.postgrest["loans"].select(columns = Columns.raw(LOAN_COLUMNS)) {
                filter { eq("group_id", groupId) }
            }.decodeList<Loan>()
            db.loanDao().syncGroupLoans(groupId, loans.map { it.toEntity() })
            loans
        }.recoverCatching {
            db.loanDao().observeGroupLoans(groupId).first().map { it.toModel() }
        }
    }

    override suspend fun getAllPendingLoansSync(): Result<List<Loan>> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["loans"].select(columns = Columns.raw(LOAN_COLUMNS)) {
                filter { eq("status", LoanStatus.PENDING.name.lowercase()) }
            }.decodeList<Loan>()
        }
    }

    override suspend fun approveLoan(loanId: String): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["loans"].update(mapOf(
                "status" to LoanStatus.APPROVED.name.lowercase(),
                "reviewed_by" to supabase.auth.currentUserOrNull()?.id,
                "reviewed_at" to java.time.Instant.now().toString()
            )) {
                filter { eq("id", loanId) }
            }
            getLoanById(loanId)
            Unit
        }
    }

    override suspend fun rejectLoan(loanId: String, reason: String): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["loans"].update(mapOf(
                "status" to LoanStatus.REJECTED.name.lowercase(),
                "rejection_reason" to reason,
                "reviewed_by" to supabase.auth.currentUserOrNull()?.id,
                "reviewed_at" to java.time.Instant.now().toString()
            )) {
                filter { eq("id", loanId) }
            }
            getLoanById(loanId)
            Unit
        }
    }

    override suspend fun disburseLoan(loanId: String, paymentMethod: PaymentMethod, adminId: String?): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            val effectiveAdminId = adminId
                ?: supabase.auth.currentUserOrNull()?.id
                ?: throw IllegalStateException("Not authenticated")

            AppLogger.i(tag, "🚀 Executing disbursement RPC for loan $loanId")
            supabase.postgrest.rpc("approve_and_disburse_loan_v2", buildJsonObject {
                put("p_loan_id", loanId)
                put("p_admin_id", effectiveAdminId)
                put("p_payment_method", paymentMethod.name.lowercase())
            })
            AppLogger.i(tag, "✅ Disbursement RPC success")

            getLoanById(loanId).onSuccess { updatedLoan ->
                com.sanibonani.save.domain.event.EventBus.emit(
                    com.sanibonani.save.domain.event.LoanDisbursedEvent(updatedLoan)
                )
            }
            Unit
        }
    }

    override suspend fun updateLoanContract(loanId: String, contractUrl: String): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["loans"].update(mapOf("contract_url" to contractUrl)) {
                filter { eq("id", loanId) }
            }
            getLoanById(loanId)
            Unit
        }
    }

    override suspend fun uploadLoanContract(loanId: String, byteArray: ByteArray, fileName: String): Result<String> = runCatching {
        val path = "contracts/$loanId/$fileName"
        val uploadedPath = storageRepo.uploadFile("loan_contracts", path, byteArray).getOrThrow()
        val publicUrl = storageRepo.getPublicUrl("loan_contracts", uploadedPath)
        updateLoanContract(loanId, publicUrl).getOrThrow()
        publicUrl
    }

    override suspend fun acceptLoanAgreement(loanId: String): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["loans"].update(mapOf("status" to LoanStatus.APPROVED.name.lowercase())) {
                filter { eq("id", loanId) }
            }
            getLoanById(loanId)
            Unit
        }
    }

    override suspend fun recordRepayment(repayment: LoanRepayment): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            val rpcParams = buildJsonObject {
                put("p_loan_id", repayment.loanId)
                put("p_member_id", repayment.memberId)
                put("p_group_id", repayment.groupId)
                put("p_amount", repayment.amount.amount.toPlainString())
                put("p_payment_method", repayment.paymentMethod.name.lowercase())
            }

            val result = supabase.postgrest.rpc("record_loan_repayment_v1", rpcParams)
                .decodeAs<RecordRepaymentResult>()

            val inserted = result.repayment
            val newBalance = result.newBalance

            db.loanDao().upsertRepayment(inserted.toEntity())
            getLoanById(repayment.loanId)

            val group = db.groupDao().getGroupById(repayment.groupId)?.toModel()
            if (group != null) {
                db.groupDao().upsertGroup(group.copy(balance = newBalance).toEntity())
            }

            Unit
        }
    }

    override suspend fun getActiveLoansCount(memberId: String): Result<Int> = retryWithExponentialBackoff {
        runCatching {
            val loans = supabase.postgrest["loans"].select {
                filter {
                    eq("member_id", memberId)
                    or {
                        eq("status", "active")
                        eq("status", "partially_paid")
                        eq("status", "approved")
                    }
                }
            }.decodeList<Loan>()
            loans.size
        }
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
