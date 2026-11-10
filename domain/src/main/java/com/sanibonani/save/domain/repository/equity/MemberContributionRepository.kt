package com.sanibonani.save.domain.repository.equity
import com.sanibonani.save.domain.model.Contribution
import kotlinx.coroutines.flow.Flow

interface MemberContributionRepository {
    suspend fun recordContribution(contribution: Contribution): Result<Unit>
    fun getMemberContributions(memberId: String, groupId: String): Flow<Result<List<Contribution>>>
    fun getGroupContributions(groupId: String): Flow<Result<List<Contribution>>>

    /**
     * One member's contributions read from the server, with what it answered written to the cache.
     *
     * [getMemberContributions] answers from Room first and sends the refresh verdict behind those rows,
     * so its first emission only proves the cache spoke - and on a device that has never synced this
     * member the cache is empty. Anything that overwrites a stored row with what it reads here, like
     * the behaviour track, needs a read that cannot answer without the server; a failure means the book
     * is unread, not that the member has contributed nothing.
     */
    suspend fun syncMemberContributions(memberId: String, groupId: String): Result<List<Contribution>>
}
