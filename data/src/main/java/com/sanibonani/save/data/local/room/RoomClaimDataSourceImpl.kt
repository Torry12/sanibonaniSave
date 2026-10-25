package com.sanibonani.save.data.local.room

import com.sanibonani.save.data.datasource.LocalClaimDataSource
import com.sanibonani.save.data.local.BeneficiaryClaimDao
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomClaimDataSourceImpl @Inject constructor(
    private val claimDao: BeneficiaryClaimDao
) : LocalClaimDataSource {

    override fun observeClaimsForMember(memberId: String, groupId: String): Flow<List<BeneficiaryPayoutClaim>> =
        claimDao.observeClaimsForMember(memberId, groupId).map { list -> list.map { it.toModel() } }

    override fun observeClaimsForGroup(groupId: String): Flow<List<BeneficiaryPayoutClaim>> =
        claimDao.observeClaimsForGroup(groupId).map { list -> list.map { it.toModel() } }

    override fun observeEscalatedClaims(): Flow<List<BeneficiaryPayoutClaim>> =
        claimDao.observeEscalatedClaims().map { list -> list.map { it.toModel() } }

    override suspend fun saveClaim(claim: BeneficiaryPayoutClaim) {
        claimDao.upsertClaim(claim.toEntity())
    }

    override suspend fun saveClaims(claims: List<BeneficiaryPayoutClaim>) {
        claimDao.upsertClaims(claims.map { it.toEntity() })
    }

    override suspend fun syncClaimsForGroup(groupId: String, claims: List<BeneficiaryPayoutClaim>) {
        claimDao.syncForGroup(groupId, claims.map { it.toEntity() })
    }

    override suspend fun syncClaimsForMember(memberId: String, groupId: String, claims: List<BeneficiaryPayoutClaim>) {
        claimDao.syncForMember(memberId, groupId, claims.map { it.toEntity() })
    }

    override suspend fun clearAll() {
        claimDao.clearAll()
    }
}
