package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.model.MockBankDirection
import com.sanibonani.save.domain.model.MockBankTransaction
import com.sanibonani.save.domain.model.PaymentStatus
import com.sanibonani.save.domain.model.PaymentType
import com.sanibonani.save.domain.repository.MockBankRepository
import com.sanibonani.save.domain.utils.OperationKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockBankRepositoryImpl @Inject constructor() : MockBankRepository {
    private val transactions = MutableStateFlow<List<MockBankTransaction>>(emptyList())

    override fun observeTransactions(): Flow<List<MockBankTransaction>> = transactions.asStateFlow()

    override suspend fun createTransaction(
        amount: Double,
        type: PaymentType,
        groupId: String,
        memberId: String?,
        direction: MockBankDirection
    ): Result<MockBankTransaction> = runCatching {
        require(amount > 0.0) { "Mock bank amount must be greater than zero." }
        require(groupId.isNotBlank()) { "Group id is required for mock bank tracking." }

        val now = Clock.System.now().toString()
        val id = OperationKeys.stableUuid(
            "mock_bank_transaction",
            direction.name,
            type.name,
            groupId,
            memberId.orEmpty(),
            amount,
            now
        )
        val reference = "MOCK-BANK-${id.take(8).uppercase()}"
        val transaction = MockBankTransaction(
            id = id,
            reference = reference,
            amount = amount,
            type = type,
            groupId = groupId,
            memberId = memberId,
            direction = direction,
            status = PaymentStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )

        transactions.update { current -> listOf(transaction) + current }
        transaction
    }

    override suspend fun updateTransactionStatus(
        transactionId: String,
        status: PaymentStatus,
        failureReason: String?
    ): Result<MockBankTransaction> = runCatching {
        val now = Clock.System.now().toString()
        var updatedTransaction: MockBankTransaction? = null

        transactions.update { current ->
            current.map { transaction ->
                if (transaction.id == transactionId) {
                    transaction.copy(
                        status = status,
                        updatedAt = now,
                        failureReason = failureReason.takeIf { status == PaymentStatus.FAILED }
                    ).also { updatedTransaction = it }
                } else {
                    transaction
                }
            }
        }

        updatedTransaction ?: throw NoSuchElementException("Mock bank transaction not found.")
    }

    override suspend fun getTransaction(transactionId: String): Result<MockBankTransaction> = runCatching {
        transactions.value.firstOrNull { it.id == transactionId }
            ?: throw NoSuchElementException("Mock bank transaction not found.")
    }

    override suspend fun clearTransactions(): Result<Unit> = runCatching {
        transactions.value = emptyList()
    }
}
