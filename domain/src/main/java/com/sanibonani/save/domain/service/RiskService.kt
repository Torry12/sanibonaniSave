package com.sanibonani.save.domain.service

import kotlinx.coroutines.flow.Flow

interface RiskService {
    suspend fun calculateRiskScore(memberId: String, groupId: String): Result<Int>
}

