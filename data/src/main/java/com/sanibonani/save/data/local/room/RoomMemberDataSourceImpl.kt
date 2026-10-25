package com.sanibonani.save.data.local.room

import com.sanibonani.save.data.datasource.LocalMemberDataSource
import com.sanibonani.save.data.local.MemberDao
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.model.Member
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMemberDataSourceImpl @Inject constructor(
    private val memberDao: MemberDao
) : LocalMemberDataSource {

    override fun observeMembers(groupId: String): Flow<List<Member>> =
        memberDao.observeMembers(groupId).map { list -> list.map { it.toModel() } }

    override fun observeActiveMembers(groupId: String): Flow<List<Member>> =
        memberDao.observeActiveMembers(groupId).map { list -> list.map { it.toModel() } }

    override fun observeMemberById(id: String): Flow<Member?> =
        memberDao.observeMemberById(id).map { it?.toModel() }

    override fun observeMemberByUserId(userId: String, groupId: String): Flow<Member?> =
        memberDao.observeMemberByUserId(userId, groupId).map { it?.toModel() }

    override fun observeAllMemberships(userId: String): Flow<List<Member>> =
        memberDao.observeAllMemberships(userId).map { list -> list.map { it.toModel() } }

    override suspend fun getMemberById(id: String): Member? =
        memberDao.getMemberById(id)?.toModel()

    override suspend fun getMemberByUserId(userId: String, groupId: String): Member? =
        memberDao.getMemberByUserId(userId, groupId)?.toModel()

    override suspend fun saveMembers(members: List<Member>) {
        memberDao.upsertMembers(members.map { it.toEntity() })
    }

    override suspend fun saveMember(member: Member) {
        memberDao.upsertMember(member.toEntity())
    }

    override suspend fun syncMemberships(userId: String, members: List<Member>) {
        memberDao.syncMembershipsForUser(userId, members.map { it.toEntity() })
    }

    override suspend fun syncActiveMembers(groupId: String, members: List<Member>) {
        memberDao.syncActiveMembers(groupId, members.map { it.toEntity() })
    }

    override suspend fun deleteMember(id: String) {
        memberDao.deleteMember(id)
    }

    override suspend fun clearAll() {
        memberDao.clearAll()
    }
}
