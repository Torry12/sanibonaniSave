package com.sanibonani.save.domain.service

/**
 * Service for provider-agnostic KYC/identity verification.
 */
interface KycService {
    suspend fun verifyIdentity(memberId: String, idNumber: String): Result<Boolean>
}

