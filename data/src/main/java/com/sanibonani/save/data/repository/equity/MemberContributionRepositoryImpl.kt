package com.sanibonani.save.data.repository.equity
import com.sanibonani.save.data.datasource.RemoteContributionDataSource
import com.sanibonani.save.data.repository.BaseRepository

import com.sanibonani.save.data.local.*
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.equity.MemberContributionRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Provider

class MemberContributionRepositoryImpl @Inject constructor(
    private val remoteSource: RemoteContributionDataSource,
    private val supabaseProvider: Provider<SupabaseClient>,
    private val memberRepo: Provider<MemberRepository>,
    private val dbProvider: Provider<SanibonaniDatabase>
) : BaseRepository("MemberContributionRepository"), MemberContributionRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    override suspend fun recordContribution(contribution: Contribution): Result<Unit> = authoritativeCall("record_contribution_v2") {
        require(contribution.memberId.isNotBlank()) { "Member id is required" }
        require(contribution.groupId.isNotBlank()) { "Group id is required" }
        require(contribution.amount.isPositive) { "Contribution amount must be positive" }
        require(contribution.type.isNotBlank()) { "Contribution type is required" }
        val transactionId = contribution.transactionId?.takeIf(String::isNotBlank)
            ?: contribution.id?.let { "contribution_$it" }
            ?: throw IllegalArgumentException("A stable contribution transaction id is required")
        // The record_contribution_v2 payload is assembled once, in the remote data source.
        // Carrying the resolved id on the model keeps `p_tx_id` byte-identical, which is the
        // key the server deduplicates on.
        remoteSource.recordContribution(contribution.copy(transactionId = transactionId)).getOrThrow()

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

