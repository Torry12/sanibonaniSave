package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.LedgerEntryEntity
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.repository.LedgerRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import com.sanibonani.save.data.repository.*

class LedgerRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase
) : BaseRepository("LedgerRepository"), LedgerRepository {

    override fun observeGroupLedger(groupId: String): Flow<Result<List<LedgerEntry>>> = observeAndSync(
        dbFlow = db.ledgerDao().observeLedger(groupId),
        mapper = { entity: LedgerEntryEntity -> entity.toModel() },
        toEntity = { model: LedgerEntry -> model.toLedgerEntity() },
        networkFetch = {
            supabase.postgrest["group_ledger"].select(columns = Columns.raw("*")) {
                filter { eq("group_id", groupId) }
            }.decodeList<LedgerEntry>()
        },
        cacheSync = { list: List<LedgerEntryEntity> -> db.ledgerDao().syncLedger(groupId, list) }
    )

    override suspend fun getGroupLedger(groupId: String): Result<List<LedgerEntry>> = runCatching {
        supabase.postgrest["group_ledger"].select(columns = Columns.raw("*")) {
            filter { eq("group_id", groupId) }
        }.decodeList<LedgerEntry>()
    }

    override suspend fun logPlatformEvent(entry: LedgerEntry): Result<Unit> = runCatching {
        AppLogger.d(tag, "🪵 Logging platform event: ${entry.description}")
        supabase.postgrest["platform_ledger"].insert(entry)
        Unit
    }
}
