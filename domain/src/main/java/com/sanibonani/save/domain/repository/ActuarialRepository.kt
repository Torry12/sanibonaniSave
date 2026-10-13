package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.ActuarialMetrics
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupFinancialInsight
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
     * Pure function to calculate generic actuarial metrics from group and member data.
     */
    fun calculateMetrics(group: Group, members: List<Member>): Result<ActuarialMetrics>

    /**
     * Calculates the adjusted monthly contribution for a specific member based on beneficiaries.
     */
    fun calculateMemberContribution(group: Group, member: Member): Double

    /**
     * Computes comprehensive, group-type-specific financial insight using
     * industry-standard actuarial methods:
     *  - Burial Society : FSCA Friendly Societies Act / FSB prudential standards
     *  - Investment Club: JSE / NASAA NAV, CAGR, Sharpe ratio
     *  - ROSCA          : Besley-Coate-Loury rotation + default-risk model
     *  - Stokvel        : NASASA payout-projection & compliance benchmarks
     *  - Emergency Fund : SA Financial Planning 6-month coverage standard
     *  - Tontine        : Mortality-adjusted survivor-benefit model
     *  - Community Savings/Other: money-market growth + goal-tracking
     *
     * Pure (synchronous) – call inside a Dispatchers.Default coroutine if performance matters.
     */
    fun computeGroupInsight(group: Group, members: List<Member>): Result<GroupFinancialInsight>

    /**
     * Async version: loads group + members then calls [computeGroupInsight].
     */
    suspend fun fetchGroupInsight(groupId: String): Result<GroupFinancialInsight>
}
