package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.model.BankTransaction
import com.sanibonani.save.domain.model.BankTransactionStatus
import com.sanibonani.save.domain.repository.BankRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : BaseRepository("BankRepository"), BankRepository {

    override suspend fun importBankTransactions(transactions: List<BankTransaction>): Result<Unit> = runCatching {
        supabase.postgrest["bank_transactions"].insert(transactions)
        Unit
    }

    override fun observeUnreconciledTransactions(groupId: String): Flow<Result<List<BankTransaction>>> = observeRemoteOnly(
        networkFetch = {
            supabase.postgrest["bank_transactions"].select {
                filter {
                    eq("group_id", groupId)
                    eq("status", BankTransactionStatus.UNRECONCILED.name.lowercase())
                }
            }.decodeList<BankTransaction>()
        }
    )

    override suspend fun reconcileManual(bankTxId: String, ledgerId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("reconcile_bank_transaction_v1", buildJsonObject {
            put("p_bank_tx_id", bankTxId)
            put("p_ledger_id", ledgerId)
        })
        Unit
    }

    override suspend fun ignoreTransaction(bankTxId: String, reason: String): Result<Unit> = runCatching {
        supabase.postgrest["bank_transactions"].update(buildJsonObject {
            put("status", BankTransactionStatus.IGNORED.name.lowercase())
            put("metadata", buildJsonObject { put("ignore_reason", reason) })
        }) {
            filter { eq("id", bankTxId) }
        }
        Unit
    }
}
