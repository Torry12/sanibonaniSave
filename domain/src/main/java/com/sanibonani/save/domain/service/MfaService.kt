package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.MfaFactor
import com.sanibonani.save.domain.model.TotpEnrollment

interface MfaService {
    val isMfaVerified: Boolean
    suspend fun enrollTotp(friendlyName: String): Result<TotpEnrollment>
    suspend fun verifyMfa(factorId: String, code: String, saveChallenge: Boolean): Result<Unit>
    suspend fun unenrollMfa(factorId: String): Result<Unit>
    suspend fun getMfaFactors(): Result<List<MfaFactor>>
}
