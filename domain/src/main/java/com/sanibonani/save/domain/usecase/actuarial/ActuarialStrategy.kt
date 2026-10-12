package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.*

/**
 * Modern strategy interface for group-type specific actuarial logic.
 */
interface ActuarialStrategy {
    fun calculateMetrics(
        engine: GroupTypeActuarialEngine,
        group: Group,
        members: List<Member>,
        stats: ActuarialGroupStats,
    ): ActuarialMetrics

    fun computeInsight(
        engine: GroupTypeActuarialEngine,
        group: Group,
        members: List<Member>,
        stats: ActuarialGroupStats,
        projections: List<FutureMonthProjection>,
        benchmark: IndustryBenchmark
    ): GroupFinancialInsight

    fun calculateViabilityPlan(
        group: Group,
        members: List<Member>,
        goalAmount: Money,
        periodMonths: Int,
        actuarialSettings: ActuarialSettings = ActuarialSettings()
    ): ViabilityPlan

    fun getIndustryBenchmark(
        group: Group,
        memberCount: Int,
        balance: Money
    ): IndustryBenchmark

    // Legacy support for older engine methods
    fun computeMetricsLegacyInternal(
        engine: GroupTypeActuarialEngine,
        group: Group,
        members: List<Member>,
        monthsActive: Int,
        totalExpected: Money
    ): Any
}

/**
 * Base implementation providing shared logic for all actuarial strategies.
 */
abstract class BaseActuarialStrategy : ActuarialStrategy {

    override fun calculateViabilityPlan(
        group: Group,
        members: List<Member>,
        goalAmount: Money,
        periodMonths: Int,
        actuarialSettings: ActuarialSettings
    ): ViabilityPlan {
        return ActuarialMathUtils.calculatePlanLogic(group, members, goalAmount, periodMonths, actuarialSettings)
    }

    protected fun commonRiskLevel(score: Int): RiskLevel = when {
        score >= 80 -> RiskLevel.LOW
        score >= 55 -> RiskLevel.MODERATE
        score >= 30 -> RiskLevel.HIGH
        else -> RiskLevel.CRITICAL
    }

    protected fun r(v: Double): Double = ActuarialMath.round(v)
}
