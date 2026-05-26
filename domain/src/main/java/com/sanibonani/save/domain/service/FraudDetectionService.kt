package com.sanibonani.save.domain.service

/**
 * Service for fraud detection logic.
 */
interface FraudDetectionService {
    suspend fun assessRisk(groupId: String): Result<Double>
}

