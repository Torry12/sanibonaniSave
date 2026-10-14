package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemoteLoanDataSource
import com.sanibonani.save.domain.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class SupabaseLoanDataSourceImpl @Inject constructor(
    private val client: SupabaseClient
) : RemoteLoanDataSource {

    private val LOAN_COLUMNS = "id,member_id,group_id,amount,interest_rate,total_to_repay,total_repaid,monthly_repayment,start_date,end_date,next_payment_date,status,purpose,created_at"
    private val REPAYMENT_COLUMNS = "id,loan_id,member_id,group_id,amount,paid_at,payment_method,transaction_id,created_at"

    override suspend fun requestLoan(loan: Loan): Result<String> = safeRunCatching {
        client.postgrest["loans"].upsert(loan) { onConflict = "id"; select() }.decodeSingle<Loan>().id!!
    }

    override suspend fun getLoanById(id: String): Result<Loan> = safeRunCatching {
        client.postgrest["loans"].select { filter { eq("id", id) } }.decodeSingle<Loan>()
    }

    override suspend fun getMemberLoans(memberId: String): Result<List<Loan>> = safeRunCatching {
        client.postgrest["loans"].select { filter { eq("member_id", memberId) } }.decodeList<Loan>()
    }

    override suspend fun getGroupLoans(groupId: String): Result<List<Loan>> = safeRunCatching {
        client.postgrest["loans"].select { filter { eq("group_id", groupId) } }.decodeList<Loan>()
    }

    override suspend fun getAllPendingLoans(): Result<List<Loan>> = safeRunCatching {
        val statuses = listOf(LoanStatus.PENDING.name.lowercase(), LoanStatus.APPROVED.name.lowercase())
        client.postgrest["loans"].select {
            filter { or { statuses.forEach { eq("status", it) } } }
        }.decodeList<Loan>()
    }

    override suspend fun updateLoanStatus(loanId: String, status: LoanStatus, reviewedBy: String?, reviewedAt: String?, rejectionReason: String?): Result<Unit> = safeRunCatching {
        client.postgrest["loans"].update(buildJsonObject {
            put("status", status.name.lowercase())
            reviewedBy?.let { put("reviewed_by", it) }
            reviewedAt?.let { put("reviewed_at", it) }
            rejectionReason?.let { put("rejection_reason", it) }
        }) { filter { eq("id", loanId) } }
    }

    override suspend fun disburseLoan(loanId: String, adminId: String, paymentMethod: PaymentMethod): Result<Unit> = safeRunCatching {
        val resolvedAdminId = if (adminId.isNotBlank() && adminId != "PLATFORM_ADMIN") adminId else "00000000-0000-0000-0000-000000000000"
        client.postgrest.rpc("approve_and_disburse_loan_v2", buildJsonObject {
            put("p_loan_id", loanId)
            put("p_admin_id", resolvedAdminId)
            put("p_payment_method", paymentMethod.name.lowercase())
        })
    }

    override suspend fun updateLoanContract(loanId: String, contractUrl: String): Result<Unit> = safeRunCatching {
        client.postgrest["loans"].update(buildJsonObject { put("contract_url", contractUrl) }) { filter { eq("id", loanId) } }
    }

    override suspend fun recordRepayment(repayment: LoanRepayment): Result<RecordRepaymentResult> = safeRunCatching {
        val rpcParams = buildJsonObject {
            put("p_loan_id", repayment.loanId)
            put("p_member_id", repayment.memberId)
            put("p_group_id", repayment.groupId)
            put("p_amount", repayment.amount.amount.toPlainString())
            put("p_payment_method", repayment.paymentMethod.name.lowercase())
        }
        val response = client.postgrest.rpc("record_loan_repayment_v1", rpcParams)
        try {
            response.decodeAs<RecordRepaymentResult>()
        } catch (_: Exception) {
            response.decodeList<RecordRepaymentResult>().first()
        }
    }

    override suspend fun getRepayments(loanId: String): Result<List<LoanRepayment>> = safeRunCatching {
        client.postgrest["loan_repayments"].select { filter { eq("loan_id", loanId) } }.decodeList<LoanRepayment>()
    }
}
