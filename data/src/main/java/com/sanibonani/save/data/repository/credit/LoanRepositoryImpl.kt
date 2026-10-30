package com.sanibonani.save.data.repository.credit

import com.sanibonani.save.data.datasource.LocalLoanDataSource
import com.sanibonani.save.data.datasource.RemoteLoanDataSource
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanRepayment
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.PaymentMethod
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.service.FileStorageService
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import com.sanibonani.save.domain.utils.safeRunCatching

class LoanRepositoryImpl @Inject constructor(
    private val remoteSource: RemoteLoanDataSource,
    private val localSource: LocalLoanDataSource,
    private val fileStorageService: FileStorageService,
    private val json: Json,
    private val pendingOpDao: com.sanibonani.save.data.local.PendingOperationDao,
    private val groupDao: com.sanibonani.save.data.local.GroupDao
) : BaseRepository("LoanRepository"), LoanRepository {

    override suspend fun requestLoan(loan: Loan): Result<String> = authoritativeCall("requestLoan") {
        val loanId = remoteSource.requestLoan(loan).getOrThrow()
        val saved = remoteSource.getLoanById(loanId).getOrThrow()
        localSource.saveLoan(saved)
        loanId
    }

    override suspend fun getLoanById(loanId: String): Result<Loan> = safeRunCatching {
        remoteSource.getLoanById(loanId).getOrElse {
            localSource.getLoanById(loanId) ?: throw it
        }
    }

    override fun getMemberLoans(memberId: String): Flow<Result<List<Loan>>> = observeAndSync(
        dbFlow = localSource.observeMemberLoans(memberId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getMemberLoans(memberId).getOrThrow() },
        cacheSync = { localSource.saveLoans(it) }
    )

    override fun getGroupLoans(groupId: String): Flow<Result<List<Loan>>> = observeAndSync(
        dbFlow = localSource.observeGroupLoans(groupId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getGroupLoans(groupId).getOrThrow() },
        cacheSync = { localSource.syncGroupLoans(groupId, it) }
    )

    override suspend fun getGroupLoansSync(groupId: String): Result<List<Loan>> = safeRunCatching {
        val remote = remoteSource.getGroupLoans(groupId).getOrThrow()
        localSource.syncGroupLoans(groupId, remote)
        remote
    }

    override suspend fun getAllPendingLoansSync(): Result<List<Loan>> = remoteSource.getAllPendingLoans()

    override suspend fun updateLoanStatus(
        loanId: String,
        status: LoanStatus,
        adminId: String?,
        reason: String?
    ): Result<Unit> = authoritativeCall("updateLoanStatus") {
        remoteSource.updateLoanStatus(loanId, status, adminId, Instant.now().toString(), reason).getOrThrow()
        localSource.saveLoan(remoteSource.getLoanById(loanId).getOrThrow())
    }

    override suspend fun approveLoan(loanId: String, adminId: String?): Result<Unit> =
        authoritativeCall("approveLoan") {
            remoteSource.updateLoanStatus(loanId, LoanStatus.APPROVED, adminId, Instant.now().toString()).getOrThrow()
            localSource.saveLoan(remoteSource.getLoanById(loanId).getOrThrow())
        }

    override suspend fun rejectLoan(loanId: String, reason: String, adminId: String?): Result<Unit> =
        authoritativeCall("rejectLoan") {
            remoteSource.updateLoanStatus(loanId, LoanStatus.REJECTED, adminId, Instant.now().toString(), reason).getOrThrow()
            localSource.saveLoan(remoteSource.getLoanById(loanId).getOrThrow())
        }

    override suspend fun disburseLoan(
        loanId: String,
        paymentMethod: PaymentMethod,
        adminId: String?
    ): Result<Unit> = authoritativeCall("disburseLoan") {
        val finalAdminId = adminId ?: "system"
        remoteSource.disburseLoan(loanId, finalAdminId, paymentMethod).getOrThrow()
        val updated = remoteSource.getLoanById(loanId).getOrThrow()
        localSource.saveLoan(updated)
        com.sanibonani.save.domain.event.EventBus.emit(com.sanibonani.save.domain.event.LoanDisbursedEvent(updated))
    }

    override suspend fun updateLoanContract(loanId: String, contractUrl: String): Result<Unit> =
        authoritativeCall("updateLoanContract") {
            remoteSource.updateLoanContract(loanId, contractUrl).getOrThrow()
            localSource.getLoanById(loanId)?.let { localSource.saveLoan(it.copy(contractUrl = contractUrl)) }
        }

    override suspend fun uploadLoanContract(loanId: String, byteArray: ByteArray, fileName: String): Result<String> =
        authoritativeCall("uploadLoanContract") {
            val extension = fileName.substringAfterLast(".", "pdf")
            val publicUrl = fileStorageService.uploadLoanContract(loanId, byteArray, extension).getOrThrow()
            updateLoanContract(loanId, publicUrl).getOrThrow()
            publicUrl
        }

    override suspend fun acceptLoanAgreement(loanId: String): Result<Unit> =
        authoritativeCall("acceptLoanAgreement") {
            remoteSource.updateLoanStatus(loanId, LoanStatus.APPROVED, null, null).getOrThrow()
            localSource.saveLoan(remoteSource.getLoanById(loanId).getOrThrow())
        }

    override suspend fun recordRepayment(
        repayment: LoanRepayment,
        idempotencyKey: String?
    ): Result<Unit> {
        val stableRepayment = repayment.copy(
            transactionId = repayment.transactionId
                ?: repayment.id
                ?: idempotencyKey
                ?: java.util.UUID.randomUUID().toString()
        )
        return safeApiCall(
            operationType = "loan_repayment",
            groupId = stableRepayment.groupId,
            payload = json.encodeToString(stableRepayment),
            dao = pendingOpDao,
            idempotencyKey = idempotencyKey
        ) {
            val result = remoteSource.recordRepayment(stableRepayment).getOrThrow()
            localSource.saveRepayments(listOf(result.repayment))

            val loan = remoteSource.getLoanById(stableRepayment.loanId).getOrThrow()
            localSource.saveLoan(loan)

            groupDao.getGroupById(stableRepayment.groupId)?.let {
                groupDao.upsertGroup(it.copy(balance = result.newBalance))
            }
        }
    }

    override suspend fun settleExpiredLoan(loanId: String): Result<com.sanibonani.save.domain.model.RecordRepaymentResult> =
        authoritativeCall("settleExpiredLoan") {
            val result = remoteSource.settleExpiredLoan(loanId).getOrThrow()
            localSource.saveRepayments(listOf(result.repayment))
            localSource.saveLoan(remoteSource.getLoanById(loanId).getOrThrow())
            groupDao.getGroupById(result.repayment.groupId)?.let {
                groupDao.upsertGroup(it.copy(balance = result.newBalance))
            }
            result
        }

    override suspend fun getRepayments(loanId: String): Flow<Result<List<LoanRepayment>>> = observeAndSync(
        dbFlow = localSource.observeRepayments(loanId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteSource.getRepayments(loanId).getOrThrow() },
        cacheSync = { localSource.saveRepayments(it) }
    )
}
