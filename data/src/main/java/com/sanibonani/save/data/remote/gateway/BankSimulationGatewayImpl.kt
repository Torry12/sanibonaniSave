package com.sanibonani.save.data.remote.gateway

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.domain.gateway.BankSimulationGateway
import com.sanibonani.save.domain.gateway.SimulatedTransaction
import com.sanibonani.save.domain.model.BankTransaction
import com.sanibonani.save.domain.model.BankTransactionStatus
import com.sanibonani.save.domain.model.Money
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankSimulationGatewayImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase
) : BankSimulationGateway {

    override suspend fun simulateIncomingTransfer(
        groupId: String,
        amount: Money,
        reference: String,
        description: String?
    ): Result<String> = runCatching {
        val txId = UUID.randomUUID().toString()
        val transaction = BankTransaction(
            id = txId,
            groupId = groupId,
            bankReference = reference,
            amount = amount,
            transactionDate = LocalDate.now().toString(),
            description = description ?: "Simulated Bank Transfer",
            status = BankTransactionStatus.UNRECONCILED
        )

        // 1. Local Room cache for offline-first & instant UI reactivity
        db.bankTransactionDao().upsert(listOf(transaction.toEntity()))

        // 2. Sync to Supabase
        runCatching {
            supabase.postgrest["bank_transactions"].insert(transaction)
        }

        txId
    }

    override suspend fun simulateBatchTransfers(
        groupId: String,
        transactions: List<SimulatedTransaction>
    ): Result<List<String>> = runCatching {
        val now = LocalDate.now().toString()
        val bankTransactions = transactions.map { sim ->
            val txId = UUID.randomUUID().toString()
            BankTransaction(
                id = txId,
                groupId = groupId,
                bankReference = sim.reference,
                amount = sim.amount,
                transactionDate = now,
                description = sim.description ?: "Simulated Batch Transfer",
                status = BankTransactionStatus.UNRECONCILED
            )
        }

        // 1. Local Room cache for offline-first & instant UI reactivity
        db.bankTransactionDao().upsert(bankTransactions.map { it.toEntity() })

        // 2. Sync to Supabase
        runCatching {
            supabase.postgrest["bank_transactions"].insert(bankTransactions)
        }

        bankTransactions.mapNotNull { it.id }
    }

    override suspend fun getTransactionByReference(reference: String): Result<SimulatedTransaction?> = runCatching {
        supabase.postgrest["bank_transactions"].select {
            filter { eq("bank_reference", reference) }
        }.decodeSingleOrNull<BankTransaction>()?.let {
            SimulatedTransaction(it.amount, it.bankReference, it.description)
        }
    }
}
