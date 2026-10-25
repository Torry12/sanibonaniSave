package com.sanibonani.save.domain.repository.admin

import com.sanibonani.save.domain.model.*

interface ActuarialRepository {
    suspend fun computeMetrics(groupId: String): Result<ActuarialMetrics>
    suspend fun calculateViabilityPlan(
        groupId: String,
        goalAmount: Money,
        periodMonths: Int,
        actuarialSettings: ActuarialSettings = ActuarialSettings()
    ): Result<ViabilityPlan>

    /**
     * Pure function to calculate generic actuarial metrics from group and member data.
     */
    fun calculateMetrics(group: Group, members: List<Member>): Result<ActuarialMetrics>

    /**
     * Computes comprehensive, group-type-specific financial insight using
     * industry-standard actuarial methods.
     */
    fun computeGroupInsight(group: Group, members: List<Member>): Result<GroupFinancialInsight>
}
