package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanRepayment
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.model.Money
import kotlinx.coroutines.flow.Flow

interface RemoteLoanDataSource {
    suspend fun requestLoan(loan: Loan): Result<String>
    suspend fun getLoanById(id: String): Result<Loan>
    suspend fun getMemberLoans(memberId: String): Result<List<Loan>>
    suspend fun getGroupLoans(groupId: String): Result<List<Loan>>
    suspend fun getAllPendingLoans(): Result<List<Loan>>
    suspend fun updateLoanStatus(loanId: String, status: LoanStatus, reviewedBy: String?, reviewedAt: String?, rejectionReason: String? = null): Result<Unit>
    suspend fun disburseLoan(loanId: String, adminId: String, paymentMethod: PaymentMethod): Result<Unit>
    suspend fun updateLoanContract(loanId: String, contractUrl: String): Result<Unit>
    suspend fun recordRepayment(repayment: LoanRepayment): Result<com.sanibonani.save.domain.model.RecordRepaymentResult>
    suspend fun getRepayments(loanId: String): Result<List<LoanRepayment>>
}

interface LocalLoanDataSource {
    fun observeMemberLoans(memberId: String): Flow<List<Loan>>
    fun observeGroupLoans(groupId: String): Flow<List<Loan>>
    suspend fun saveLoans(loans: List<Loan>)
    suspend fun saveLoan(loan: Loan)
    suspend fun syncGroupLoans(groupId: String, loans: List<Loan>)
    fun observeRepayments(loanId: String): Flow<List<LoanRepayment>>
    suspend fun saveRepayments(repayments: List<LoanRepayment>)
    suspend fun deleteRepaymentsByLoanId(loanId: String)
    suspend fun clearAll()
}
