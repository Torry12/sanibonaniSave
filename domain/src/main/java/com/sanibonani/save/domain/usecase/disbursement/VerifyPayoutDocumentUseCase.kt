package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import javax.inject.Inject

/**
 * Accepts or rejects the supporting document on a payout request.
 */
class VerifyPayoutDocumentUseCase @Inject constructor(
    private val payoutRepository: PayoutRepository
) {
    suspend operator fun invoke(payoutId: String, approve: Boolean): Result<Unit> =
        payoutRepository.verifyPayoutDocument(
            payoutId = payoutId,
            status = if (approve) DocumentStatus.VERIFIED else DocumentStatus.REJECTED
        )
}
