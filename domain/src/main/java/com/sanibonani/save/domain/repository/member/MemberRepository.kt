package com.sanibonani.save.domain.repository.member

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import kotlinx.coroutines.flow.Flow

interface MemberRepository {
    fun getGroupMembers(groupId: String): Flow<Result<List<Member>>>
    suspend fun syncGroupMembers(groupId: String): Result<List<Member>>
    suspend fun getMemberById(id: String): Result<Member>
    fun observeMemberById(id: String): Flow<Result<Member?>>
    suspend fun getMemberByUserId(userId: String, groupId: String): Result<Member>
    suspend fun getMemberships(userId: String): Result<List<Member>>
    fun observeMemberByUserId(userId: String, groupId: String): Flow<Result<Member?>>
    fun observeMemberships(userId: String): Flow<Result<List<Member>>>
    suspend fun registerMember(member: Member, transactionId: String? = null): Result<Member>
    /**
     * Inserts [member], or updates the existing row when `(group_id, user_id)` already
     * exists. The database trigger `tr_ensure_admin_is_member` creates a placeholder
     * member row when a group is inserted, so a plain INSERT for the group admin would
     * violate `UNIQUE(group_id, user_id)` and surface as "This record already exists".
     */
    suspend fun upsertMember(member: Member): Result<Member>
    suspend fun recordContribution(contribution: Contribution): Result<Unit>
    suspend fun updateMemberStatus(memberId: String, status: MemberStatus): Result<Unit>
    suspend fun updateMemberBankingDetails(
        memberId: String,
        bankName: String,
        accountNo: String,
        branchCode: String
    ): Result<Unit>
    suspend fun getAllProbationMembers(): Result<List<Member>>
    suspend fun updateMemberDocumentStatus(
        memberId: String,
        docIndex: Int,
        status: DocumentStatus
    ): Result<Unit>
    suspend fun updateMemberDocuments(
        memberId: String,
        doc1Url: String? = null,
        doc1Type: String? = null,
        doc2Url: String? = null,
        doc2Type: String? = null,
        doc3Url: String? = null,
        doc3Type: String? = null,
        doc4Url: String? = null,
        doc4Type: String? = null,
        doc5Url: String? = null,
        doc5Type: String? = null,
        profilePhotoUrl: String? = null
    ): Result<Unit>
    suspend fun uploadMemberDocument(
        memberId: String,
        documentIndex: Int,
        byteArray: ByteArray,
        fileName: String,
        documentType: String? = null
    ): Result<String>
}
