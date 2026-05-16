package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.*
import kotlin.Result

interface PlatformRepository {
    suspend fun getPlatformAnalytics(): Result<PlatformAnalytics>
    suspend fun getAllGroups(): Result<List<Group>>
    suspend fun updateGlobalFees(
        memberCharge: Double,
        registrationFee: Double,
        payoutFee: Double,
        whatsappFee: Double,
        lateFeePercent: Double,
        autoSuspensionDays: Int
    ): Result<Unit>
    suspend fun getPlatformSettings(): Result<Map<String, Double>>
    suspend fun unsuspendGroup(groupId: String): Result<Unit>
    suspend fun suspendGroup(groupId: String, reason: String): Result<Unit>
    suspend fun getPlatformPayments(): Result<List<Payment>>
    suspend fun getGroupMetrics(groupId: String): Result<ActuarialMetrics>
    suspend fun logAuditEvent(auditLog: AuditLog): Result<Unit>
    suspend fun broadcastPlatformMessage(message: String): Result<Unit>
    suspend fun getAuditLogs(limit: Int = 50): Result<List<AuditLog>>
    suspend fun getPlatformLedger(): Result<List<LedgerEntry>>
    suspend fun getMemberBehaviorInsights(): Result<List<MemberBehaviorInsight>>
}
