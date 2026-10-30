package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import javax.inject.Inject

/**
 * Payout requests still waiting on a platform decision.
 */
class GetPendingPayoutsUseCase @Inject constructor(
    private val payoutRepository: PayoutRepository
) {
    suspend operator fun invoke(): Result<List<PayoutRequest>> =
        payoutRepository.getPendingPayouts()
}
