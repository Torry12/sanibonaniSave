package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.MockBankDirection
import com.sanibonani.save.domain.model.MockBankTransaction
import com.sanibonani.save.domain.model.PaymentStatus
import com.sanibonani.save.domain.model.PaymentType
import kotlinx.coroutines.flow.Flow

interface MockBankRepository {
    fun observeTransactions(): Flow<List<MockBankTransaction>>

    suspend fun createTransaction(
        amount: Double,
        type: PaymentType,
        groupId: String,
        memberId: String?,
        direction: MockBankDirection
    ): Result<MockBankTransaction>

    suspend fun updateTransactionStatus(
        transactionId: String,
        status: PaymentStatus,
        failureReason: String? = null
    ): Result<MockBankTransaction>

    suspend fun getTransaction(transactionId: String): Result<MockBankTransaction>

    suspend fun clearTransactions(): Result<Unit>
}
