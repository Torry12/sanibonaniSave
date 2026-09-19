package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money

object ViabilityUtils {

    fun computeDefaultGoal(group: Group?): Money {
        if (group == null) return Money(36000.0)
        if (group.goalAmount.isPositive) return group.goalAmount

        val members = group.currentMembers.coerceAtLeast(10)
        val monthly = if (group.monthlyContribution.isPositive) group.monthlyContribution else Money(500.0)

        return when (group.type) {
            GroupType.BURIAL_SOCIETY -> Money(30000.0)
            GroupType.INVESTMENT_CLUB -> Money(85000.0)
            GroupType.ROSCA -> monthly * members
            GroupType.EMERGENCY_FUND -> monthly * members * 6
            else -> (monthly * members * 12).max(Money(36000.0))
        }
    }

    fun computeDefaultPeriod(group: Group?): Int {
        val period = group?.periodMonths ?: 0
        return if (period > 0) period else 12
    }
}
