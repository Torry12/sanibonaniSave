package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemoteMemberDataSource
import com.sanibonani.save.data.remote.PostgrestColumns
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.DocumentStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

import com.sanibonani.save.domain.repository.system.DataConnectionProtocol

@Singleton
class SupabaseMemberDataSourceImpl @Inject constructor(
    protocol: DataConnectionProtocol,
    private val payloadFactory: com.sanibonani.save.data.repository.mappers.DataPayloadFactory
) : BaseSupabaseDataSource(protocol), RemoteMemberDataSource {

    override suspend fun getMembers(groupId: String): Result<List<Member>> = runCatching {
        runCatching {
            client.postgrest["members"].select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE)) {
                filter { eq("group_id", groupId) }
            }.decodeList<Member>()
        }.getOrElse {
            client.postgrest["members"].select {
                filter { eq("group_id", groupId) }
            }.decodeList<Member>()
        }
    }

    override suspend fun getProbationMembers(): Result<List<Member>> = runCatching {
        runCatching {
            client.postgrest["members"].select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE)) {
                filter { eq("status", MemberStatus.PROBATION.name.lowercase()) }
            }.decodeList<Member>()
        }.getOrElse {
            client.postgrest["members"].select {
                filter { eq("status", MemberStatus.PROBATION.name.lowercase()) }
            }.decodeList<Member>()
        }
    }

    override suspend fun getMemberById(id: String): Result<Member> = runCatching {
        runCatching {
            client.postgrest["members"].select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE)) {
                filter { eq("id", id) }
            }.decodeSingle<Member>()
        }.getOrElse {
            client.postgrest["members"].select {
                filter { eq("id", id) }
            }.decodeSingle<Member>()
        }
    }

    override suspend fun getMemberByUserId(userId: String, groupId: String): Result<Member> = runCatching {
        runCatching {
            client.postgrest["members"].select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE)) {
                filter {
                    eq("user_id", userId)
                    eq("group_id", groupId)
                }
            }.decodeSingle<Member>()
        }.getOrElse {
            client.postgrest["members"].select {
                filter {
                    eq("user_id", userId)
                    eq("group_id", groupId)
                }
            }.decodeSingle<Member>()
        }
    }

    override suspend fun getMemberships(userId: String): Result<List<Member>> = runCatching {
        runCatching {
            client.postgrest["members"].select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE)) {
                filter { eq("user_id", userId) }
            }.decodeList<Member>()
        }.getOrElse {
            client.postgrest["members"].select {
                filter { eq("user_id", userId) }
            }.decodeList<Member>()
        }
    }

    override suspend fun registerMember(member: Member, transactionId: String?): Result<Member> = runCatching {
        val payload = payloadFactory.createMemberPayload(member)
        runCatching {
            client.postgrest["members"].insert(payload) {
                select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE))
            }.decodeSingle<Member>()
        }.getOrElse {
            client.postgrest["members"].insert(payload) {
                select()
            }.decodeSingle<Member>()
        }
    }

    override suspend fun upsertMember(member: Member): Result<Member> = runCatching {
        val payload = payloadFactory.createMemberPayload(member)
        // ON CONFLICT (group_id, user_id) DO UPDATE — matches the unique constraint
        // enforced by the database and the admin row the groups trigger creates.
        runCatching {
            client.postgrest["members"].upsert(payload) {
                onConflict = "group_id,user_id"
                select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE))
            }.decodeSingle<Member>()
        }.getOrElse {
            client.postgrest["members"].upsert(payload) {
                onConflict = "group_id,user_id"
                select()
            }.decodeSingle<Member>()
        }
    }

    override suspend fun updateMemberStatus(memberId: String, status: MemberStatus): Result<Unit> = runCatching {
        client.postgrest["members"].update(buildJsonObject { put("status", status.name.lowercase()) }) {
            filter { eq("id", memberId) }
        }
    }

    override suspend fun updateMemberBankingDetails(memberId: String, bankName: String, accountNo: String, branchCode: String): Result<Unit> = runCatching {
        client.postgrest["members"].update(buildJsonObject {
            put("bank_name", bankName)
            put("account_no", accountNo)
            put("branch_code", branchCode)
        }) { filter { eq("id", memberId) } }
    }

    override suspend fun updateMemberDocumentStatus(memberId: String, docIndex: Int, status: DocumentStatus): Result<Unit> = runCatching {
        val col = "document_${docIndex}_status"
        client.postgrest["members"].update(buildJsonObject { put(col, status.name.lowercase()) }) {
            filter { eq("id", memberId) }
        }
    }

    override suspend fun updateMemberFcmToken(memberId: String, token: String): Result<Unit> = runCatching {
        client.postgrest["members"].update(buildJsonObject { put("fcm_token", token) }) {
            filter { eq("id", memberId) }
        }
    }

    override suspend fun updateMemberDocuments(
        memberId: String,
        doc1Url: String?, doc1Type: String?,
        doc2Url: String?, doc2Type: String?,
        doc3Url: String?, doc3Type: String?,
        doc4Url: String?, doc4Type: String?,
        doc5Url: String?, doc5Type: String?,
        profilePhotoUrl: String?
    ): Result<Unit> = runCatching {
        val updates = buildJsonObject {
            doc1Url?.let { put("document_1_url", it) }
            doc1Type?.let { put("document_1_type", it) }
            doc2Url?.let { put("document_2_url", it) }
            doc2Type?.let { put("document_2_type", it) }
            doc3Url?.let { put("document_3_url", it) }
            doc3Type?.let { put("document_3_type", it) }
            doc4Url?.let { put("document_4_url", it) }
            doc4Type?.let { put("document_4_type", it) }
            doc5Url?.let { put("document_5_url", it) }
            doc5Type?.let { put("document_5_type", it) }
            profilePhotoUrl?.let { put("profile_photo_url", it) }
        }
        if (updates.isNotEmpty()) {
            client.postgrest["members"].update(updates) {
                filter { eq("id", memberId) }
            }
        }
    }
}
