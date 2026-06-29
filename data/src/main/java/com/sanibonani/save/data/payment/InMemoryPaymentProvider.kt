package com.sanibonani.save.data.payment

import com.sanibonani.save.domain.payment.PaymentProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryPaymentProvider @Inject constructor() : PaymentProvider {
    override suspend fun collect(amount: Long, currency: String): Result<String> =
        Result.success("demo-collect-tx-id")

    override suspend fun disburse(amount: Long, currency: String): Result<String> =
        Result.success("demo-disburse-tx-id")

    override suspend fun refund(transactionId: String): Result<Boolean> =
        Result.success(true)

    override suspend fun verify(transactionId: String): Result<Boolean> =
        Result.success(true)
}

