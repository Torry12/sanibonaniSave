package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.DocumentStatus
import kotlinx.coroutines.flow.Flow

interface RemoteMemberDataSource {
    suspend fun getMembers(groupId: String): Result<List<Member>>
    suspend fun getProbationMembers(): Result<List<Member>>
    suspend fun getMemberById(id: String): Result<Member>
    suspend fun getMemberByUserId(userId: String, groupId: String): Result<Member>
    suspend fun getMemberships(userId: String): Result<List<Member>>
    suspend fun registerMember(member: Member, transactionId: String?): Result<Member>
    /**
     * Inserts [member], or updates the existing row when `(group_id, user_id)` already
     * exists. Used for admin onboarding because the `tr_ensure_admin_is_member` trigger
     * already creates a placeholder row on group insert, which would make a plain
     * INSERT violate `UNIQUE(group_id, user_id)`.
     */
    suspend fun upsertMember(member: Member): Result<Member>
    suspend fun updateMemberStatus(memberId: String, status: MemberStatus): Result<Unit>
    suspend fun updateMemberBankingDetails(memberId: String, bankName: String, accountNo: String, branchCode: String): Result<Unit>
    suspend fun updateMemberDocumentStatus(memberId: String, docIndex: Int, status: DocumentStatus): Result<Unit>
    suspend fun updateMemberFcmToken(memberId: String, token: String): Result<Unit>
    suspend fun updateMemberDocuments(
        memberId: String,
        doc1Url: String? = null, doc1Type: String? = null,
        doc2Url: String? = null, doc2Type: String? = null,
        doc3Url: String? = null, doc3Type: String? = null,
        doc4Url: String? = null, doc4Type: String? = null,
        doc5Url: String? = null, doc5Type: String? = null,
        profilePhotoUrl: String? = null
    ): Result<Unit>
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
