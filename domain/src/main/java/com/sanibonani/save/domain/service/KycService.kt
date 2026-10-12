package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.Member
import java.io.File

/**
 * Service for Identity and KYC Verification.
 */
interface KycService {
    /**
     * Evaluates member's identity parameters (ID number checksums, etc.).
     */
    fun evaluateMemberIdentity(member: Member): Result<Unit>

    /**
     * Orchestrates the AI-powered KYC workflow.
     */
    suspend fun processKycAi(
        memberId: String,
        idFront: File,
        selfie: File
    ): Result<Unit>
}
