package com.sanibonani.save.domain.repository.equity
import com.sanibonani.save.domain.model.Contribution
import kotlinx.coroutines.flow.Flow

interface MemberContributionRepository {
    suspend fun recordContribution(contribution: Contribution): Result<Unit>
    fun getMemberContributions(memberId: String, groupId: String): Flow<Result<List<Contribution>>>
    fun getGroupContributions(groupId: String): Flow<Result<List<Contribution>>>
}
