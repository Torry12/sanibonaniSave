package com.sanibonani.save.data.local.room

import com.sanibonani.save.data.datasource.LocalBeneficiaryDataSource
import com.sanibonani.save.data.local.BeneficiaryDao
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.model.Beneficiary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBeneficiaryDataSourceImpl @Inject constructor(
    private val beneficiaryDao: BeneficiaryDao
) : LocalBeneficiaryDataSource {

    override fun observeBeneficiaries(memberId: String): Flow<List<Beneficiary>> =
        beneficiaryDao.observeBeneficiaries(memberId).map { list -> list.map { it.toModel() } }

    override suspend fun saveBeneficiaries(memberId: String, beneficiaries: List<Beneficiary>) {
        beneficiaryDao.syncBeneficiaries(memberId, beneficiaries.map { it.toEntity() })
    }

    override suspend fun saveBeneficiary(beneficiary: Beneficiary) {
        beneficiaryDao.upsertBeneficiary(beneficiary.toEntity())
    }

    override suspend fun deleteBeneficiary(groupId: String, memberId: String, id: String) {
        beneficiaryDao.deleteBeneficiary(groupId, memberId, id)
    }

    override suspend fun clearAll() {
        beneficiaryDao.clearAll()
    }
}
