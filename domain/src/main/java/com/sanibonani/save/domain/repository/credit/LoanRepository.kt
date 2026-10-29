package com.sanibonani.save.domain.repository.credit

import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanRepayment
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.RecordRepaymentResult
import kotlinx.coroutines.flow.Flow

interface LoanRepository {
    suspend fun requestLoan(loan: Loan): Result<String>
    suspend fun getLoanById(loanId: String): Result<Loan>
    fun getMemberLoans(memberId: String): Flow<Result<List<Loan>>>
    fun getGroupLoans(groupId: String): Flow<Result<List<Loan>>>
    suspend fun getGroupLoansSync(groupId: String): Result<List<Loan>>
    suspend fun getAllPendingLoansSync(): Result<List<Loan>>
    suspend fun approveLoan(loanId: String, adminId: String? = null): Result<Unit>
    suspend fun rejectLoan(loanId: String, reason: String, adminId: String? = null): Result<Unit>
    suspend fun updateLoanStatus(
        loanId: String,
        status: LoanStatus,
        adminId: String? = null,
        reason: String? = null
    ): Result<Unit>
    suspend fun disburseLoan(loanId: String, paymentMethod: PaymentMethod, adminId: String? = null): Result<Unit>
    suspend fun recordRepayment(repayment: LoanRepayment, idempotencyKey: String? = null): Result<Unit>
    suspend fun settleExpiredLoan(loanId: String): Result<RecordRepaymentResult>
    suspend fun getRepayments(loanId: String): Flow<Result<List<LoanRepayment>>>
    suspend fun updateLoanContract(loanId: String, contractUrl: String): Result<Unit>
    suspend fun uploadLoanContract(loanId: String, byteArray: ByteArray, fileName: String): Result<String>
    suspend fun acceptLoanAgreement(loanId: String): Result<Unit>
}
