package com.sanibonani.save.domain.service

import kotlinx.coroutines.flow.Flow

interface TreasuryService {
    suspend fun getTreasuryBalance(groupId: String): Result<Long>
    suspend fun schedulePayout(groupId: String, amount: Long): Result<Boolean>
}

