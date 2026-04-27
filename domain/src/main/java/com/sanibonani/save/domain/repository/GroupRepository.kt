package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupSettings
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun getPublicGroups(): Flow<Result<List<Group>>>
    suspend fun getGroupById(id: String): Result<Group>
    fun observeGroup(groupId: String): Flow<Result<Group?>>
    fun observeGroupsByAdmin(adminId: String): Flow<Result<List<Group>>>
    suspend fun createGroup(
        group: Group
    ): Result<String>
    suspend fun updateGroupBalance(groupId: String, newBalance: Double): Result<Unit>
    suspend fun incrementGroupBalance(groupId: String, amount: Double): Result<Unit>
    suspend fun updateGroupSettings(groupId: String, settings: GroupSettings): Result<Unit>
    suspend fun updateGroupSettings(groupId: String, settings: Map<String, Any>): Result<Unit>
    fun observeGroupFeeStatus(groupId: String): Flow<AdminFeeState>
    suspend fun activateGroup(groupId: String, txId: String? = null): Result<Unit>
    suspend fun updateFeeStatus(groupId: String, status: AdminFeeState): Result<Unit>
    suspend fun uploadConstitution(groupId: String, fileBytes: ByteArray, fileName: String): Result<String>
    suspend fun updateGroup(group: Group): Result<Unit>
    suspend fun getCurrentUserEmail(): String?
    suspend fun getGroupsByAdmin(adminId: String): Result<List<Group>>
}
