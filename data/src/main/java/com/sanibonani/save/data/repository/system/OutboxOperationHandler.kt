package com.sanibonani.save.data.repository.system

import com.sanibonani.save.domain.model.PendingOperation

/**
 * Strategy interface for handling the replay of failed operations from the offline outbox.
 */
interface OutboxOperationHandler {
    suspend fun handle(operation: PendingOperation): Result<Unit>
}
