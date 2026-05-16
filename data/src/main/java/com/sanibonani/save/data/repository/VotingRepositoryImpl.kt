package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.model.GroupPoll
import com.sanibonani.save.domain.model.GroupPollOption
import com.sanibonani.save.domain.model.GroupPollVote
import com.sanibonani.save.domain.model.GroupPollWithOptions
import com.sanibonani.save.domain.model.PollStatus
import com.sanibonani.save.domain.repository.VotingRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class VotingRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : BaseRepository("VotingRepository"), VotingRepository {

    override fun observePolls(groupId: String, memberId: String?): Flow<Result<List<GroupPollWithOptions>>> = flow {
        emit(runCatching {
            val polls = supabase.postgrest["group_polls"].select {
                filter { eq("group_id", groupId) }
            }.decodeList<GroupPoll>()

            polls.map { poll ->
                val pollId = poll.id.orEmpty()
                val options = supabase.postgrest["group_poll_options"].select {
                    filter { eq("poll_id", pollId) }
                }.decodeList<GroupPollOption>()

                val voteOptionId = if (!memberId.isNullOrBlank()) {
                    supabase.postgrest["group_poll_votes"].select(columns = Columns.list("option_id")) {
                        filter {
                            eq("poll_id", pollId)
                            eq("member_id", memberId)
                        }
                        limit(1)
                    }.decodeSingleOrNull<GroupPollVote>()?.optionId
                } else {
                    null
                }

                GroupPollWithOptions(
                    poll = poll,
                    options = options,
                    myVoteOptionId = voteOptionId
                )
            }
        })
    }

    override suspend fun createPoll(
        groupId: String,
        createdByMemberId: String?,
        title: String,
        description: String?,
        options: List<String>,
        allowMultipleChoice: Boolean,
        endsAt: String?
    ): Result<String> = runCatching {
        require(title.trim().length >= 3) { "Title must be at least 3 characters." }
        require(options.map { it.trim() }.filter { it.isNotEmpty() }.size >= 2) { "At least 2 options are required." }

        val payload = buildJsonObject {
            put("group_id", groupId)
            createdByMemberId?.let { put("created_by_member_id", it) }
            put("title", title.trim())
            description?.let { put("description", it.trim()) }
            put("status", PollStatus.OPEN.name.lowercase())
            put("allow_multiple_choice", allowMultipleChoice)
            endsAt?.let { put("ends_at", it) }
        }

        val createdPoll = supabase.postgrest["group_polls"].insert(payload) { select() }
            .decodeSingle<GroupPoll>()

        val pollId = createdPoll.id ?: error("Poll ID missing after create.")

        val optionRows = options
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .mapIndexed { idx, label ->
                buildJsonObject {
                    put("poll_id", pollId)
                    put("label", label)
                    put("position", idx + 1)
                }
            }

        supabase.postgrest["group_poll_options"].insert(optionRows)
        pollId
    }

    override suspend fun castVote(
        groupId: String,
        pollId: String,
        optionId: String,
        memberId: String
    ): Result<Unit> = runCatching {
        val votePayload = buildJsonObject {
            put("group_id", groupId)
            put("poll_id", pollId)
            put("option_id", optionId)
            put("member_id", memberId)
        }

        supabase.postgrest["group_poll_votes"].upsert(votePayload) {
            onConflict = "poll_id,member_id"
            select()
        }.decodeSingle<GroupPollVote>()
        Unit
    }

    override suspend fun closePoll(pollId: String): Result<Unit> = runCatching {
        supabase.postgrest["group_polls"].update(
            buildJsonObject {
                put("status", PollStatus.CLOSED.name.lowercase())
            }
        ) {
            filter { eq("id", pollId) }
        }
        Unit
    }
}

