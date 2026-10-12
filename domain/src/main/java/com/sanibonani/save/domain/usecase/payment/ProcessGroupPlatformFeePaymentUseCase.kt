package com.sanibonani.save.domain.usecase.payment
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.utils.OperationKeys
import javax.inject.Inject

/**
 * Orchestrates the payment of outstanding platform fees from a group's bank account.
 * This is triggered by a Group Admin.
 */
class ProcessGroupPlatformFeePaymentUseCase @Inject constructor(
    private val financialRepository: GroupFinancialRepository,
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String): Result<Unit> = runCatching {
        // 1. Fetch all outstanding fees
        val fees = financialRepository.getOutstandingPlatformFees(groupId).getOrThrow()
        if (fees.isEmpty()) return Result.success(Unit)

        // 2. Calculate total
        val totalDue = fees.fold(Money.ZERO) { acc, fee -> acc + (fee.amountDue - fee.amountPaid) }
        if (!totalDue.isPositive) return Result.success(Unit)

        // 3. Verify group balance
        val group = groupRepository.getGroupById(groupId).getOrNull()
            ?: throw Exception("Group not found")

        if (group.balance < totalDue) {
            throw Exception("INSUFFICIENT_FUNDS: Group balance (${group.balance.format()}) is less than total due (${totalDue.format()})")
        }

        // 4. Process payment.
        //
        // The txId MUST be deterministic: deriving it from wall-clock time meant a
        // retry after a network timeout produced a *different* txId, so the database
        // could not recognise the payment as a duplicate and the group was charged
        // twice. It is now keyed on the exact fee set being settled, so an identical
        // retry reuses the same txId and the payment function's idempotency guard
        // (see pay_platform_fee_v2) returns it as already applied.
        val feeFingerprint = fees
            .map { it.id ?: "${it.feeType}:${it.monthReference ?: it.dueDate.orEmpty()}:${it.amountDue.toPlainString()}" }
            .sorted()
        val txId = "PLATFORM-FEE-" + OperationKeys.stableSuffix(
            namespace = "platform_fee_payment",
            groupId,
            totalDue.toPlainString(),
            feeFingerprint.joinToString(",")
        )

        financialRepository.payPlatformFee(
            groupId = groupId,
            amount = totalDue,
            feeType = "monthly",
            txId = txId
        ).getOrThrow()
    }
}
