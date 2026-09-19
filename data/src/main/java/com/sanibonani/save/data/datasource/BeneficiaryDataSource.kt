package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.Beneficiary
import kotlinx.coroutines.flow.Flow

interface RemoteBeneficiaryDataSource {
    suspend fun getBeneficiaries(memberId: String): Result<List<Beneficiary>>
    suspend fun addBeneficiary(beneficiary: Beneficiary): Result<Beneficiary>
    suspend fun updateBeneficiary(beneficiary: Beneficiary): Result<Beneficiary>
    suspend fun deleteBeneficiary(groupId: String, memberId: String, id: String): Result<Unit>
}

interface LocalBeneficiaryDataSource {
    fun observeBeneficiaries(memberId: String): Flow<List<Beneficiary>>
    suspend fun saveBeneficiaries(memberId: String, beneficiaries: List<Beneficiary>)
    suspend fun saveBeneficiary(beneficiary: Beneficiary)
    suspend fun deleteBeneficiary(groupId: String, memberId: String, id: String)
    suspend fun clearAll()
}
