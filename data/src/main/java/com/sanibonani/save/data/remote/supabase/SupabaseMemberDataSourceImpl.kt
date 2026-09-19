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

@Singleton
class SupabaseMemberDataSourceImpl @Inject constructor(
    private val client: SupabaseClient,
    private val payloadFactory: com.sanibonani.save.data.repository.mappers.DataPayloadFactory
) : RemoteMemberDataSource {

    override suspend fun getMembers(groupId: String): Result<List<Member>> = runCatching {
        client.postgrest["members"].select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE)) {
            filter { eq("group_id", groupId) }
        }.decodeList<Member>()
    }

    override suspend fun getMemberById(id: String): Result<Member> = runCatching {
        client.postgrest["members"].select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE)) {
            filter { eq("id", id) }
        }.decodeSingle<Member>()
    }

    override suspend fun getMemberByUserId(userId: String, groupId: String): Result<Member> = runCatching {
        client.postgrest["members"].select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE)) {
            filter {
                eq("user_id", userId)
                eq("group_id", groupId)
            }
        }.decodeSingle<Member>()
    }

    override suspend fun getMemberships(userId: String): Result<List<Member>> = runCatching {
        client.postgrest["members"].select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE)) {
            filter { eq("user_id", userId) }
        }.decodeList<Member>()
    }

    override suspend fun registerMember(member: Member, transactionId: String?): Result<Member> = runCatching {
        val payload = payloadFactory.createMemberPayload(member)
        client.postgrest["members"].insert(payload) {
            select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE))
        }.decodeSingle<Member>()
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
}
