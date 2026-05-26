package com.sanibonani.save.data.service

import com.sanibonani.save.domain.service.FraudDetectionService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryFraudDetectionService @Inject constructor() : FraudDetectionService {
    override suspend fun assessRisk(groupId: String): Result<Double> = Result.success(0.0)
}

