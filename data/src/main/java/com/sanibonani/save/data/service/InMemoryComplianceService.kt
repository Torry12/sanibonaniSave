package com.sanibonani.save.data.service

import com.sanibonani.save.domain.service.ComplianceService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryComplianceService @Inject constructor() : ComplianceService {
    override suspend fun checkCompliance(memberId: String): Result<Boolean> = Result.success(true)
}

