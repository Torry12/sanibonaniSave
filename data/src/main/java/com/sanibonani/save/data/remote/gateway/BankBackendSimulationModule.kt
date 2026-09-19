package com.sanibonani.save.data.remote.gateway

import com.sanibonani.save.domain.gateway.BankSimulationGateway
import com.sanibonani.save.domain.gateway.SimulatedTransaction
import com.sanibonani.save.domain.model.Money
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Orchestrates bank backend simulations, providing high-level scenarios
 * for testing the platform's reconciliation and financial integrity logic.
 */
@Singleton
class BankBackendSimulationModule @Inject constructor(
    private val bankSimulationGateway: BankSimulationGateway
) {
    /**
     * Simulates a typical month of bank activity for a group.
     * Some members pay on time, some are late, and some don't pay.
     */
    suspend fun simulateMonthOfActivity(
        groupId: String,
        memberNames: List<String>,
        monthlyContribution: Money
    ): Result<List<String>> = runCatching {
        val transactions = memberNames.mapNotNull { name ->
            val behavior = Random.nextDouble()
            when {
                behavior < 0.7 -> { // 70% pay in full
                    SimulatedTransaction(
                        amount = monthlyContribution,
                        reference = "REF-${name.take(3).uppercase()}-${Random.nextInt(1000, 9999)}",
                        description = "Monthly Contribution: $name"
                    )
                }
                behavior < 0.85 -> { // 15% pay partial
                    SimulatedTransaction(
                        amount = monthlyContribution * 0.5,
                        reference = "PART-${name.take(3).uppercase()}-${Random.nextInt(1000, 9999)}",
                        description = "Partial Payment: $name"
                    )
                }
                else -> null // 15% don't pay
            }
        }

        bankSimulationGateway.simulateBatchTransfers(groupId, transactions).getOrThrow()
    }

    /**
     * Simulates a single incoming payment with a specific reference.
     */
    suspend fun simulateSinglePayment(
        groupId: String,
        amount: Money,
        reference: String
    ): Result<String> {
        return bankSimulationGateway.simulateIncomingTransfer(
            groupId = groupId,
            amount = amount,
            reference = reference,
            description = "Simulated Direct Deposit"
        )
    }
}
