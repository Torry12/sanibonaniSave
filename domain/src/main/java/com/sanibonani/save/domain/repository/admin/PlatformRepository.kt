package com.sanibonani.save.domain.repository.admin

import com.sanibonani.save.domain.model.*
import kotlin.Result

interface PlatformRepository {
    suspend fun getPlatformAnalytics(): Result<PlatformAnalytics>
    suspend fun getAllGroups(): Result<List<Group>>
    suspend fun updateGlobalFees(config: PlatformConfig): Result<Unit>
    suspend fun getPlatformSettings(): Result<PlatformConfig>
    suspend fun computeSystemWideMetrics(): Result<Unit>
    suspend fun triggerCloudBackup(): Result<Unit>
    suspend fun getCloudBackups(): Result<List<DatabaseBackup>>
    suspend fun deleteCloudBackup(name: String): Result<Unit>
    suspend fun repairGroupData(groupId: String): Result<Unit>
    suspend fun searchMembers(query: String): Result<List<MemberBehaviorInsight>>
    suspend fun exportLocalDatabase(useOnline: Boolean): Result<java.io.File>
    suspend fun getGranularFeeConfigs(): Result<List<PlatformFeeConfig>>
    suspend fun updateGranularFeeConfig(config: PlatformFeeConfig): Result<Unit>
    suspend fun unsuspendGroup(groupId: String): Result<Unit>
    suspend fun suspendGroup(groupId: String, reason: String): Result<Unit>
    suspend fun getGroupMetrics(groupId: String): Result<ActuarialMetrics>
    suspend fun broadcastPlatformMessage(message: String): Result<Unit>
    suspend fun getPlatformLedger(): Result<List<LedgerEntry>>
    suspend fun deleteGroupFromPlatform(groupId: String): Result<Unit>
}
