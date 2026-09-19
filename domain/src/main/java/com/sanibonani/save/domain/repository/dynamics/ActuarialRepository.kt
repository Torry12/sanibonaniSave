package com.sanibonani.save.domain.repository.dynamics

import com.sanibonani.save.domain.model.*

interface ActuarialRepository {
    suspend fun computeMetrics(groupId: String): Result<ActuarialMetrics>
    suspend fun calculateViabilityPlan(
        groupId: String,
        goalAmount: Money,
        periodMonths: Int
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
     * Calculates the adjusted monthly contribution for a specific member based on beneficiaries.
     */
    fun calculateMemberContribution(group: Group, member: Member): Money

    /**
     * Computes comprehensive, group-type-specific financial insight using
     * industry-standard actuarial methods.
     */
    fun computeGroupInsight(group: Group, members: List<Member>): Result<GroupFinancialInsight>

    /**
     * Async version: loads group + members then calls [computeGroupInsight].
     */
    suspend fun fetchGroupInsight(groupId: String): Result<GroupFinancialInsight>
}
