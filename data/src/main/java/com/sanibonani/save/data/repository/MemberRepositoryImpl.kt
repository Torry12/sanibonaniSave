package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.LedgerEntryCreatedEvent
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.remote.PostgrestColumns
import com.sanibonani.save.data.utils.logAndGetMessage
import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.ContributionStatus
import com.sanibonani.save.domain.model.RecordContributionResult
import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberDocument
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.ActuarialRepository
import com.sanibonani.save.domain.repository.BeneficiaryRepository
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.MemberDocumentRepository
import com.sanibonani.save.domain.repository.MemberRepository
import com.sanibonani.save.domain.repository.StorageRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Provider

internal const val PROFILE_PHOTO_INDEX = 0
internal const val PROFILE_PHOTOS_BUCKET = "avatars"
internal const val MEMBER_DOCUMENTS_BUCKET = "documents"

internal fun memberUploadBucketForIndex(documentIndex: Int): String {
    return if (documentIndex == PROFILE_PHOTO_INDEX) PROFILE_PHOTOS_BUCKET else MEMBER_DOCUMENTS_BUCKET
}

internal fun memberUploadPathForIndex(memberId: String, documentIndex: Int, ext: String, timestamp: Long): String {
    return if (documentIndex == PROFILE_PHOTO_INDEX) {
        "members/$memberId/profile_$timestamp.$ext"
    } else {
        "members/$memberId/doc_${documentIndex}_$timestamp.$ext"
    }
}

class MemberRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val groupRepo: GroupRepository,
    private val actuarialRepo: Provider<ActuarialRepository>,
    private val db: SanibonaniDatabase,
    private val storageRepo: StorageRepository
) : BaseRepository("MemberRepository"), MemberRepository, BeneficiaryRepository, MemberDocumentRepository {

    // NOTE: Keep aligned with PostgrestColumns / supabase/rebuild_kit_v4/01_TABLES_AND_INDEXES.sql.
    // Do NOT select optional columns unless the backend schema supports them.
    private val CONTRIBUTION_COLUMNS_SAFE = "id,member_id,group_id,amount,created_at,due_date,paid_at,status,type,transaction_id,late_fees_applied"

    private fun Throwable.isMissingColumnError(): Boolean {
        val msg = message.orEmpty()
        return msg.contains("column", ignoreCase = true) && msg.contains("does not exist", ignoreCase = true)
    }

    private suspend fun fetchMembersList(
        columns: String = PostgrestColumns.MEMBERS_SAFE,
        configure: io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder.() -> Unit = {}
    ): List<Member> = try {
        supabase.postgrest["members"].select(columns = Columns.raw(columns)) {
            configure()
        }.decodeList<Member>()
    } catch (e: Exception) {
        if (columns == PostgrestColumns.MEMBERS_SAFE && e.isMissingColumnError()) {
            AppLogger.w(
                tag,
                "Members schema missing extended columns; using minimal projection. Apply supabase/migrations/20260529120000_align_members_app_columns.sql."
            )
            fetchMembersList(PostgrestColumns.MEMBERS_MINIMAL, configure)
        } else {
            throw e
        }
    }

    private suspend fun fetchMemberSingle(
        columns: String = PostgrestColumns.MEMBERS_SAFE,
        configure: io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder.() -> Unit
    ): Member = try {
        supabase.postgrest["members"].select(columns = Columns.raw(columns)) {
            configure()
        }.decodeSingle<Member>()
    } catch (e: Exception) {
        if (columns == PostgrestColumns.MEMBERS_SAFE && e.isMissingColumnError()) {
            AppLogger.w(tag, "Members schema missing extended columns; using minimal projection.")
            fetchMemberSingle(PostgrestColumns.MEMBERS_MINIMAL, configure)
        } else {
            throw e
        }
    }

    private suspend fun decodeMemberAfterWrite(
        columns: String,
        request: suspend (String) -> Member
    ): Member = try {
        request(columns)
    } catch (e: Exception) {
        if (columns == PostgrestColumns.MEMBERS_SAFE && e.isMissingColumnError()) {
            AppLogger.w(tag, "Members schema missing extended columns after write; using minimal projection.")
            decodeMemberAfterWrite(PostgrestColumns.MEMBERS_MINIMAL, request)
        } else {
            throw e
        }
    }

    private suspend fun fetchMemberOrNull(
        columns: String = PostgrestColumns.MEMBERS_SAFE,
        configure: io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder.() -> Unit
    ): Member? = try {
        supabase.postgrest["members"].select(columns = Columns.raw(columns)) {
            configure()
        }.decodeSingleOrNull<Member>()
    } catch (e: Exception) {
        if (columns == PostgrestColumns.MEMBERS_SAFE && e.isMissingColumnError()) {
            fetchMemberOrNull(PostgrestColumns.MEMBERS_MINIMAL, configure)
        } else {
            null
        }
    }

    override fun getGroupMembers(groupId: String): Flow<Result<List<Member>>> = observeAndSync(
        dbFlow = db.memberDao().observeActiveMembers(groupId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            fetchMembersList {
                filter {
                    eq("group_id", groupId)
                    // Only active and probation members for portal display
                    or {
                        eq("status", "active")
                        eq("status", "probation")
                    }
                }
            }
        },
        cacheSync = { list -> db.memberDao().syncActiveMembers(groupId, list) }
    )

    override suspend fun syncGroupMembers(groupId: String): Result<List<Member>> = retryWithExponentialBackoff {
        runCatching {
            val remote = fetchMembersList {
                filter {
                    eq("group_id", groupId)
                    or {
                        eq("status", "active")
                        eq("status", "probation")
                    }
                }
            }
            db.memberDao().syncActiveMembers(groupId, remote.map { it.toEntity() })
            remote
        }
    }
    
    override suspend fun getGroupMembersPaginated(
        groupId: String,
        offset: Int,
        limit: Int
    ): Result<List<Member>> = retryWithExponentialBackoff {
        runCatching {
            val members = fetchMembersList {
                filter {
                    eq("group_id", groupId)
                }
                range(offset.toLong(), (offset + limit - 1).toLong())
                order("created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }
            db.memberDao().upsertMembers(members.map { it.toEntity() })
            members
        }
    }

    override suspend fun getMemberById(id: String): Result<Member> = retryWithExponentialBackoff {
        runCatching {
            val member = fetchMemberSingle { filter { eq("id", id) } }
            db.memberDao().upsertMember(member.toEntity())
            member
        }.recoverCatching { exception ->
            db.memberDao().getMemberById(id)?.toModel()
                ?: throw IllegalStateException(exception.logAndGetMessage(tag))
        }
    }

    override suspend fun getMemberByUserId(userId: String, groupId: String): Result<Member> = retryWithExponentialBackoff {
        runCatching {
            val member = fetchMemberSingle {
                filter {
                    eq("user_id", userId)
                    eq("group_id", groupId)
                }
            }
            db.memberDao().upsertMember(member.toEntity())
            member
        }.recoverCatching { exception ->
            db.memberDao().getMemberByUserId(userId, groupId)?.toModel()
                ?: throw IllegalStateException(exception.logAndGetMessage(tag))
        }
    }

    override suspend fun getMemberships(userId: String): Result<List<Member>> = retryWithExponentialBackoff {
        runCatching {
            val members = fetchMembersList { filter { eq("user_id", userId) } }
            db.memberDao().syncMembershipsForUser(userId, members.map { it.toEntity() })
            members
        }.recoverCatching { exception ->
            val local = db.memberDao().getAllMemberships(userId).map { it.toModel() }
            if (local.isNotEmpty()) local else throw IllegalStateException(exception.logAndGetMessage(tag))
        }
    }

    override fun observeMemberships(userId: String): Flow<Result<List<Member>>> = observeAndSync(
        dbFlow = db.memberDao().observeAllMemberships(userId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            fetchMembersList { filter { eq("user_id", userId) } }
        },
        cacheSync = { list -> db.memberDao().syncMembershipsForUser(userId, list) }
    )

    override fun observeMemberByUserId(userId: String, groupId: String): Flow<Result<Member?>> = channelFlow {
        val dbJob = launch {
            observeAndSyncItem(
                dbFlow = db.memberDao().observeMemberByUserId(userId, groupId),
                mapper = { it.toModel() },
                toEntity = { it.toEntity() },
                networkFetch = {
                    fetchMemberSingle {
                        filter {
                            eq("user_id", userId)
                            eq("group_id", groupId)
                        }
                    }
                },
                cacheSync = { entity -> db.memberDao().upsertMember(entity) }
            ).collect { send(it) }
        }

        val channel = supabase.realtime.channel("member_rt_$userId")
        val changes = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "members"
        }

        val rtJob = launch {
            changes.collect { update ->
                if (update.record["user_id"]?.jsonPrimitive?.content == userId && 
                    update.record["group_id"]?.jsonPrimitive?.content == groupId) {
                    getMemberByUserId(userId, groupId)
                }
            }
        }

        channel.subscribe()
        awaitClose {
            dbJob.cancel()
            rtJob.cancel()
            launch { channel.unsubscribe() }
        }
    }


    override suspend fun registerMember(member: Member, transactionId: String?): Result<Member> = runCatching {
        if (member.groupId.isBlank()) throw Exception("Group ID required for registration")
        
        // 1. UNIQUE CHECK: Prevent duplicate joining by UserID + GroupID
        val existing = fetchMemberOrNull {
            filter {
                eq("user_id", member.userId ?: "")
                eq("group_id", member.groupId)
            }
        }
        
        if (existing != null) {
            if (existing.status != MemberStatus.PENDING_PAYMENT) {
                throw Exception("You are already a member of this group.")
            }
            // If they are PENDING_PAYMENT and we have a txId, we can proceed to update them
            if (transactionId == null) {
                throw Exception("You have a pending registration. Please complete payment.")
            }
        }

        // 2. UNIQUE CHECK: Member ID number must be unique within the group (if not the same member)
        val duplicateId = fetchMemberOrNull {
            filter {
                eq("group_id", member.groupId)
                eq("id_number", member.idNumber ?: "")
            }
        }
        if (duplicateId != null && duplicateId.id != existing?.id) {
            throw Exception("This ID number is already registered in this group.")
        }


        val group = groupRepo.getGroupById(member.groupId).getOrThrow()
        
        val isGroupAdmin = group.adminUserId == member.userId
        if (!group.registrationPaid && !isGroupAdmin) {
            throw Exception("This group is not yet active. Please contact the administrator.")
        }

        // JOINING FEE LOGIC: If a joining fee is required and no transactionId provided,
        // the member will be created with PENDING_PAYMENT status.
        // The payment flow will update the status to ACTIVE/PROBATION after successful payment.
        // This allows the registration form → payment → completion flow.

        // Calculate probation end date (now + probation_months)
        val now = java.time.LocalDateTime.now()
        val probationEndDate = now.plusMonths(group.probationMonths.toLong())
        
        // Create member with proper initial status
        val nextStatus = if (transactionId != null) {
            if (group.probationMonths > 0) MemberStatus.PROBATION else MemberStatus.ACTIVE
        } else {
            if (group.joiningFee > 0) MemberStatus.PENDING_PAYMENT 
            else if (group.probationMonths > 0) MemberStatus.PROBATION 
            else MemberStatus.ACTIVE
        }

        val memberWithDates = member.copy(
            status = nextStatus,
            joinedAt = existing?.joinedAt ?: now.toString(),
            probationEndAt = existing?.probationEndAt ?: probationEndDate.toString()
        )
        
        val insertData = buildJsonObject {
            put("group_id", memberWithDates.groupId)
            memberWithDates.userId?.let { put("user_id", it) }
            put("full_name", memberWithDates.fullName)
            put("id_number", memberWithDates.idNumber)
            put("phone", memberWithDates.phone)
            put("email", memberWithDates.email)
            put("street", memberWithDates.street)
            put("suburb", memberWithDates.suburb)
            put("city", memberWithDates.city)
            put("province", memberWithDates.province)
            put("notification_pref", memberWithDates.notificationPref.name.lowercase())
            put("status", memberWithDates.status.name.lowercase())
            memberWithDates.joinedAt?.let { put("joined_at", it) }
            memberWithDates.probationEndAt?.let { put("probation_end_at", it) }
            memberWithDates.memberKey?.let { put("member_key", it) }
            memberWithDates.profilePhotoUrl?.let { put("profile_photo_url", it) }
            put("total_contributions", memberWithDates.totalContributions ?: 0)
            put("total_paid", memberWithDates.totalPaid)
            put("beneficiary_count", memberWithDates.beneficiaryCount ?: 0)
            put("beneficiary_over_65_count", memberWithDates.beneficiaryOver65Count ?: 0)
            memberWithDates.monthlyContributionOverride?.let { put("monthly_contribution_override", it) }
        }
        
        val registered = if (existing != null) {
            decodeMemberAfterWrite(PostgrestColumns.MEMBERS_SAFE) { cols ->
                supabase.postgrest["members"].update(insertData) {
                    filter { eq("id", existing.id ?: throw IllegalStateException("Member ID missing for update")) }
                    select(columns = Columns.raw(cols))
                }.decodeSingle<Member>()
            }
        } else {
            decodeMemberAfterWrite(PostgrestColumns.MEMBERS_SAFE) { cols ->
                supabase.postgrest["members"].insert(insertData) {
                    select(columns = Columns.raw(cols))
                }.decodeSingle<Member>()
            }
        }

        
        // 3. JOINING FEE LOGIC: If we have a transactionId, record the contribution
        if (transactionId != null && group.joiningFee > 0) {
            val contribution = Contribution(
                memberId = registered.id ?: throw IllegalStateException("Registered member ID is null"),
                groupId = member.groupId,
                amount = group.joiningFee,
                status = ContributionStatus.PAID,
                type = "joining_fee",
                dueDate = registered.joinedAt ?: now.toString(),
                paidAt = now.toString(),
                transactionId = transactionId
            )
            recordContribution(contribution).getOrThrow()
        }
        
        db.memberDao().upsertMember(registered.toEntity())
        registered
    }

    override suspend fun recordContribution(contribution: Contribution): Result<Unit> = runCatching {
        val group = groupRepo.getGroupById(contribution.groupId).getOrThrow()
        val member = getMemberById(contribution.memberId).getOrThrow()

        if (contribution.type == "member_fee_ledger") {
            val now = java.time.LocalDateTime.now().toString()
            val ledgerInsert = buildJsonObject {
                put("member_id", contribution.memberId)
                put("group_id", contribution.groupId)
                put("amount", contribution.amount)
                put("type", "member_fee")
                put("status", contribution.status.name.lowercase())
                put("due_date", contribution.dueDate)
                put("paid_at", contribution.paidAt ?: now)
                contribution.transactionId?.let { put("transaction_id", it) }
                put("payment_method", "ledger")
            }

            supabase.postgrest["contributions"].insert(ledgerInsert)

            val ledgerRow = supabase.postgrest["contributions"].select(
                columns = Columns.raw(CONTRIBUTION_COLUMNS_SAFE)
            ) {
                filter {
                    eq("member_id", contribution.memberId)
                    eq("group_id", contribution.groupId)
                    eq("type", "member_fee")
                    contribution.transactionId?.takeIf { it.isNotBlank() }?.let {
                        eq("transaction_id", it)
                    }
                }
                order("created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                range(0, 0)
            }.decodeList<Contribution>().firstOrNull()
                ?: throw IllegalStateException("Member fee ledger entry created but could not be retrieved.")

            db.contributionDao().upsertContributions(listOf(ledgerRow.toEntity()))

            return@runCatching
        }

        // Use actuarial premium if it's a burial society and not a manual amount
        val finalAmount = if (contribution.amount <= 0.0) {
            actuarialRepo.get().calculateMemberContribution(group, member)
        } else {
            contribution.amount
        }

        // Use Atomic RPC to ensure data integrity
        val rpcParams = buildJsonObject {
            put("p_member_id", contribution.memberId)
            put("p_group_id", contribution.groupId)
            put("p_amount", finalAmount)
            put("p_type", contribution.type)
            put("p_due_date", contribution.dueDate)
            put("p_paid_at", contribution.paidAt)
            put("p_status", contribution.status.name.lowercase())
            contribution.transactionId?.let { put("p_tx_id", it) }
        }

        // NOTE:
        // Some PostgREST RPCs return a JSON object (single composite) rather than a JSON array.
        // We've seen runtime decode errors in the payment flow when the client tries to decode
        // an array but receives an object. To make this robust across PostgREST/RPC return
        // shapes, we:
        //  1) execute the RPC (atomic DB-side write)
        //  2) use the returned new balance and contribution record
        val result = supabase.postgrest.rpc("record_contribution_v1", rpcParams)
            .decodeAs<RecordContributionResult>()

        val inserted = result.contribution
        val newBalance = result.newBalance

        // Update local cache
        db.contributionDao().upsertContributions(listOf(inserted.toEntity()))
        
        // Update member and group cache locally to match DB state
        // Only increment totalContributions if it was an actual contribution (matching RPC logic)
        val isActualContribution = contribution.type != "member_fee" && contribution.type != "platform_fee"
        val updatedMember = if (isActualContribution) {
            member.copy(
                totalContributions = (member.totalContributions ?: 0) + 1,
                totalPaid = (member.totalPaid ?: 0.0) + finalAmount
            )
        } else member

        val updatedGroup = group.copy(balance = newBalance)
        
        db.memberDao().upsertMember(updatedMember.toEntity())
        db.groupDao().upsertGroup(updatedGroup.toEntity())

        // Emit event for real-time audit logs
        EventBus.emit(
            LedgerEntryCreatedEvent(
                LedgerEntry(
                    groupId = contribution.groupId,
                    amount = finalAmount,
                    balanceAfter = newBalance,
                    description = "Contribution: ${contribution.type}",
                    category = if (contribution.type == "member_fee") "platform_fee" else "contribution",
                    transactionId = contribution.transactionId
                )
            )
        )
        
        Unit
    }

    override suspend fun updateMemberStatus(memberId: String, status: MemberStatus): Result<Unit> = runCatching {
        val updated = supabase.postgrest["members"].update(buildJsonObject { put("status", status.name.lowercase()) }) {
            filter { eq("id", memberId) }
            select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE))
        }.decodeSingle<Member>()

        db.memberDao().upsertMember(updated.toEntity())
        Unit
    }

    override suspend fun getAllProbationMembers(): Result<List<Member>> = runCatching {
        val members = fetchMembersList {
            filter { eq("status", MemberStatus.PROBATION.name.lowercase()) }
        }
        db.memberDao().upsertMembers(members.map { it.toEntity() })
        members
    }


    override suspend fun updateMemberDocumentStatus(
        memberId: String,
        docIndex: Int,
        status: DocumentStatus
    ): Result<Unit> = runCatching {
        val field = "document_${docIndex}_status"
        val updated = supabase.postgrest["members"].update(buildJsonObject {
            put(field, status.name.lowercase())
        }) {
            filter { eq("id", memberId) }
            select(columns = Columns.raw(PostgrestColumns.MEMBERS_SAFE))
        }.decodeSingle<Member>()

        db.memberDao().upsertMember(updated.toEntity())
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
    ): Result<Unit> = runCatching {
        val updates = buildJsonObject {
            doc1Url?.let { 
                put("document_1_url", it)
                put("document_1_status", "pending")
                doc1Type?.let { type -> put("document_1_type", type) }
            }
            doc2Url?.let { 
                put("document_2_url", it)
                put("document_2_status", "pending")
                doc2Type?.let { type -> put("document_2_type", type) }
            }
            doc3Url?.let { 
                put("document_3_url", it)
                put("document_3_status", "pending")
                doc3Type?.let { type -> put("document_3_type", type) }
            }
            doc4Url?.let { 
                put("document_4_url", it)
                put("document_4_status", "pending")
                doc4Type?.let { type -> put("document_4_type", type) }
            }
            doc5Url?.let { 
                put("document_5_url", it)
                put("document_5_status", "pending")
                doc5Type?.let { type -> put("document_5_type", type) }
            }
            profilePhotoUrl?.let { put("profile_photo_url", it) }
        }
        
        if (updates.isNotEmpty()) {
            retryWithExponentialBackoff {
                supabase.postgrest["members"].update(updates) { 
                    filter { eq("id", memberId) } 
                }
            }

            // Patch local cache in-place to avoid an extra network fetch after upload.
            db.memberDao().getMemberById(memberId)?.let { local ->
                val patched = local.copy(
                    profilePhotoUrl = profilePhotoUrl ?: local.profilePhotoUrl,
                    document1Url = doc1Url ?: local.document1Url,
                    document1Type = doc1Type ?: local.document1Type,
                    document1Status = if (doc1Url != null) DocumentStatus.PENDING else local.document1Status,
                    document2Url = doc2Url ?: local.document2Url,
                    document2Type = doc2Type ?: local.document2Type,
                    document2Status = if (doc2Url != null) DocumentStatus.PENDING else local.document2Status,
                    document3Url = doc3Url ?: local.document3Url,
                    document3Type = doc3Type ?: local.document3Type,
                    document3Status = if (doc3Url != null) DocumentStatus.PENDING else local.document3Status,
                    document4Url = doc4Url ?: local.document4Url,
                    document4Type = doc4Type ?: local.document4Type,
                    document4Status = if (doc4Url != null) DocumentStatus.PENDING else local.document4Status,
                    document5Url = doc5Url ?: local.document5Url,
                    document5Type = doc5Type ?: local.document5Type,
                    document5Status = if (doc5Url != null) DocumentStatus.PENDING else local.document5Status,
                    updatedAt = System.currentTimeMillis()
                )
                db.memberDao().upsertMember(patched)
            }
        }
        Unit
    }

    override suspend fun uploadMemberDocument(
        memberId: String,
        documentIndex: Int,
        byteArray: ByteArray,
        fileName: String,
        documentType: String?
    ): Result<String> = try {
        val ext = fileName.substringAfterLast(".", if (documentIndex == PROFILE_PHOTO_INDEX) "jpg" else "pdf")
        val timestamp = System.currentTimeMillis()
        // Use a unique path for all documents to avoid stale image/file caching (Coil/HTTP/UI).
        val path = memberUploadPathForIndex(memberId, documentIndex, ext, timestamp)
        val bucket = memberUploadBucketForIndex(documentIndex)

        val uploadedPath = storageRepo.uploadFile(bucket, path, byteArray).getOrThrow()
        val publicUrl = storageRepo.getPublicUrl(bucket, uploadedPath)

        updateMemberDocuments(
            memberId = memberId,
            doc1Url = if (documentIndex == 1) publicUrl else null,
            doc1Type = if (documentIndex == 1) documentType else null,
            doc2Url = if (documentIndex == 2) publicUrl else null,
            doc2Type = if (documentIndex == 2) documentType else null,
            doc3Url = if (documentIndex == 3) publicUrl else null,
            doc3Type = if (documentIndex == 3) documentType else null,
            doc4Url = if (documentIndex == 4) publicUrl else null,
            doc4Type = if (documentIndex == 4) documentType else null,
            doc5Url = if (documentIndex == 5) publicUrl else null,
            doc5Type = if (documentIndex == 5) documentType else null,
            profilePhotoUrl = if (documentIndex == PROFILE_PHOTO_INDEX) publicUrl else null
        ).getOrThrow()

        Result.success(publicUrl)
    } catch (e: Exception) {
        val userMsg = e.logAndGetMessage(tag)
        AppLogger.e(tag, "Failed to upload document: $userMsg", e)
        Result.failure(IllegalStateException(userMsg))
    }

    override suspend fun uploadAndAddMemberDocument(
        memberId: String,
        groupId: String,
        label: String,
        byteArray: ByteArray,
        fileName: String,
        documentType: String?
    ): Result<MemberDocument> = runCatching {
        val timestamp = System.currentTimeMillis()
        val ext = fileName.substringAfterLast(".", "pdf")
        val path = "members/$memberId/relational_${label.filter { it.isLetterOrDigit() }}_$timestamp.$ext"
        
        val uploadedPath = storageRepo.uploadFile("documents", path, byteArray).getOrThrow()
        val publicUrl = storageRepo.getPublicUrl("documents", uploadedPath)
        
        val doc = MemberDocument(
            memberId = memberId,
            groupId = groupId,
            label = label,
            documentUrl = publicUrl,
            documentType = documentType ?: ext,
            status = DocumentStatus.PENDING
        )
        
        addMemberDocument(doc).getOrThrow()
    }

    override fun getGroupContributions(groupId: String): Flow<Result<List<Contribution>>> = observeAndSync(
        dbFlow = db.contributionDao().observeGroupContributions(groupId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["contributions"].select(columns = Columns.raw(CONTRIBUTION_COLUMNS_SAFE)) {
                filter { eq("group_id", groupId) }
            }.decodeList<Contribution>()
        },
        cacheSync = { list -> db.contributionDao().syncGroupContributions(groupId, list) }
    )

    override fun getMemberContributions(memberId: String, groupId: String): Flow<Result<List<Contribution>>> = observeAndSync(
        dbFlow = db.contributionDao().observeContributions(memberId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["contributions"].select(columns = Columns.raw(CONTRIBUTION_COLUMNS_SAFE)) {
                filter {
                    eq("member_id", memberId)
                    eq("group_id", groupId)
                }
            }.decodeList<Contribution>()
        },
        cacheSync = { list -> db.contributionDao().syncMemberContributions(memberId, list) }
    )

    override fun observeBeneficiaries(memberId: String): Flow<Result<List<Beneficiary>>> = observeAndSync(
        dbFlow = db.beneficiaryDao().observeBeneficiaries(memberId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["beneficiaries"].select {
                filter { eq("member_id", memberId) }
            }.decodeList<Beneficiary>()
        },
        cacheSync = { list -> db.beneficiaryDao().syncBeneficiaries(memberId, list) }
    )

    override suspend fun addBeneficiary(beneficiary: Beneficiary): Result<Beneficiary> = runCatching {
        // Validation: Max beneficiaries check
        val group = groupRepo.getGroupById(beneficiary.groupId).getOrThrow()
        val currentCount = supabase.postgrest["beneficiaries"].select {
            filter { 
                eq("member_id", beneficiary.memberId)
                eq("group_id", beneficiary.groupId)
            }
        }.decodeList<Beneficiary>().size
        
        val maxBeneficiaries = group.maxBeneficiaries ?: 0
        if (maxBeneficiaries > 0 && currentCount >= maxBeneficiaries) {
            throw Exception("Maximum beneficiary limit of $maxBeneficiaries reached.")
        }

        // Logic for isOver65 based on DOB
        val isOver65 = try {
            val dobString = beneficiary.dateOfBirth
            if (!dobString.isNullOrBlank()) {
                val dob = java.time.LocalDate.parse(dobString)
                val today = java.time.LocalDate.now()
                var age = today.year - dob.year
                if (today.monthValue < dob.monthValue || (today.monthValue == dob.monthValue && today.dayOfMonth < dob.dayOfMonth)) {
                    age--
                }
                age >= 65
            } else false
        } catch (e: Exception) {
            false
        }

        val finalBeneficiary = beneficiary.copy(isOver65 = isOver65)
        val inserted = supabase.postgrest["beneficiaries"].insert(finalBeneficiary) { select() }.decodeSingle<Beneficiary>()
        db.beneficiaryDao().upsertBeneficiary(inserted.toEntity())
        
        // Triggers in Supabase will handle member.beneficiary_count/over65 sync
        // Sync local member cache
        getMemberById(beneficiary.memberId)
        
        inserted
    }

    override suspend fun updateBeneficiary(beneficiary: Beneficiary): Result<Beneficiary> = runCatching {
        val updateData = buildJsonObject {
            put("full_name", beneficiary.fullName)
            beneficiary.idNumber?.let { put("id_number", it) }
            beneficiary.relationship?.let { put("relationship", it) }
            beneficiary.dateOfBirth?.let { put("date_of_birth", it) }
            put("is_over_65", beneficiary.isOver65)
        }
        val updated = supabase.postgrest["beneficiaries"].update(updateData) {
            filter {
                eq("group_id", beneficiary.groupId)
                eq("member_id", beneficiary.memberId)
                eq("id", beneficiary.id ?: "")
            }
            select()
        }.decodeSingle<Beneficiary>()
        
        db.beneficiaryDao().upsertBeneficiary(updated.toEntity())
        getMemberById(beneficiary.memberId)
        updated
    }

    override suspend fun deleteBeneficiary(groupId: String, memberId: String, id: String): Result<Unit> = runCatching {
        // 1. Get beneficiary to find document URL for storage cleanup
        val beneficiary = db.beneficiaryDao().getBeneficiaryById(id)

        // 2. Delete from remote
        supabase.postgrest["beneficiaries"].delete {
            filter {
                eq("group_id", groupId)
                eq("member_id", memberId)
                eq("id", id)
            }
        }

        // 3. Delete from local
        db.beneficiaryDao().deleteBeneficiary(groupId, memberId, id)

        // 4. Cleanup storage if document exists
        beneficiary?.documentUrl?.let { url ->
            val path = extractStoragePath(url)
            if (path != null) {
                storageRepo.deleteFile("documents", path)
            }
        }

        getMemberById(memberId)
        Unit
    }

    private fun extractStoragePath(url: String): String? {
        return try {
            // Pattern for authenticated URLs: .../authenticated/documents/members/ID/file.ext
            // or beneficiaries/...
            val marker = "/documents/"
            if (url.contains(marker)) {
                url.substringAfter(marker).substringBefore("?")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun syncBeneficiaries(memberId: String): Result<List<Beneficiary>> = runCatching {
        val list = supabase.postgrest["beneficiaries"].select {
            filter { eq("member_id", memberId) }
        }.decodeList<Beneficiary>()
        db.beneficiaryDao().upsertBeneficiaries(list.map { it.toEntity() })
        list
    }

    override suspend fun uploadBeneficiaryDocument(
        beneficiaryId: String,
        groupId: String,
        memberId: String,
        byteArray: ByteArray,
        fileName: String
    ): Result<String> = runCatching {
        val ext = fileName.substringAfterLast(".", "pdf")
        val timestamp = System.currentTimeMillis()
        // Use a unique path to avoid stale content caching in Coil/UI.
        val path = "beneficiaries/$memberId/${beneficiaryId}_$timestamp.$ext"
        
        // Upload to storage
        val uploadedPath = storageRepo.uploadFile("documents", path, byteArray).getOrThrow()
        val publicUrl = storageRepo.getPublicUrl("documents", uploadedPath)
        
        // Update beneficiary record with document URL
        val updateData = buildJsonObject {
            put("document_url", publicUrl)
            put("document_status", "pending")
        }
        
        supabase.postgrest["beneficiaries"].update(updateData) {
            filter {
                eq("id", beneficiaryId)
                eq("group_id", groupId)
                eq("member_id", memberId)
            }
        }
        
        // Sync local cache
        syncBeneficiaries(memberId)
        
        publicUrl
    }

    override fun observeMemberDocuments(memberId: String): Flow<Result<List<MemberDocument>>> = observeAndSync(
        dbFlow = db.memberDocumentDao().observeDocuments(memberId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["member_documents"].select {
                filter { eq("member_id", memberId) }
            }.decodeList<MemberDocument>()
        },
        cacheSync = { list -> db.memberDocumentDao().syncDocuments(memberId, list) }
    )

    override suspend fun syncMemberDocuments(memberId: String): Result<List<MemberDocument>> = runCatching {
        val list = supabase.postgrest["member_documents"].select {
            filter { eq("member_id", memberId) }
        }.decodeList<MemberDocument>()
        db.memberDocumentDao().syncDocuments(memberId, list.map { it.toEntity() })
        list
    }

    override suspend fun addMemberDocument(document: MemberDocument): Result<MemberDocument> = runCatching {
        // Use upsert to handle re-uploading documents with the same label
        // The unique constraint is on (member_id, label)
        val inserted = supabase.postgrest["member_documents"].upsert(document) {
            onConflict = "member_id,label"
            select()
        }.decodeSingle<MemberDocument>()
        db.memberDocumentDao().upsertDocument(inserted.toEntity())
        inserted
    }

    override suspend fun updateMemberDocument(document: MemberDocument): Result<MemberDocument> = runCatching {
        val updated = supabase.postgrest["member_documents"].update(document) {
            filter { eq("id", document.id ?: "") }
            select()
        }.decodeSingle<MemberDocument>()
        db.memberDocumentDao().upsertDocument(updated.toEntity())
        updated
    }

}
