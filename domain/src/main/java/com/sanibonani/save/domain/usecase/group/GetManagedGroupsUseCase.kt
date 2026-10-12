package com.sanibonani.save.domain.usecase.group
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
     * @param isPlatformAdmin If true, returns ALL groups on the platform.
     */
    suspend operator fun invoke(
        userId: String,
        adminOnly: Boolean = false,
        isPlatformAdmin: Boolean = false
    ): Result<List<Group>> {
        return try {
            if (isPlatformAdmin) {
                // Platform admins oversee every group, so the platform-wide listing is
                // the authoritative source. Repository applies the appropriate scope.
                groupRepository.getPublicGroups().first()
            } else if (adminOnly) {
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
     * @param isPlatformAdmin If true, returns ALL groups on the platform.
     */
    fun observeManagedGroups(
        userId: String,
        adminOnly: Boolean = false,
        isPlatformAdmin: Boolean = false
    ): Flow<Result<List<Group>>> {
        return if (isPlatformAdmin) {
            // For platform admin, they observe all public groups as managed groups
            groupRepository.getPublicGroups()
        } else if (adminOnly) {
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
