package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupSettings
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.Money
import kotlinx.coroutines.flow.Flow

interface RemoteGroupDataSource {
    suspend fun getPublicGroups(): Result<List<Group>>
    suspend fun getGroupById(id: String): Result<Group>
    suspend fun getGroupsByAdmin(adminId: String): Result<List<Group>>
    suspend fun createGroup(group: Group): Result<String>
    suspend fun updateGroup(group: Group): Result<Unit>
    suspend fun updateGroupSettings(groupId: String, settings: GroupSettings): Result<Unit>
    suspend fun updateGroupSettings(groupId: String, settings: Map<String, Any>): Result<Unit>
    suspend fun activateGroup(groupId: String, txId: String?): Result<Unit>
    suspend fun updateFeeStatus(groupId: String, status: AdminFeeState): Result<Unit>
    suspend fun incrementGroupBalance(groupId: String, amount: Money, memberId: String? = null): Result<Money>
    suspend fun recordDisbursement(groupId: String, amount: Money, description: String, category: String, transactionId: String? = null, memberId: String? = null): Result<Money>
    suspend fun deleteGroup(groupId: String): Result<Unit>
}

interface LocalGroupDataSource {
    fun observeAllGroups(): Flow<List<Group>>
    fun observeGroupById(id: String): Flow<Group?>
    fun observePublicGroups(): Flow<List<Group>>
    fun observeGroupsByAdmin(adminId: String): Flow<List<Group>>
    suspend fun getGroupById(id: String): Group?
    suspend fun saveGroups(groups: List<Group>)
    suspend fun saveGroup(group: Group)
    suspend fun deleteGroup(groupId: String)
    suspend fun clearAll()
}
