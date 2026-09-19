package com.sanibonani.save.domain.usecase.dynamics

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class CalculateStokvelPayoutsUseCase @Inject constructor() {
    operator fun invoke(group: Group, members: List<Member>): Money {
        val monthsRemaining = 12 - LocalDate.now().monthValue
        val expectedMonthlyTotal = members.size * group.monthlyContribution.toDouble()
        val projectedTotal = group.balance.toDouble() + (expectedMonthlyTotal * monthsRemaining)
        return Money(projectedTotal / members.size.coerceAtLeast(1))
    }
}
