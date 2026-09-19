package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemoteBeneficiaryDataSource
import com.sanibonani.save.domain.model.Beneficiary
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseBeneficiaryDataSourceImpl @Inject constructor(
    private val client: SupabaseClient
) : RemoteBeneficiaryDataSource {

    override suspend fun getBeneficiaries(memberId: String): Result<List<Beneficiary>> = runCatching {
        client.postgrest["beneficiaries"].select { filter { eq("member_id", memberId) } }.decodeList<Beneficiary>()
    }

    override suspend fun addBeneficiary(beneficiary: Beneficiary): Result<Beneficiary> = runCatching {
        client.postgrest["beneficiaries"].insert(beneficiary) { select() }.decodeSingle<Beneficiary>()
    }

    override suspend fun updateBeneficiary(beneficiary: Beneficiary): Result<Beneficiary> = runCatching {
        client.postgrest["beneficiaries"].update(beneficiary) {
            filter {
                eq("id", beneficiary.id!!)
                eq("group_id", beneficiary.groupId)
                eq("member_id", beneficiary.memberId)
            }
            select()
        }.decodeSingle<Beneficiary>()
    }

    override suspend fun deleteBeneficiary(groupId: String, memberId: String, id: String): Result<Unit> = runCatching {
        client.postgrest["beneficiaries"].delete {
            filter {
                eq("id", id)
                eq("group_id", groupId)
                eq("member_id", memberId)
            }
        }
    }
}
