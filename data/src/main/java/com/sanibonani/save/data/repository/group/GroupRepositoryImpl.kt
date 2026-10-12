package com.sanibonani.save.data.repository.group

import com.sanibonani.save.data.datasource.*
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.LedgerEntryCreatedEvent
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.service.FileStorageService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject

class GroupRepositoryImpl @Inject constructor(
    private val remoteGroupSource: RemoteGroupDataSource,
    private val localGroupSource: LocalGroupDataSource,
    private val fileStorageService: FileStorageService,
    private val realtimeSource: RealtimeDataSource,
    private val supabaseRepo: com.sanibonani.save.domain.repository.auth.SupabaseRepository,
    private val json: Json
) : BaseRepository("GroupRepository"), GroupRepository {

    override suspend fun uploadConstitution(groupId: String, fileBytes: ByteArray, fileName: String): Result<String> = runCatching {
        val extension = fileName.substringAfterLast(".", "pdf")
        val url = fileStorageService.uploadGroupConstitution(groupId, fileBytes, extension).getOrThrow()

        remoteGroupSource.updateGroupSettings(groupId, mapOf("constitution_url" to url, "constitution_status" to "pending")).getOrThrow()
        localGroupSource.getGroupById(groupId)?.let {
            localGroupSource.saveGroup(it.copy(constitutionUrl = url, constitutionStatus = DocumentStatus.PENDING))
        }
        url
    }

    override fun getPublicGroups(): Flow<Result<List<Group>>> = observeAndSync(
        dbFlow = localGroupSource.observePublicGroups(),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteGroupSource.getPublicGroups().getOrThrow() },
        cacheSync = { localGroupSource.saveGroups(it) }
    )

    override fun getPublicGroupsFromCache(): Flow<Result<List<Group>>> =
        localGroupSource.observePublicGroups().map { Result.success(it) }

    override suspend fun getGroupById(id: String): Result<Group> = runCatching {
        remoteGroupSource.getGroupById(id).getOrElse {
            localGroupSource.getGroupById(id) ?: throw it
        }
    }

    override fun observeGroup(groupId: String): Flow<Result<Group?>> = observeAndSyncItem(
        dbFlow = localGroupSource.observeGroupById(groupId),
        mapper = { it },
        toEntity = { it!! },
        networkFetch = { remoteGroupSource.getGroupById(groupId).getOrThrow() },
        cacheSync = { localGroupSource.saveGroup(it) }
    )

    override fun observeGroupsByAdmin(adminId: String): Flow<Result<List<Group>>> = observeAndSync(
        dbFlow = localGroupSource.observeGroupsByAdmin(adminId),
        mapper = { it },
        toEntity = { it },
        networkFetch = { remoteGroupSource.getGroupsByAdmin(adminId).getOrThrow() },
        cacheSync = { localGroupSource.saveGroups(it) }
    )

    override suspend fun createGroup(group: Group): Result<String> = runCatching {
        val groupId = remoteGroupSource.createGroup(group).getOrThrow()
        val created = remoteGroupSource.getGroupById(groupId).getOrThrow()
        localGroupSource.saveGroup(created)
        groupId
    }

    override suspend fun incrementGroupBalance(groupId: String, amount: Money): Result<Money> = authoritativeCall("increment_group_balance") {
        require(groupId.isNotBlank()) { "Group id is required" }
        require(!amount.isZero) { "Balance adjustment must be non-zero" }
        val newBalance = remoteGroupSource.incrementGroupBalance(groupId, amount, null).getOrThrow()
        localGroupSource.getGroupById(groupId)?.let {
            localGroupSource.saveGroup(it.copy(balance = newBalance))
        }
        newBalance
    }

    override suspend fun recordDisbursement(groupId: String, amount: Money, description: String, category: String, transactionId: String?): Result<Money> = authoritativeCall("record_disbursement") {
        require(groupId.isNotBlank()) { "Group id is required" }
        require(amount.isPositive) { "Disbursement amount must be positive" }
        require(description.isNotBlank()) { "Disbursement description is required" }
        require(category.isNotBlank()) { "Disbursement category is required" }
        val newBalance = remoteGroupSource.recordDisbursement(groupId, amount, description, category, transactionId, memberId = null).getOrThrow()
        localGroupSource.getGroupById(groupId)?.let {
            localGroupSource.saveGroup(it.copy(balance = newBalance))
        }
        newBalance
    }

    override suspend fun updateGroupSettings(groupId: String, settings: GroupSettings): Result<Unit> = runCatching {
        remoteGroupSource.updateGroupSettings(groupId, settings).getOrThrow()
        val updated = remoteGroupSource.getGroupById(groupId).getOrThrow()
        localGroupSource.saveGroup(updated)
    }

    override suspend fun updateGroupSettings(groupId: String, settings: Map<String, Any>): Result<Unit> = runCatching {
        remoteGroupSource.updateGroupSettings(groupId, settings).getOrThrow()
        val updated = remoteGroupSource.getGroupById(groupId).getOrThrow()
        localGroupSource.saveGroup(updated)
    }

    override fun observeGroupFeeStatus(groupId: String): Flow<AdminFeeState> = realtimeSource.observeGroupFeeStatus(groupId)

    override suspend fun updateFeeStatus(groupId: String, status: AdminFeeState): Result<Unit> = runCatching {
        remoteGroupSource.updateFeeStatus(groupId, status).getOrThrow()
        localGroupSource.getGroupById(groupId)?.let {
            localGroupSource.saveGroup(it.copy(feeStatus = status))
        }
    }

    override suspend fun updateGroup(group: Group): Result<Unit> = runCatching {
        remoteGroupSource.updateGroup(group).getOrThrow()
        localGroupSource.saveGroup(group)
    }

    override suspend fun getCurrentUserEmail(): String? = supabaseRepo.currentSessionEmail

    override suspend fun getGroupsByAdmin(adminId: String): Result<List<Group>> = remoteGroupSource.getGroupsByAdmin(adminId)

    override suspend fun deleteGroup(groupId: String): Result<Unit> = runCatching {
        remoteGroupSource.deleteGroup(groupId).getOrThrow()
        localGroupSource.deleteGroup(groupId)
    }
}
