package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.model.PlatformFee

interface RemoteFinancialDataSource {
    suspend fun getVerifiedBalance(groupId: String): Result<Money>
    suspend fun repairGroupData(groupId: String): Result<Unit>
    suspend fun payPlatformFee(groupId: String, amount: Money, feeType: String, txId: String?, fromGroupFund: Boolean): Result<Unit>
    suspend fun getOutstandingPlatformFees(groupId: String): Result<List<PlatformFee>>
}

interface LocalFinancialDataSource {
    suspend fun clearAll()
}
