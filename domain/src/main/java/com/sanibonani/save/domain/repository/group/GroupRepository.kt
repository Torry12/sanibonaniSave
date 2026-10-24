package com.sanibonani.save.domain.repository.group

import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupSettings
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun getPublicGroups(): Flow<Result<List<Group>>>
    fun getPublicGroupsFromCache(): Flow<Result<List<Group>>>
    suspend fun getGroupById(id: String): Result<Group>
    fun observeGroup(groupId: String): Flow<Result<Group?>>
    fun observeGroupsByAdmin(adminId: String): Flow<Result<List<Group>>>
    suspend fun createGroup(
        group: Group
    ): Result<String>
    /** Atomically updates group balance by adding the specified amount (can be negative). */
    suspend fun incrementGroupBalance(
        groupId: String,
        amount: com.sanibonani.save.domain.model.Money,
        transactionId: String? = null
    ): Result<com.sanibonani.save.domain.model.Money>

    /**
     * Atomically records a disbursement (outflow).
     * Decrements group balance and records a ledger entry.
     */
    suspend fun recordDisbursement(
        groupId: String,
        amount: com.sanibonani.save.domain.model.Money,
        description: String,
        category: String,
        transactionId: String? = null
    ): Result<com.sanibonani.save.domain.model.Money>
    suspend fun updateGroupSettings(groupId: String, settings: GroupSettings): Result<Unit>
    suspend fun updateGroupSettings(groupId: String, settings: Map<String, Any>): Result<Unit>
    fun observeGroupFeeStatus(groupId: String): Flow<AdminFeeState>
    suspend fun updateFeeStatus(groupId: String, status: AdminFeeState): Result<Unit>
    suspend fun uploadConstitution(groupId: String, fileBytes: ByteArray, fileName: String): Result<String>
    suspend fun deleteGroup(groupId: String): Result<Unit>
    suspend fun updateGroup(group: Group): Result<Unit>
    suspend fun getGroupsByAdmin(adminId: String): Result<List<Group>>
}
