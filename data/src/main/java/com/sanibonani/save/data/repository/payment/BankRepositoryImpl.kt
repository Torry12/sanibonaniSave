package com.sanibonani.save.data.repository.payment
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.model.BankConnection
import com.sanibonani.save.domain.model.BankTransaction
import com.sanibonani.save.domain.model.BankTransactionStatus
import com.sanibonani.save.domain.repository.payment.BankRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class BankRepositoryImpl @Inject constructor(
    private val supabaseProvider: javax.inject.Provider<SupabaseClient>,
    private val dbProvider: javax.inject.Provider<SanibonaniDatabase>,
    private val payloadFactory: com.sanibonani.save.data.repository.mappers.DataPayloadFactory,
    private val parserFactory: com.sanibonani.save.domain.service.BankParserFactory
) : BaseRepository("BankRepository"), BankRepository {
    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    override suspend fun importBankTransactions(transactions: List<BankTransaction>): Result<Unit> =
        authoritativeCall("importBankTransactions") {
            if (transactions.isEmpty()) return@authoritativeCall
            val payloads = transactions.map { payloadFactory.createBankTransactionPayload(it) }
            supabase.postgrest["bank_transactions"].insert(payloads)
            db.bankTransactionDao().upsert(transactions.map { it.toEntity() })
        }

    override suspend fun importRawStatement(rawData: String, format: String, groupId: String): Result<Unit> = safeRunCatching {
        if (rawData.isBlank()) return Result.success(Unit)
        val parser = parserFactory.getParser(format)
        val transactions = parser.parse(rawData, groupId)
        if (transactions.isNotEmpty()) {
            importBankTransactions(transactions).getOrThrow()
        }
    }

    override fun observeUnreconciledTransactions(groupId: String): Flow<Result<List<BankTransaction>>> = observeAndSync(
        dbFlow = db.bankTransactionDao().observeByGroup(groupId).map { list -> list.filter { it.status == BankTransactionStatus.UNRECONCILED } },
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["bank_transactions"].select(columns = Columns.raw("*")) {
                filter {
                    eq("group_id", groupId)
                    eq("status", BankWireStatus.of(BankTransactionStatus.UNRECONCILED))
                }
            }.decodeList<BankTransaction>()
        },
        cacheSync = { list -> db.bankTransactionDao().upsert(list) }
    )

    override suspend fun reconcileManual(bankTxId: String, ledgerId: String): Result<Unit> =
        authoritativeCall("reconcileManual") {
            supabase.postgrest.rpc("reconcile_bank_transaction_v1", buildJsonObject {
                put("p_bank_tx_id", bankTxId)
                put("p_ledger_id", ledgerId)
            })
            // The local mirror only ever follows a server that accepted the reconciliation.
            db.bankTransactionDao().updateReconciliationStatus(
                id = bankTxId,
                status = BankTransactionStatus.RECONCILED,
                ledgerId = ledgerId
            )
        }

    override suspend fun ignoreTransaction(bankTxId: String, reason: String): Result<Unit> =
        authoritativeCall("ignoreTransaction") {
            val payload = buildJsonObject {
                put("status", BankWireStatus.of(BankTransactionStatus.IGNORED))
                put("metadata", buildJsonObject { put("ignore_reason", reason) })
            }
            supabase.postgrest["bank_transactions"].update(payload) {
                filter { eq("id", bankTxId) }
            }
            db.bankTransactionDao().updateReconciliationStatus(
                id = bankTxId,
                status = BankTransactionStatus.IGNORED,
                ledgerId = null
            )
        }

    override fun observeBankConnection(groupId: String): Flow<Result<BankConnection?>> = observeAndSyncItem(
        dbFlow = db.bankTransactionDao().observeConnection(groupId),
        mapper = { it.toModel() },
        toEntity = { it?.toEntity() },
        networkFetch = {
            supabase.postgrest["bank_connections"].select(columns = Columns.raw("*")) {
                filter { eq("group_id", groupId) }
            }.decodeSingleOrNull<BankConnection>()
        },
        cacheSync = { db.bankTransactionDao().upsertConnection(it) }
    )

    override suspend fun saveBankConnection(connection: BankConnection): Result<Unit> =
        authoritativeCall("saveBankConnection") {
            supabase.postgrest["bank_connections"].upsert(connection) {
                onConflict = "group_id"
            }
            db.bankTransactionDao().upsertConnection(connection.toEntity())
        }

    override suspend fun deleteBankConnection(groupId: String): Result<Unit> =
        authoritativeCall("deleteBankConnection") {
            supabase.postgrest["bank_connections"].delete {
                filter { eq("group_id", groupId) }
            }
            db.bankTransactionDao().deleteConnectionByGroup(groupId)
        }
}
