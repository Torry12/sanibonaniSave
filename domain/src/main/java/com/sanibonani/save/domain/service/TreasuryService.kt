package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.Money

interface TreasuryService {
    suspend fun getTreasuryBalance(groupId: String): Result<Money>
    suspend fun schedulePayout(groupId: String, amount: Money): Result<Boolean>
    suspend fun calculateRequiredReserve(groupId: String): Result<Money>
}

