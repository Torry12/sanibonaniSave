package com.sanibonani.save.data.repository.member

import com.sanibonani.save.data.datasource.LocalBeneficiaryDataSource
import com.sanibonani.save.data.datasource.RemoteBeneficiaryDataSource
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import com.sanibonani.save.domain.service.FileStorageService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Beneficiary roster for a member: Room cache first, then reconciled against Supabase, with the
 * offline outbox applied by [BaseRepository] on writes.
 */
class BeneficiaryRepositoryImpl @Inject constructor(
    private val remoteBeneficiarySource: RemoteBeneficiaryDataSource,
    private val localBeneficiarySource: LocalBeneficiaryDataSource,
    private val fileStorageService: FileStorageService
) : BaseRepository("BeneficiaryRepository"), BeneficiaryRepository {

    override fun observeBeneficiaries(memberId: String): Flow<Result<List<Beneficiary>>> = observeAndSync(
        dbFlow = localBeneficiarySource.observeBeneficiaries(memberId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteBeneficiarySource.getBeneficiaries(memberId).getOrThrow() },
        cacheSync = { localBeneficiarySource.saveBeneficiaries(memberId, it) }
    )

    override suspend fun addBeneficiary(beneficiary: Beneficiary): Result<Beneficiary> =
        authoritativeCall("addBeneficiary") {
            val saved = remoteBeneficiarySource.addBeneficiary(beneficiary).getOrThrow()
            localBeneficiarySource.saveBeneficiary(saved)
            saved
        }

    override suspend fun updateBeneficiary(beneficiary: Beneficiary): Result<Beneficiary> =
        authoritativeCall("updateBeneficiary") {
            val saved = remoteBeneficiarySource.updateBeneficiary(beneficiary).getOrThrow()
            localBeneficiarySource.saveBeneficiary(saved)
            saved
        }

    override suspend fun deleteBeneficiary(groupId: String, memberId: String, id: String): Result<Unit> =
        authoritativeCall("deleteBeneficiary") {
            remoteBeneficiarySource.deleteBeneficiary(groupId, memberId, id).getOrThrow()
            localBeneficiarySource.deleteBeneficiary(groupId, memberId, id)
        }

    override suspend fun syncBeneficiaries(memberId: String): Result<List<Beneficiary>> = safeRunCatching {
        val remote = remoteBeneficiarySource.getBeneficiaries(memberId).getOrThrow()
        localBeneficiarySource.saveBeneficiaries(memberId, remote)
        remote
    }

    override suspend fun uploadBeneficiaryDocument(beneficiaryId: String, groupId: String, memberId: String, byteArray: ByteArray, fileName: String): Result<String> {
        val extension = fileName.substringAfterLast(".", "pdf")
        return fileStorageService.uploadBeneficiaryDocument(memberId, beneficiaryId, byteArray, extension)
    }
}
