package com.sanibonani.save.domain.usecase.disbursement
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.PayoutProcessedEvent
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.service.FinancialSecurityService
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import com.sanibonani.save.domain.usecase.financialResult

/**
 * Orchestrates the processing of a disbursement request (Payout).
 * Enforces strict state transitions, MFA security, and liquidity validation.
 */
class ProcessDisbursementUseCase @Inject constructor(
    private val payoutRepository: PayoutRepository,
    private val securityService: FinancialSecurityService
) {
    private val TAG = "ProcessDisbursement"

    suspend operator fun invoke(
        payoutId: String,
        groupId: String,
        status: PayoutStatus,
        adminId: String,
        payoutReference: String? = null,
        metadata: JsonObject? = null
    ): Result<PayoutRequest> = financialResult {
        AppLogger.d(TAG, "Processing payout $payoutId transition to $status by $adminId")

        // An empty actor id is what a dropped session hands over. Refusing it here keeps the audit
        // trail attributable and stops processed_by being stamped with a value the dual-control
        // gate would later read back as a match between two different admins.
        if (adminId.isBlank()) {
            throw IllegalStateException("A signed-in administrator is required to change a payout status.")
        }

        // 1. Fetch current record
        val payout = payoutRepository.getPayoutById(payoutId).getOrThrow()

        // 2. Validate state transition
        validateTransition(payout.status, status).getOrThrow()

        // 3. Security and Liquidity Check
        if (status in listOf(PayoutStatus.GROUP_APPROVED, PayoutStatus.PROCESSING, PayoutStatus.COMPLETED)) {
            // Calculate effective liquidity by subtracting already committed payouts. An
            // unreadable queue is not an empty one: treating it as empty would let a payout
            // pass the balance check while other approved payouts are still outstanding.
            val allPending = payoutRepository.getPendingPayouts().getOrThrow()
            val committedAmount = allPending.filter {
                it.groupId == groupId &&
                it.id != payoutId &&
                it.status in listOf(PayoutStatus.GROUP_APPROVED, PayoutStatus.PROCESSING)
            }.fold(Money.ZERO) { acc, p -> acc + p.amount }

            securityService.validateLiquidity(groupId, payout.amount + committedAmount).getOrThrow()
        }

        // MAKER-CHECKER DUAL CONTROL
        // One administrator cannot both prepare and pay out a high-value request alone.
        if (status == PayoutStatus.COMPLETED) {
            securityService.assertDualControl(payout.amount, adminId, payout.processedBy)
                .onFailure {
                    AppLogger.w(TAG, "Dual-control gate blocked admin $adminId on payout $payoutId (processor ${payout.processedBy}).")
                }
                .getOrThrow()
            securityService.assertMfaRequirement(payout.amount).getOrThrow()
        }

        // 4. Execute atomic update
        val updateResult = if (status == PayoutStatus.COMPLETED) {
            payoutRepository.completePayoutAtomic(payoutId, adminId, payoutReference)
        } else {
            payoutRepository.updatePayoutStatus(payoutId, status, payoutReference, metadata, adminId)
        }

        // 5. Fetch updated record
        val updated = payoutRepository.getPayoutById(payoutId).getOrThrow()

        // 6. Emit Event on Success
        updateResult.onSuccess {
            EventBus.emit(PayoutProcessedEvent(updated, status))
            AppLogger.i(TAG, "Payout $payoutId successfully moved to $status")
        }.onFailure {
            AppLogger.e(TAG, "Failed to update payout $payoutId: ${it.message}")
        }

        updateResult.getOrThrow()
        return@financialResult updated
    }

    private fun validateTransition(current: PayoutStatus, next: PayoutStatus): Result<Unit> {
        if (current == next) return Result.success(Unit)

        // Terminal states cannot be moved from
        if (current in listOf(PayoutStatus.COMPLETED, PayoutStatus.CANCELLED, PayoutStatus.REJECTED)) {
            return Result.failure(IllegalStateException("Cannot transition from terminal state $current"))
        }

        val allowed = when (current) {
            PayoutStatus.PENDING -> listOf(PayoutStatus.GROUP_APPROVED, PayoutStatus.CANCELLED, PayoutStatus.REJECTED)
            PayoutStatus.GROUP_APPROVED -> listOf(PayoutStatus.PROCESSING, PayoutStatus.CANCELLED, PayoutStatus.REJECTED)
            PayoutStatus.PROCESSING -> listOf(PayoutStatus.COMPLETED, PayoutStatus.FAILED, PayoutStatus.CANCELLED)
            PayoutStatus.FAILED -> listOf(PayoutStatus.PROCESSING, PayoutStatus.CANCELLED)
            else -> emptyList()
        }

        if (next !in allowed) {
            return Result.failure(IllegalStateException("Transition from $current to $next is not permitted"))
        }
        return Result.success(Unit)
    }


}
