package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.domain.model.BeneficiaryClaimStatus
import kotlinx.coroutines.flow.Flow

interface RemoteClaimDataSource {
    suspend fun submitClaim(claim: BeneficiaryPayoutClaim): Result<BeneficiaryPayoutClaim>
    suspend fun getClaimsForMember(memberId: String, groupId: String): Result<List<BeneficiaryPayoutClaim>>
    suspend fun getClaimsForGroup(groupId: String): Result<List<BeneficiaryPayoutClaim>>
    suspend fun getClaimById(id: String): Result<BeneficiaryPayoutClaim>
    suspend fun updateClaimStatus(claimId: String, status: BeneficiaryClaimStatus, reviewedBy: String?, adminNotes: String?, rejectionReason: String?): Result<Unit>
    suspend fun payClaimAtomic(claimId: String, adminId: String, notes: String?): Result<Unit>
    suspend fun getEscalatedClaims(): Result<List<BeneficiaryPayoutClaim>>
}

interface LocalClaimDataSource {
    fun observeClaimsForMember(memberId: String, groupId: String): Flow<List<BeneficiaryPayoutClaim>>
    fun observeClaimsForGroup(groupId: String): Flow<List<BeneficiaryPayoutClaim>>
    fun observeEscalatedClaims(): Flow<List<BeneficiaryPayoutClaim>>
    suspend fun saveClaim(claim: BeneficiaryPayoutClaim)
    suspend fun saveClaims(claims: List<BeneficiaryPayoutClaim>)
    suspend fun syncClaimsForGroup(groupId: String, claims: List<BeneficiaryPayoutClaim>)
    suspend fun syncClaimsForMember(memberId: String, groupId: String, claims: List<BeneficiaryPayoutClaim>)
    suspend fun clearAll()
}
