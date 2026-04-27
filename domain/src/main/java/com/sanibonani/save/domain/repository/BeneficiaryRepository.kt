package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.Beneficiary
import kotlinx.coroutines.flow.Flow

interface BeneficiaryRepository {
    fun observeBeneficiaries(memberId: String): Flow<Result<List<Beneficiary>>>
    suspend fun addBeneficiary(beneficiary: Beneficiary): Result<Beneficiary>
    suspend fun updateBeneficiary(beneficiary: Beneficiary): Result<Beneficiary>
    suspend fun deleteBeneficiary(groupId: String, memberId: String, id: String): Result<Unit>
    suspend fun syncBeneficiaries(memberId: String): Result<List<Beneficiary>>
    suspend fun uploadBeneficiaryDocument(
        beneficiaryId: String,
        groupId: String,
        memberId: String,
        byteArray: ByteArray,
        fileName: String
    ): Result<String>
}
