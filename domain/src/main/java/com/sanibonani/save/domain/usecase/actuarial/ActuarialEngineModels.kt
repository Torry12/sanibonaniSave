package com.sanibonani.save.domain.usecase.actuarial
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money

data class ActuarialGroupStats(
    val activeMemberCount: Int,
    val monthsActive: Int,
    val totalExpectedAnnualContributions: Money,
    val paymentRatePct: Double,
    val activeMembers: List<Member>
)

