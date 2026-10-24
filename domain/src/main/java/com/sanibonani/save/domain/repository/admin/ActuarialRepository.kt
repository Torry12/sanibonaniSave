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
     * Calculates a dynamic joining fee based on accumulated reserves to ensure
     * fairness between old and new members.
     */
    suspend fun calculateDynamicJoiningFee(groupId: String): Result<Money>

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
