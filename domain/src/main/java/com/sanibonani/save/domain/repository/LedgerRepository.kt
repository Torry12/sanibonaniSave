package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.LedgerEntry
import kotlinx.coroutines.flow.Flow

interface LedgerRepository {
    fun observeGroupLedger(groupId: String): Flow<Result<List<LedgerEntry>>>
    suspend fun getGroupLedger(groupId: String): Result<List<LedgerEntry>>
    suspend fun logPlatformEvent(entry: LedgerEntry): Result<Unit>
}
