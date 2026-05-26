package com.sanibonani.save.data.service

import com.sanibonani.save.domain.service.TreasuryService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryTreasuryService @Inject constructor() : TreasuryService {
    override suspend fun getTreasuryBalance(groupId: String): Result<Long> = Result.success(0L)
    override suspend fun schedulePayout(groupId: String, amount: Long): Result<Boolean> = Result.success(true)
}

