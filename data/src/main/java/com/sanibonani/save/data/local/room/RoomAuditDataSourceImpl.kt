package com.sanibonani.save.data.local.room

import com.sanibonani.save.data.datasource.LocalAuditDataSource
import com.sanibonani.save.data.local.AuditDao
import com.sanibonani.save.data.local.AuditReportEntity
import com.sanibonani.save.data.local.SanibonaniDatabase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RoomAuditDataSourceImpl @Inject constructor(
    private val dbProvider: Provider<SanibonaniDatabase>
) : LocalAuditDataSource {
    private val auditDao: AuditDao get() = dbProvider.get().auditDao()
    override fun observeReport(id: String): Flow<AuditReportEntity?> = auditDao.observeReport(id)
    override suspend fun saveReport(entity: AuditReportEntity) = auditDao.upsertReport(entity)
    override suspend fun clearAll() = auditDao.clearAll()
}
