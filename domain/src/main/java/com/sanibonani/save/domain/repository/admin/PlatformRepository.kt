package com.sanibonani.save.domain.repository.admin

import com.sanibonani.save.domain.model.*
import kotlin.Result

interface PlatformRepository {
    /**
     * The platform-wide counters as the server answers them. A success means both reads behind it
     * landed: the counters come from the summary view, which alone holds platform revenue and the
     * average risk score, and the two charts from the group roster. A refusal is a failure and not a
     * platform of zeros, because the landing hero and the admin dashboard both render the error this
     * call reports, and none of these numbers may be estimated in code.
     */
    suspend fun getPlatformAnalytics(): Result<PlatformAnalytics>

    /**
     * Every group the server will show a platform admin. A success is the complete roster and is treated
     * as such: the governance, loan-settlement and broadcast paths walk this list once and report the
     * platform governed, so a refusal must be a failure here rather than an empty list. Nothing is
     * answered from the local cache, because cached rows cannot say which groups exist today.
     */
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
