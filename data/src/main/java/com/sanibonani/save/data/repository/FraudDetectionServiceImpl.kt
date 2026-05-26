package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.service.FraudDetectionService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FraudDetectionServiceImpl @Inject constructor() : FraudDetectionService {
    override suspend fun assessRisk(groupId: String): Result<Double> {
        // TODO: Implement risk assessment logic
        return Result.success(0.0)
    }
}
