package com.sanibonani.save.domain.usecase.payment

import com.sanibonani.save.domain.model.PlatformFee
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import javax.inject.Inject

/**
 * What a group still owes the platform. The admin fee card and
 * [ProcessGroupPlatformFeePaymentUseCase] must read the same set, otherwise the amount shown and
 * the amount settled can disagree.
 */
class GetOutstandingPlatformFeesUseCase @Inject constructor(
    private val financialRepository: GroupFinancialRepository
) {
    suspend operator fun invoke(groupId: String): Result<List<PlatformFee>> =
        financialRepository.getOutstandingPlatformFees(groupId)
}
