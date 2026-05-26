package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.service.ComplianceService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComplianceServiceImpl @Inject constructor() : ComplianceService {
    override suspend fun checkCompliance(memberId: String): Result<Boolean> {
        // TODO: Implement compliance check logic
        return Result.success(true)
    }
}
