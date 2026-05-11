package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanRepayment
import com.sanibonani.save.domain.model.PaymentMethod
import kotlinx.coroutines.flow.Flow

interface LoanRepository {
    suspend fun requestLoan(loan: Loan): Result<String>
    suspend fun getLoanById(loanId: String): Result<Loan>
    fun getMemberLoans(memberId: String): Flow<Result<List<Loan>>>
    fun getGroupLoans(groupId: String): Flow<Result<List<Loan>>>
    suspend fun approveLoan(loanId: String): Result<Unit>
    suspend fun rejectLoan(loanId: String, reason: String): Result<Unit>
    suspend fun disburseLoan(loanId: String, paymentMethod: PaymentMethod): Result<Unit>
    suspend fun recordRepayment(repayment: LoanRepayment): Result<Unit>
    suspend fun calculateInterest(amount: Double, rate: Double, months: Int): Double
    suspend fun getRepayments(loanId: String): Flow<Result<List<LoanRepayment>>>
    suspend fun updateLoanContract(loanId: String, contractUrl: String): Result<Unit>
    suspend fun uploadLoanContract(loanId: String, byteArray: ByteArray, fileName: String): Result<String>
    suspend fun acceptLoanAgreement(loanId: String): Result<Unit>
}
