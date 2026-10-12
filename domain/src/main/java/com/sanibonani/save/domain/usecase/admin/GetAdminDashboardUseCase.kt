package com.sanibonani.save.domain.usecase.admin
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
data class AdminDashboardData(val group: Group, val members: List<Member>)
class GetAdminDashboardUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository
) {
    fun observeDashboard(groupId: String): Flow<Result<AdminDashboardData>> = combine(groupRepository.observeGroup(groupId), memberRepository.getGroupMembers(groupId)) { g, m -> runCatching { AdminDashboardData(g.getOrThrow()!!, m.getOrThrow()) } }
}
