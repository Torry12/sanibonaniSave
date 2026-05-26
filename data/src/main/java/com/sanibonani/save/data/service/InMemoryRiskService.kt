package com.sanibonani.save.data.service

import com.sanibonani.save.domain.service.RiskService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryRiskService @Inject constructor() : RiskService {
    override suspend fun calculateRiskScore(memberId: String, groupId: String): Result<Int> = Result.success(0)
}

