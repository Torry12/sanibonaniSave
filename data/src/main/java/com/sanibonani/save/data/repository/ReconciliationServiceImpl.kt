package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.service.ReconciliationService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReconciliationServiceImpl @Inject constructor() : ReconciliationService {
    override suspend fun reconcile(groupId: String): Result<Unit> {
        // TODO: Implement reconciliation logic
        return Result.success(Unit)
    }
}
