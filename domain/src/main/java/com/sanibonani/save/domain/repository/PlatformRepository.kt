package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.model.ActuarialMetrics
import com.sanibonani.save.domain.model.PlatformAnalytics

interface PlatformRepository {
    suspend fun getPlatformAnalytics(): Result<PlatformAnalytics>
    suspend fun getAllGroups(): Result<List<Group>>
    suspend fun updateGlobalFees(memberCharge: Double, registrationFee: Double): Result<Unit>
    suspend fun getPlatformSettings(): Result<Map<String, Double>>
    suspend fun unsuspendGroup(groupId: String): Result<Unit>
    suspend fun suspendGroup(groupId: String, reason: String): Result<Unit>
    suspend fun getPlatformPayments(): Result<List<Payment>>
    suspend fun getGroupMetrics(groupId: String): Result<ActuarialMetrics>
}
