package com.sanibonani.save.domain.service


interface RiskService {
    suspend fun calculateRiskScore(memberId: String, groupId: String): Result<Int>
}

