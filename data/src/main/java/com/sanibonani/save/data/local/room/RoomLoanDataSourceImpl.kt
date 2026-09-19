package com.sanibonani.save.data.local.room

import com.sanibonani.save.data.datasource.LocalLoanDataSource
import com.sanibonani.save.data.local.LoanDao
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanRepayment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLoanDataSourceImpl @Inject constructor(
    private val loanDao: LoanDao
) : LocalLoanDataSource {

    override fun observeMemberLoans(memberId: String): Flow<List<Loan>> =
        loanDao.observeLoans(memberId).map { list -> list.map { it.toModel() } }

    override fun observeGroupLoans(groupId: String): Flow<List<Loan>> =
        loanDao.observeGroupLoans(groupId).map { list -> list.map { it.toModel() } }

    override suspend fun saveLoans(loans: List<Loan>) {
        loanDao.upsertLoans(loans.map { it.toEntity() })
    }

    override suspend fun saveLoan(loan: Loan) {
        loanDao.upsertLoan(loan.toEntity())
    }

    override suspend fun syncGroupLoans(groupId: String, loans: List<Loan>) {
        loanDao.syncGroupLoans(groupId, loans.map { it.toEntity() })
    }

    override fun observeRepayments(loanId: String): Flow<List<LoanRepayment>> =
        loanDao.observeRepayments(loanId).map { list -> list.map { it.toModel() } }

    override suspend fun saveRepayments(repayments: List<LoanRepayment>) {
        loanDao.upsertRepayments(repayments.map { it.toEntity() })
    }

    override suspend fun deleteRepaymentsByLoanId(loanId: String) {
        loanDao.deleteRepaymentsByLoanId(loanId)
    }

    override suspend fun clearAll() {
        loanDao.clearAllLoans()
        loanDao.clearAllRepayments()
    }
}
