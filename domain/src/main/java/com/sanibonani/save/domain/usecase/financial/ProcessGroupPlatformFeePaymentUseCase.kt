package com.sanibonani.save.domain.usecase.financial

import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.financial.GroupFinancialRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
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
        val totalDue = Money(fees.sumOf { it.amountDue.toDouble() - it.amountPaid.toDouble() })
        if (!totalDue.isPositive) return Result.success(Unit)

        // 3. Verify group balance
        val group = groupRepository.getGroupById(groupId).getOrNull()
            ?: throw Exception("Group not found")

        if (group.balance < totalDue) {
            throw Exception("INSUFFICIENT_FUNDS: Group balance (${group.balance.format()}) is less than total due (${totalDue.format()})")
        }

        // 4. Process payment
        financialRepository.payPlatformFee(
            groupId = groupId,
            amount = totalDue,
            feeType = "monthly",
            txId = "PLATFORM-FEE-${System.currentTimeMillis()}"
        ).getOrThrow()
    }
}
