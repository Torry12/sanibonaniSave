package com.sanibonani.save.data.repository.group

import com.sanibonani.save.data.local.GroupPollWithDetailsEntity
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.model.GroupPoll
import com.sanibonani.save.domain.model.GroupPollOption
import com.sanibonani.save.domain.model.GroupPollVote
import com.sanibonani.save.domain.model.GroupPollWithOptions
import com.sanibonani.save.domain.model.PollStatus
import com.sanibonani.save.domain.repository.group.VotingRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.sanibonani.save.domain.utils.requirePersistedId

class VotingRepositoryImpl @Inject constructor(
    private val connectionManager: com.sanibonani.save.data.remote.SupabaseConnectionManager,
    private val supabaseProvider: javax.inject.Provider<SupabaseClient>
) : BaseRepository("VotingRepository"), VotingRepository {
    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = connectionManager.getDatabase()

    override fun observePolls(groupId: String, memberId: String?): Flow<Result<List<GroupPollWithOptions>>> {
        val dao = db.groupVotingDao()
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
                val pollIds = polls.mapNotNull { it.id }

                if (pollIds.isNotEmpty()) {
                    val options = supabase.postgrest["group_poll_options"].select {
                        filter { or { pollIds.forEach { eq("poll_id", it) } } }
                    }.decodeList<GroupPollOption>()

                    val votes = supabase.postgrest["group_poll_votes"].select {
                        filter { or { pollIds.forEach { eq("poll_id", it) } } }
                    }.decodeList<GroupPollVote>()

                    dao.upsertPolls(polls.map { it.toEntity() })
                    dao.upsertOptions(options.map { it.toEntity() })
                    dao.upsertVotes(votes.map { it.toEntity() })
                } else {
                    dao.upsertPolls(emptyList())
                }

                polls.map { poll ->
                    GroupPollWithOptions(poll = poll)
                }
            },
            cacheSync = { }
        )
    }

    override suspend fun createPoll(
        groupId: String,
        createdByMemberId: String?,
        title: String,
        description: String?,
        options: List<String>,
        allowMultipleChoice: Boolean,
        endsAt: String?
    ): Result<String> = authoritativeCall("createPoll") {
        val poll = GroupPoll(
            groupId = groupId,
            createdByMemberId = createdByMemberId,
            title = title,
            description = description,
            status = PollStatus.OPEN,
            allowMultipleChoice = allowMultipleChoice,
            endsAt = endsAt
        )
        val created = supabase.postgrest["group_polls"].insert(poll) { select() }.decodeSingle<GroupPoll>()
        val id = created.id.requirePersistedId("poll")
        val optionRows = options.mapIndexed { index, label ->
            GroupPollOption(pollId = id, label = label, position = index + 1)
        }
        supabase.postgrest["group_poll_options"].insert(optionRows)

        db.groupVotingDao().upsertPolls(listOf(created.toEntity()))
        db.groupVotingDao().upsertOptions(optionRows.map { it.toEntity() })
        id
    }

    override suspend fun castVote(
        groupId: String,
        pollId: String,
        optionId: String,
        memberId: String
    ): Result<Unit> = authoritativeCall("castVote") {
        val vote = GroupPollVote(groupId = groupId, pollId = pollId, optionId = optionId, memberId = memberId)
        supabase.postgrest["group_poll_votes"].upsert(vote) { onConflict = "poll_id,member_id" }
        db.groupVotingDao().upsertVotes(listOf(vote.toEntity()))
    }

    override suspend fun closePoll(pollId: String): Result<Unit> = authoritativeCall("closePoll") {
        supabase.postgrest.rpc(CLOSE_POLL_RPC, buildClosePollRpcParams(pollId))
    }

    companion object {
        const val CLOSE_POLL_RPC = "close_poll_v1"

        /**
         * PostgREST casts `p_poll_id` to uuid before the RPC is resolved, so a blank id would
         * arrive as a malformed request rather than the answer the caller expects. Refusing it
         * here keeps the screen's failure message about the poll instead of about the wire.
         */
        fun buildClosePollRpcParams(pollId: String): JsonObject {
            require(pollId.isNotBlank()) { "This poll cannot be closed: it has no identifier." }
            return buildJsonObject { put("p_poll_id", pollId) }
        }
    }
}
