package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.Payment
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    suspend fun recordPayment(payment: Payment): Result<String>
    fun getPayments(groupId: String): Flow<Result<List<Payment>>>
}
