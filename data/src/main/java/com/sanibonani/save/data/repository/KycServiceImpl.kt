package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.service.KycService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KycServiceImpl @Inject constructor() : KycService {
    override suspend fun verifyIdentity(memberId: String, idNumber: String): Result<Boolean> {
        // TODO: Integrate with real KYC provider (e.g. SmileID, Stitch, etc.)
        return Result.success(true)
    }
}
