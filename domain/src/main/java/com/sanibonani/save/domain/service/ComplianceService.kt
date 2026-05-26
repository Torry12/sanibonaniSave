package com.sanibonani.save.domain.service

/**
 * Service for compliance checks (e.g., regulatory, KYC).
 */
interface ComplianceService {
    suspend fun checkCompliance(memberId: String): Result<Boolean>
}

