package com.sanibonani.save.domain.usecase.dynamics

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.utils.PaymentCalculator
import javax.inject.Inject

class CalculateInvestmentClubValuationUseCase @Inject constructor() {
    operator fun invoke(group: Group, member: Member): Money {
        val totalPaid = member.totalPaid
        val share = PaymentCalculator.calculateMemberSharePercentage(group.balance, totalPaid)
        return group.balance * (share / 100.0)
    }
}
