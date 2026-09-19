package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.PayoutProcessedEvent
import javax.inject.Inject

/**
 * Handles the logic for a Platform Admin processing a disbursement request.
 * Unifies various outflow types under a secure, MFA-protected workflow.
 */
class ProcessDisbursementUseCase @Inject constructor(
    private val payoutRepository: PayoutRepository,
    private val supabaseRepository: SupabaseRepository
) {
    companion object {
        private val MFA_THRESHOLD = Money(10000.0)
    }

    suspend operator fun invoke(
        payoutId: String,
        groupId: String,
        status: PayoutStatus,
        adminId: String,
        payoutReference: String? = null
    ): Result<Unit> = runCatching {
        // 1. Fetch record
        val payout = payoutRepository.getPayoutById(payoutId).getOrThrow()

        // 2. Security: MFA Check for large amounts
        if (status == PayoutStatus.COMPLETED && payout.amount >= MFA_THRESHOLD) {
            if (!supabaseRepository.isMfaVerified) {
                throw Exception("MFA_REQUIRED: High-value disbursement requires AAL2 verification.")
            }
        }

        // 3. Update status and balance atomically
        val result = if (status == PayoutStatus.COMPLETED) {
            payoutRepository.completePayoutAtomic(payoutId, adminId, payoutReference)
        } else {
            payoutRepository.updatePayoutStatus(payoutId, status, payoutReference)
        }

        if (result.isSuccess) {
            EventBus.emit(PayoutProcessedEvent(payout, status))
        }

        return result
    }
}
