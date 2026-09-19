package com.sanibonani.save.domain.usecase.financial

import com.sanibonani.save.domain.model.ConsolidationReport
import com.sanibonani.save.domain.model.LedgerEntry

/**
 * Command Pattern for Ledger operations.
 * Allows for encapsulation and execution of financial bookkeeping actions.
 */
interface LedgerCommand<T> {
    suspend fun execute(): Result<T>
}

data class LogPlatformEventCommand(
    val entry: LedgerEntry,
    private val repository: com.sanibonani.save.domain.repository.financial.LedgerRepository
) : LedgerCommand<Unit> {
    override suspend fun execute(): Result<Unit> = repository.logPlatformEvent(entry)
}

data class ConsolidateLedgerCommand(
    val groupId: String,
    private val repository: com.sanibonani.save.domain.repository.financial.LedgerRepository
) : LedgerCommand<ConsolidationReport> {
    override suspend fun execute(): Result<ConsolidationReport> = repository.consolidateGroupLedger(groupId)
}

data class CreateCheckpointCommand(
    val groupId: String,
    private val repository: com.sanibonani.save.domain.repository.financial.LedgerRepository
) : LedgerCommand<String> {
    override suspend fun execute(): Result<String> = repository.createCheckpoint(groupId)
}
