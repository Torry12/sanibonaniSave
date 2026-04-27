package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.ActuarialMetrics
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.ViabilityPlan

interface ActuarialRepository {
    suspend fun computeMetrics(groupId: String): Result<ActuarialMetrics>
    suspend fun calculateViabilityPlan(
        groupId: String,
        goalAmount: Double,
        periodMonths: Int
    ): Result<ViabilityPlan>
    
    /**
     * Calculates a dynamic joining fee based on accumulated reserves to ensure
     * fairness between old and new members.
     */
    suspend fun calculateDynamicJoiningFee(groupId: String): Result<Double>

    /**
     * Pure function to calculate metrics from group and member data.
     */
    fun calculateMetrics(group: Group, members: List<Member>): ActuarialMetrics

    /**
     * Calculates the adjusted monthly contribution for a specific member based on beneficiaries.
     */
    fun calculateMemberContribution(group: Group, member: Member): Double
}
