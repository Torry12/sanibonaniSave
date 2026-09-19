package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.DocumentStatus
import kotlinx.coroutines.flow.Flow

interface RemoteMemberDataSource {
    suspend fun getMembers(groupId: String): Result<List<Member>>
    suspend fun getMemberById(id: String): Result<Member>
    suspend fun getMemberByUserId(userId: String, groupId: String): Result<Member>
    suspend fun getMemberships(userId: String): Result<List<Member>>
    suspend fun registerMember(member: Member, transactionId: String?): Result<Member>
    suspend fun updateMemberStatus(memberId: String, status: MemberStatus): Result<Unit>
    suspend fun updateMemberBankingDetails(memberId: String, bankName: String, accountNo: String, branchCode: String): Result<Unit>
    suspend fun updateMemberDocumentStatus(memberId: String, docIndex: Int, status: DocumentStatus): Result<Unit>
    suspend fun updateMemberFcmToken(memberId: String, token: String): Result<Unit>
}

interface LocalMemberDataSource {
    fun observeMembers(groupId: String): Flow<List<Member>>
    fun observeActiveMembers(groupId: String): Flow<List<Member>>
    fun observeMemberById(id: String): Flow<Member?>
    fun observeMemberByUserId(userId: String, groupId: String): Flow<Member?>
    fun observeAllMemberships(userId: String): Flow<List<Member>>
    suspend fun getMemberById(id: String): Member?
    suspend fun getMemberByUserId(userId: String, groupId: String): Member?
    suspend fun saveMembers(members: List<Member>)
    suspend fun saveMember(member: Member)
    suspend fun syncMemberships(userId: String, members: List<Member>)
    suspend fun syncActiveMembers(groupId: String, members: List<Member>)
    suspend fun deleteMember(id: String)
    suspend fun clearAll()
}
