package com.sanibonani.save.data.repository.member

import com.sanibonani.save.data.datasource.LocalMemberDataSource
import com.sanibonani.save.data.datasource.RemoteContributionDataSource
import com.sanibonani.save.data.datasource.RemoteMemberDataSource
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.service.FileStorageService
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Implementation of [MemberRepository].
 *
 * Beneficiaries live in `BeneficiaryRepositoryImpl` and member documents in
 * `MemberDocumentRepositoryImpl`; contribution reads live in the contribution repositories.
 *
 * Data Flow Pattern (as per AGENTS.md):
 * 1. Local First: Emits Room cache data immediately.
 * 2. Sync Network: Fetches from Supabase in background (via observeAndSync).
 * 3. Offline-First: Uses [com.sanibonani.save.data.local.PendingOperationDao] for outbox pattern on writes.
 */
class MemberRepositoryImpl @Inject constructor(
    private val remoteMemberSource: RemoteMemberDataSource,
    private val localMemberSource: LocalMemberDataSource,
    private val fileStorageService: FileStorageService,
    private val json: Json,
    private val pendingOpDao: com.sanibonani.save.data.local.PendingOperationDao,
    private val dbProvider: Provider<SanibonaniDatabase>,
    private val remoteContributionSource: RemoteContributionDataSource
) : BaseRepository("MemberRepository"), MemberRepository {

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

    override suspend fun getMemberById(id: String): Result<Member> = safeRunCatching {
        remoteMemberSource.getMemberById(id).getOrElse {
            localMemberSource.getMemberById(id) ?: throw it
        }
    }

    override fun observeMemberById(id: String): Flow<Result<Member?>> = observeAndSyncItem(
        dbFlow = localMemberSource.observeMemberById(id),
        mapper = { it },
        toEntity = { it },
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
        toEntity = { it },
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

    override suspend fun registerMember(member: Member, transactionId: String?): Result<Member> =
        authoritativeCall("registerMember") {
            val registered = remoteMemberSource.registerMember(member, transactionId).getOrThrow()
            localMemberSource.saveMember(registered)
            registered
        }

    override suspend fun upsertMember(member: Member): Result<Member> = authoritativeCall("upsertMember") {
        val saved = remoteMemberSource.upsertMember(member).getOrThrow()
        localMemberSource.saveMember(saved)
        saved
    }

    override suspend fun recordContribution(contribution: Contribution): Result<Unit> {
        val stableContribution = contribution.copy(
            transactionId = contribution.transactionId
                ?: contribution.id
                ?: java.util.UUID.randomUUID().toString()
        )
        return safeApiCall(
            operationType = "contribution",
            groupId = stableContribution.groupId,
            payload = json.encodeToString(stableContribution),
            dao = pendingOpDao,
            idempotencyKey = stableContribution.transactionId
        ) {
            remoteContributionSource.recordContribution(stableContribution).getOrThrow()
            dbProvider.get().contributionDao().upsertContributions(listOf(stableContribution.toEntity()))
        }
    }

    override suspend fun updateMemberStatus(memberId: String, status: MemberStatus): Result<Unit> =
        authoritativeCall("updateMemberStatus") {
            remoteMemberSource.updateMemberStatus(memberId, status).getOrThrow()
            val updated = localMemberSource.getMemberById(memberId)?.copy(status = status)
            if (updated != null) {
                localMemberSource.saveMember(updated)
                EventBus.emit(com.sanibonani.save.domain.event.MemberStatusChangedEvent(updated))
            }
        }

    override suspend fun updateMemberBankingDetails(
        memberId: String,
        bankName: String,
        accountNo: String,
        branchCode: String
    ): Result<Unit> = authoritativeCall("updateMemberBankingDetails") {
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

    override suspend fun updateMemberDocumentStatus(
        memberId: String,
        docIndex: Int,
        status: DocumentStatus
    ): Result<Unit> = authoritativeCall("updateMemberDocumentStatus") {
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

    override suspend fun updateMemberDocuments(
        memberId: String,
        doc1Url: String?,
        doc1Type: String?,
        doc2Url: String?,
        doc2Type: String?,
        doc3Url: String?,
        doc3Type: String?,
        doc4Url: String?,
        doc4Type: String?,
        doc5Url: String?,
        doc5Type: String?,
        profilePhotoUrl: String?
    ): Result<Unit> = authoritativeCall("updateMemberDocuments") {
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

    override suspend fun uploadMemberDocument(
        memberId: String,
        documentIndex: Int,
        byteArray: ByteArray,
        fileName: String,
        documentType: String?
    ): Result<String> = authoritativeCall("uploadMemberDocument") {
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
}
