package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Encapsulates the logic for discovering all groups where the current user has a role (admin or member).
 */
class GetManagedGroupsUseCase @Inject constructor(
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository
) {
    /**
     * @param userId The ID of the user.
     * @param adminOnly If true, only returns groups where the user is the [Group.adminUserId].
     */
    suspend operator fun invoke(userId: String, adminOnly: Boolean = false): Result<List<Group>> {
        return try {
            if (adminOnly) {
                groupRepository.getGroupsByAdmin(userId)
            } else {
                val memberships = memberRepository.getMemberships(userId).getOrThrow()
                val groupIds = memberships.map { it.groupId }.distinct()
                val groups = groupIds.mapNotNull { groupId ->
                    groupRepository.getGroupById(groupId).getOrNull()
                }
                Result.success(groups)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observes groups with real-time updates.
     * @param userId The ID of the user.
     * @param adminOnly If true, only returns groups where the user is the [Group.adminUserId].
     */
    fun observeManagedGroups(userId: String, adminOnly: Boolean = false): Flow<Result<List<Group>>> {
        return if (adminOnly) {
            groupRepository.observeGroupsByAdmin(userId)
        } else {
            flow {
                memberRepository.observeMemberships(userId).collect { membershipsResult ->
                    membershipsResult.onSuccess { memberships ->
                        val groupIds = memberships.map { it.groupId }.distinct()
                        // For now, use a simple approach - emit all groups at once
                        // In a real app, you might want to observe each group individually
                        val groups = mutableListOf<Group>()
                        for (groupId in groupIds) {
                            groupRepository.getGroupById(groupId).getOrNull()?.let { groups.add(it) }
                        }
                        emit(Result.success(groups))
                    }.onFailure { e ->
                        emit(Result.failure(e))
                    }
                }
            }
        }
    }
}
