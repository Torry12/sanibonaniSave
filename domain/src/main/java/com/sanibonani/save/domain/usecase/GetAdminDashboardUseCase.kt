package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class AdminDashboardData(
    val group: Group,
    val members: List<Member>
)

/**
 * Consolidates administration-related data fetching for a specific group.
 * This Use Case reduces the number of repositories a ViewModel needs to observe directly.
 */
class GetAdminDashboardUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository
) {
    fun observeDashboard(groupId: String): Flow<Result<AdminDashboardData>> {
        return combine(
            groupRepository.observeGroup(groupId),
            memberRepository.getGroupMembers(groupId)
        ) { groupResult, membersResult ->
            try {
                val group = groupResult.getOrThrow() ?: throw Exception("Group not found")
                val members = membersResult.getOrThrow()
                Result.success(AdminDashboardData(group, members))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
