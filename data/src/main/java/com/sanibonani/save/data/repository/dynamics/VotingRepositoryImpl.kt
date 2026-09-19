package com.sanibonani.save.data.repository.dynamics

import com.sanibonani.save.data.local.*
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.data.repository.mappers.*
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.dynamics.VotingRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class VotingRepositoryImpl @Inject constructor(
    private val connectionManager: com.sanibonani.save.data.remote.SupabaseConnectionManager,
    private val supabaseProvider: javax.inject.Provider<SupabaseClient>
) : BaseRepository("VotingRepository"), VotingRepository {
    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase? get() = connectionManager.getDatabase()

    override fun observePolls(groupId: String, memberId: String?): Flow<Result<List<GroupPollWithOptions>>> {
        val dao = db?.groupVotingDao() ?: return flow { emit(Result.failure(Exception("Database unavailable"))) }
        return observeAndSync(
            dbFlow = dao.observePollsWithDetails(groupId),
            mapper = { entity ->
                GroupPollWithOptions(
                    poll = entity.poll.toModel(),
                    options = entity.options.map { it.toModel() },
                    myVoteOptionId = entity.votes.find { it.memberId == memberId }?.optionId
                )
            },
            toEntity = { GroupPollWithDetailsEntity(it.poll.toEntity(), it.options.map { o -> o.toEntity() }, emptyList()) },
            networkFetch = {
                val polls = supabase.postgrest["group_polls"].select { filter { eq("group_id", groupId) } }.decodeList<GroupPoll>()
                dao.syncPolls(groupId, polls.map { it.toEntity() })
                polls.map { GroupPollWithOptions(poll = it) }
            },
            cacheSync = { }
        )
    }

    override suspend fun createPoll(groupId: String, createdByMemberId: String?, title: String, description: String?, options: List<String>, allowMultipleChoice: Boolean, endsAt: String?): Result<String> = runCatching {
        val poll = GroupPoll(groupId, createdByMemberId, title, description, PollStatus.OPEN, allowMultipleChoice, endsAt = endsAt)
        val created = supabase.postgrest["group_polls"].insert(poll) { select() }.decodeSingle<GroupPoll>()
        val id = created.id!!
        val optionRows = options.mapIndexed { i, l -> GroupPollOption(pollId = id, label = l, position = i + 1) }
        supabase.postgrest["group_poll_options"].insert(optionRows)
        db?.groupVotingDao()?.upsertPolls(listOf(created.toEntity()))
        id
    }

    override suspend fun castVote(groupId: String, pollId: String, optionId: String, memberId: String): Result<Unit> = runCatching {
        val vote = GroupPollVote(groupId, pollId, optionId, memberId)
        supabase.postgrest["group_poll_votes"].upsert(vote) { onConflict = "poll_id,member_id" }
        Unit
    }

    override suspend fun closePoll(pollId: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("close_poll_v1", buildJsonObject { put("p_poll_id", pollId) })
        Unit
    }
}
