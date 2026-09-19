package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberContributionRepository
import com.sanibonani.save.domain.repository.MemberRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider

class MemberContributionRepositoryImpl @Inject constructor(
    private val supabaseProvider: Provider<SupabaseClient>,
    private val memberRepo: Provider<MemberRepository>,
    private val dbProvider: Provider<SanibonaniDatabase>
) : BaseRepository("MemberContributionRepository"), MemberContributionRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    override suspend fun recordContribution(contribution: Contribution): Result<Unit> = runCatching {
        val rpcParams = buildJsonObject {
            put("p_member_id", contribution.memberId)
            put("p_group_id", contribution.groupId)
            put("p_amount", contribution.amount.amount)
            put("p_due_date", contribution.dueDate)
            put("p_paid_at", contribution.paidAt ?: Instant.now().toString())
            put("p_status", contribution.status.name.lowercase())
            put("p_type", contribution.type)
            put("p_tx_id", contribution.transactionId ?: "app_${Instant.now().toEpochMilli()}")
        }
        supabase.postgrest.rpc("record_contribution_v1", rpcParams)

        // Refresh local cache
        memberRepo.get().getMemberById(contribution.memberId)
        db.contributionDao().upsertContributions(listOf(contribution.toEntity()))
    }

    override fun getMemberContributions(memberId: String, groupId: String): Flow<Result<List<Contribution>>> = observeAndSync(
        dbFlow = db.contributionDao().observeContributions(memberId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["contributions"].select {
                filter {
                    eq("member_id", memberId)
                    eq("group_id", groupId)
                }
            }.decodeList<Contribution>()
        },
        cacheSync = { list -> db.contributionDao().syncMemberContributions(memberId, list) }
    )

    override fun getGroupContributions(groupId: String): Flow<Result<List<Contribution>>> = observeAndSync(
        dbFlow = db.contributionDao().observeGroupContributions(groupId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["contributions"].select {
                filter { eq("group_id", groupId) }
            }.decodeList<Contribution>()
        },
        cacheSync = { list -> db.contributionDao().syncGroupContributions(groupId, list) }
    )
}
