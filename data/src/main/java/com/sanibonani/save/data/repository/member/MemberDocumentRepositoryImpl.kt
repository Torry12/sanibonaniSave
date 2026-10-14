package com.sanibonani.save.data.repository.member

import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.data.local.*
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.MemberDocument
import com.sanibonani.save.domain.repository.member.MemberDocumentRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.StorageRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Provider
import com.sanibonani.save.domain.utils.safeRunCatching

class MemberDocumentRepositoryImpl @Inject constructor(
    private val supabaseProvider: Provider<SupabaseClient>,
    private val memberRepo: Provider<MemberRepository>,
    private val dbProvider: Provider<SanibonaniDatabase>,
    private val storageRepo: StorageRepository
) : BaseRepository("MemberDocumentRepository"), MemberDocumentRepository {

    private val supabase: SupabaseClient get() = supabaseProvider.get()
    private val db: SanibonaniDatabase get() = dbProvider.get()

    override fun observeMemberDocuments(memberId: String): Flow<Result<List<MemberDocument>>> = observeAndSync(
        dbFlow = db.memberDocumentDao().observeDocuments(memberId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["member_documents"].select { filter { eq("member_id", memberId) } }.decodeList<MemberDocument>()
        },
        cacheSync = { list -> db.memberDocumentDao().syncDocuments(memberId, list) }
    )

    override suspend fun syncMemberDocuments(memberId: String): Result<List<MemberDocument>> = safeRunCatching {
        val remote = supabase.postgrest["member_documents"].select { filter { eq("member_id", memberId) } }.decodeList<MemberDocument>()
        db.memberDocumentDao().syncDocuments(memberId, remote.map { it.toEntity() })
        remote
    }

    override suspend fun addMemberDocument(document: MemberDocument): Result<MemberDocument> = retryWithExponentialBackoff {
        val saved = supabase.postgrest["member_documents"].upsert(document) {
            onConflict = "id"
            select()
        }.decodeSingle<MemberDocument>()
        db.memberDocumentDao().upsertDocument(saved.toEntity())
        saved
    }

    override suspend fun updateMemberDocument(document: MemberDocument): Result<MemberDocument> = retryWithExponentialBackoff {
        val saved = supabase.postgrest["member_documents"].upsert(document) {
            onConflict = "id"
            select()
        }.decodeSingle<MemberDocument>()
        db.memberDocumentDao().upsertDocument(saved.toEntity())
        saved
    }

    override suspend fun uploadAndAddMemberDocument(
        memberId: String,
        groupId: String,
        label: String,
        byteArray: ByteArray,
        fileName: String,
        documentType: String?
    ): Result<MemberDocument> = safeRunCatching {
        val url = memberRepo.get().uploadMemberDocument(memberId, 99, byteArray, fileName, documentType).getOrThrow()
        val doc = MemberDocument(
            memberId = memberId,
            groupId = groupId,
            label = label,
            documentUrl = url,
            documentType = documentType ?: "file",
            status = DocumentStatus.PENDING
        )
        addMemberDocument(doc).getOrThrow()
    }
}
