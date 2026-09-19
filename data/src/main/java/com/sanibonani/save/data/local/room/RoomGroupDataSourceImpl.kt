package com.sanibonani.save.data.local.room

import com.sanibonani.save.data.datasource.LocalGroupDataSource
import com.sanibonani.save.data.local.GroupDao
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.domain.model.Group
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomGroupDataSourceImpl @Inject constructor(
    private val groupDao: GroupDao
) : LocalGroupDataSource {

    override fun observeAllGroups(): Flow<List<Group>> =
        groupDao.observeAllGroups().map { list -> list.map { it.toModel() } }

    override fun observeGroupById(id: String): Flow<Group?> =
        groupDao.observeGroupById(id).map { it?.toModel() }

    override fun observePublicGroups(): Flow<List<Group>> =
        groupDao.observePublicGroups().map { list -> list.map { it.toModel() } }

    override fun observeGroupsByAdmin(adminId: String): Flow<List<Group>> =
        groupDao.observeGroupsByAdmin(adminId).map { list -> list.map { it.toModel() } }

    override suspend fun getGroupById(id: String): Group? =
        groupDao.getGroupById(id)?.toModel()

    override suspend fun saveGroups(groups: List<Group>) {
        groupDao.upsertGroups(groups.map { it.toEntity() })
    }

    override suspend fun saveGroup(group: Group) {
        groupDao.upsertGroup(group.toEntity())
    }

    override suspend fun clearAll() {
        groupDao.clearAll()
    }
}
