package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.model.ReconciliationRecord
import com.sanibonani.save.domain.repository.ReconciliationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryReconciliationRepository @Inject constructor() : ReconciliationRepository {
    private val records = MutableStateFlow<List<ReconciliationRecord>>(emptyList())

    override fun observeReconciliations(groupId: String): Flow<List<ReconciliationRecord>> =
        records.asStateFlow().map { it.filter { rec -> rec.groupId == groupId } }

    override suspend fun recordReconciliation(record: ReconciliationRecord): Result<ReconciliationRecord> {
        records.value = records.value + record
        return Result.success(record)
    }
}

