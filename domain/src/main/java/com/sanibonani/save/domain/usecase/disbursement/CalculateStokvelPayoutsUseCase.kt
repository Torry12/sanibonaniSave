package com.sanibonani.save.domain.usecase.disbursement
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import javax.inject.Inject
import com.sanibonani.save.domain.utils.PaymentCalculator

class CalculateStokvelPayoutsUseCase @Inject constructor(
    private val paymentCalculator: PaymentCalculator
) {
    operator fun invoke(group: Group, members: List<Member>, targetMember: Member? = null): Money {
        return paymentCalculator.projectPayout(group, members, targetMember)
    }
}
