package com.sanibonani.save.domain.usecase.dynamics

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberRepository
import kotlinx.coroutines.flow.*
import javax.inject.Inject

class GetManagedGroupsUseCase @Inject constructor(
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository
) {
    fun observeManagedGroups(userId: String, adminOnly: Boolean = false, isPlatformAdmin: Boolean = false): Flow<Result<List<Group>>> {
        return if (isPlatformAdmin) {
            flow {
                emit(groupRepository.getAllGroups(fromNetwork = false))
                emit(groupRepository.getAllGroups(fromNetwork = true))
            }
        } else if (adminOnly) {
            groupRepository.observeGroupsByAdmin(userId)
        } else {
            memberRepository.observeMemberships(userId).map { res ->
                res.map { list -> list.mapNotNull { groupRepository.getGroupById(it.groupId).getOrNull() } }
            }
        }
    }
}
