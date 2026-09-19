package com.sanibonani.save.domain.gateway

import com.sanibonani.save.domain.model.Money

/**
 * Interface for simulating bank backend operations for testing and development.
 * This allows the app to trigger virtual bank transactions that the system can then reconcile.
 */
interface BankSimulationGateway {
    /**
     * Simulates a physical bank transfer being recorded in the group's bank account.
     *
     * @param groupId The unique ID of the savings group.
     * @param amount The amount of the transfer.
     * @param reference The bank reference provided by the sender.
     * @param description Optional description for the transaction.
     * @return Result containing the unique transaction ID from the simulated bank backend.
     */
    suspend fun simulateIncomingTransfer(
        groupId: String,
        amount: Money,
        reference: String,
        description: String? = null
    ): Result<String>

    /**
     * Simulates a batch of bank transactions.
     */
    suspend fun simulateBatchTransfers(
        groupId: String,
        transactions: List<SimulatedTransaction>
    ): Result<List<String>>
}

data class SimulatedTransaction(
    val amount: Money,
    val reference: String,
    val description: String? = null
)
