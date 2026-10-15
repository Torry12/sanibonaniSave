package com.sanibonani.save.data.repository.member

import com.sanibonani.save.data.datasource.LocalBeneficiaryDataSource
import com.sanibonani.save.data.datasource.LocalMemberDataSource
import com.sanibonani.save.data.datasource.RemoteBeneficiaryDataSource
import com.sanibonani.save.data.datasource.RemoteContributionDataSource
import com.sanibonani.save.data.datasource.RemoteMemberDataSource
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberDocument
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import com.sanibonani.save.domain.repository.member.MemberDocumentRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.service.FileStorageService
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Implementation of [MemberRepository], [BeneficiaryRepository], and [MemberDocumentRepository].
 *
 * Data Flow Pattern (as per AGENTS.md):
 * 1. Local First: Emits Room cache data immediately.
 * 2. Sync Network: Fetches from Supabase in background (via observeAndSync).
 * 3. Offline-First: Uses [com.sanibonani.save.data.local.PendingOperationDao] for outbox pattern on writes.
 */
class MemberRepositoryImpl @Inject constructor(
    private val remoteMemberSource: RemoteMemberDataSource,
    private val localMemberSource: LocalMemberDataSource,
    private val remoteBeneficiarySource: RemoteBeneficiaryDataSource,
    private val localBeneficiarySource: LocalBeneficiaryDataSource,
    private val groupRepo: GroupRepository,
    private val actuarialRepo: Provider<ActuarialRepository>,
    private val fileStorageService: FileStorageService,
    private val payloadFactory: com.sanibonani.save.data.repository.mappers.DataPayloadFactory,
    private val json: Json,
    private val pendingOpDao: com.sanibonani.save.data.local.PendingOperationDao,
    private val dbProvider: Provider<SanibonaniDatabase>,
    private val remoteContributionSource: RemoteContributionDataSource
) : BaseRepository("MemberRepository"), MemberRepository, BeneficiaryRepository, MemberDocumentRepository {

    override fun getGroupMembers(groupId: String): Flow<Result<List<Member>>> = observeAndSync(
        dbFlow = localMemberSource.observeActiveMembers(groupId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteMemberSource.getMembers(groupId).getOrThrow() },
        cacheSync = { list -> localMemberSource.syncActiveMembers(groupId, list) }
    )

    override suspend fun syncGroupMembers(groupId: String): Result<List<Member>> = safeRunCatching {
        val remote = remoteMemberSource.getMembers(groupId).getOrThrow()
        localMemberSource.syncActiveMembers(groupId, remote)
        remote
    }

    override suspend fun getGroupMembersPaginated(groupId: String, offset: Int, limit: Int): Result<List<Member>> =
        remoteMemberSource.getMembers(groupId) // Simplification for portability demo

    override suspend fun getMemberById(id: String): Result<Member> = safeRunCatching {
        remoteMemberSource.getMemberById(id).getOrElse {
            localMemberSource.getMemberById(id) ?: throw it
        }
    }

    override fun observeMemberById(id: String): Flow<Result<Member?>> = observeAndSyncItem(
        dbFlow = localMemberSource.observeMemberById(id),
        mapper = { it },
        toEntity = { it!! },
        networkFetch = { remoteMemberSource.getMemberById(id).getOrThrow() },
        cacheSync = { localMemberSource.saveMember(it) }
    )

    override suspend fun getMemberByUserId(userId: String, groupId: String): Result<Member> = safeRunCatching {
        remoteMemberSource.getMemberByUserId(userId, groupId).getOrElse {
            localMemberSource.getMemberByUserId(userId, groupId) ?: throw it
        }
    }

    override suspend fun getMemberships(userId: String): Result<List<Member>> = safeRunCatching {
        val remote = remoteMemberSource.getMemberships(userId).getOrThrow()
        localMemberSource.syncMemberships(userId, remote)
        remote
    }

    override fun observeMemberByUserId(userId: String, groupId: String): Flow<Result<Member?>> = observeAndSyncItem(
        dbFlow = localMemberSource.observeMemberByUserId(userId, groupId),
        mapper = { it },
        toEntity = { it!! },
        networkFetch = { remoteMemberSource.getMemberByUserId(userId, groupId).getOrThrow() },
        cacheSync = { localMemberSource.saveMember(it) }
    )

    override fun observeMemberships(userId: String): Flow<Result<List<Member>>> = observeAndSync(
        dbFlow = localMemberSource.observeAllMemberships(userId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteMemberSource.getMemberships(userId).getOrThrow() },
        cacheSync = { localMemberSource.syncMemberships(userId, it) }
    )

    override suspend fun registerMember(member: Member, transactionId: String?): Result<Member> = safeRunCatching {
        val registered = remoteMemberSource.registerMember(member, transactionId).getOrThrow()
        localMemberSource.saveMember(registered)
        registered
    }

    override suspend fun upsertMember(member: Member): Result<Member> = safeRunCatching {
        val saved = remoteMemberSource.upsertMember(member).getOrThrow()
        localMemberSource.saveMember(saved)
        saved
    }

    override suspend fun recordContribution(contribution: Contribution): Result<Unit> =
        safeApiCall(
            operationType = "contribution",
            groupId = contribution.groupId,
            payload = json.encodeToString(contribution),
            dao = pendingOpDao
        ) {
            remoteContributionSource.recordContribution(contribution).getOrThrow()
            dbProvider.get().contributionDao().upsertContributions(listOf(contribution.toEntity()))
            Unit
        }

    override suspend fun updateMemberStatus(memberId: String, status: MemberStatus): Result<Unit> = safeRunCatching {
        remoteMemberSource.updateMemberStatus(memberId, status).getOrThrow()
        val updated = localMemberSource.getMemberById(memberId)?.copy(status = status)
        if (updated != null) {
            localMemberSource.saveMember(updated)
            EventBus.emit(com.sanibonani.save.domain.event.MemberStatusChangedEvent(updated))
        }
    }

    override suspend fun updateMemberBankingDetails(memberId: String, bankName: String, accountNo: String, branchCode: String): Result<Unit> = safeRunCatching {
        remoteMemberSource.updateMemberBankingDetails(memberId, bankName, accountNo, branchCode).getOrThrow()
        localMemberSource.getMemberById(memberId)?.let {
            localMemberSource.saveMember(it.copy(bankName = bankName, accountNo = accountNo, branchCode = branchCode))
        }
    }

    override suspend fun getAllProbationMembers(): Result<List<Member>> = safeRunCatching {
        val remote = remoteMemberSource.getProbationMembers().getOrThrow()
        remote.forEach { localMemberSource.saveMember(it) }
        remote
    }

    override suspend fun updateMemberDocumentStatus(memberId: String, docIndex: Int, status: DocumentStatus): Result<Unit> = safeRunCatching {
        remoteMemberSource.updateMemberDocumentStatus(memberId, docIndex, status).getOrThrow()
        localMemberSource.getMemberById(memberId)?.let { member ->
            val updated = when (docIndex) {
                1 -> member.copy(document1Status = status)
                2 -> member.copy(document2Status = status)
                3 -> member.copy(document3Status = status)
                4 -> member.copy(document4Status = status)
                5 -> member.copy(document5Status = status)
                else -> member
            }
            localMemberSource.saveMember(updated)
        }
    }

    override suspend fun updateMemberDocuments(memberId: String, doc1Url: String?, doc1Type: String?, doc2Url: String?, doc2Type: String?, doc3Url: String?, doc3Type: String?, doc4Url: String?, doc4Type: String?, doc5Url: String?, doc5Type: String?, profilePhotoUrl: String?): Result<Unit> = safeRunCatching {
        remoteMemberSource.updateMemberDocuments(memberId, doc1Url, doc1Type, doc2Url, doc2Type, doc3Url, doc3Type, doc4Url, doc4Type, doc5Url, doc5Type, profilePhotoUrl).getOrThrow()
        localMemberSource.getMemberById(memberId)?.let { member ->
            localMemberSource.saveMember(
                member.copy(
                    document1Url = doc1Url ?: member.document1Url,
                    document1Type = doc1Type ?: member.document1Type,
                    document2Url = doc2Url ?: member.document2Url,
                    document2Type = doc2Type ?: member.document2Type,
                    document3Url = doc3Url ?: member.document3Url,
                    document3Type = doc3Type ?: member.document3Type,
                    document4Url = doc4Url ?: member.document4Url,
                    document4Type = doc4Type ?: member.document4Type,
                    document5Url = doc5Url ?: member.document5Url,
                    document5Type = doc5Type ?: member.document5Type,
                    profilePhotoUrl = profilePhotoUrl ?: member.profilePhotoUrl
                )
            )
        } ?: throw IllegalStateException("Member not found: $memberId")
    }

    override suspend fun uploadMemberDocument(memberId: String, documentIndex: Int, byteArray: ByteArray, fileName: String, documentType: String?): Result<String> = safeRunCatching {
        val extension = fileName.substringAfterLast(".", "pdf")
        val url = fileStorageService.uploadMemberDocument(memberId, documentIndex, byteArray, extension).getOrThrow()

        if (documentIndex == 0) {
            updateMemberDocuments(memberId, profilePhotoUrl = url).getOrThrow()
        } else {
            when (documentIndex) {
                1 -> updateMemberDocuments(memberId, doc1Url = url, doc1Type = documentType).getOrThrow()
                2 -> updateMemberDocuments(memberId, doc2Url = url, doc2Type = documentType).getOrThrow()
                3 -> updateMemberDocuments(memberId, doc3Url = url, doc3Type = documentType).getOrThrow()
                4 -> updateMemberDocuments(memberId, doc4Url = url, doc4Type = documentType).getOrThrow()
                5 -> updateMemberDocuments(memberId, doc5Url = url, doc5Type = documentType).getOrThrow()
            }
        }
        url
    }

    override fun getMemberContributions(memberId: String, groupId: String): Flow<Result<List<Contribution>>> = flowOf(Result.success(emptyList())) // Move to ContributionRepo
    override fun getGroupContributions(groupId: String): Flow<Result<List<Contribution>>> = flowOf(Result.success(emptyList()))

    // ── BeneficiaryRepository ────────────────────────────────────────────────

    override fun observeBeneficiaries(memberId: String): Flow<Result<List<Beneficiary>>> = observeAndSync(
        dbFlow = localBeneficiarySource.observeBeneficiaries(memberId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteBeneficiarySource.getBeneficiaries(memberId).getOrThrow() },
        cacheSync = { localBeneficiarySource.saveBeneficiaries(memberId, it) }
    )

    override suspend fun addBeneficiary(beneficiary: Beneficiary): Result<Beneficiary> = safeRunCatching {
        val saved = remoteBeneficiarySource.addBeneficiary(beneficiary).getOrThrow()
        localBeneficiarySource.saveBeneficiary(saved)
        saved
    }

    override suspend fun updateBeneficiary(beneficiary: Beneficiary): Result<Beneficiary> = safeRunCatching {
        val saved = remoteBeneficiarySource.updateBeneficiary(beneficiary).getOrThrow()
        localBeneficiarySource.saveBeneficiary(saved)
        saved
    }

    override suspend fun deleteBeneficiary(groupId: String, memberId: String, id: String): Result<Unit> = safeRunCatching {
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

    // ── MemberDocumentRepository ─────────────────────────────────────────────

    override fun observeMemberDocuments(memberId: String): Flow<Result<List<MemberDocument>>> = flowOf(Result.success(emptyList()))
    override suspend fun syncMemberDocuments(memberId: String): Result<List<MemberDocument>> = Result.success(emptyList())
    override suspend fun addMemberDocument(document: MemberDocument): Result<MemberDocument> = safeRunCatching {
        val saved = document.copy(id = document.id ?: java.util.UUID.randomUUID().toString())
        dbProvider.get().memberDocumentDao().upsertDocument(saved.toEntity())
        saved
    }

    override suspend fun updateMemberDocument(document: MemberDocument): Result<MemberDocument> = safeRunCatching {
        val saved = document.copy(id = document.id ?: java.util.UUID.randomUUID().toString())
        dbProvider.get().memberDocumentDao().upsertDocument(saved.toEntity())
        saved
    }

    override suspend fun uploadAndAddMemberDocument(memberId: String, groupId: String, label: String, byteArray: ByteArray, fileName: String, documentType: String?): Result<MemberDocument> = safeRunCatching {
        val extension = fileName.substringAfterLast(".", "pdf")
        val url = fileStorageService.uploadMemberDocument(memberId, 99, byteArray, extension).getOrThrow()
        val document = MemberDocument(
            memberId = memberId,
            groupId = groupId,
            label = label,
            documentUrl = url,
            documentType = documentType ?: "file",
            status = DocumentStatus.PENDING
        )
        addMemberDocument(document).getOrThrow()
    }
}
