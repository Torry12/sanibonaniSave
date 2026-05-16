package com.sanibonani.save.domain.usecase.emergency

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.GroupRepository
import javax.inject.Inject

/**
 * Emergency Fund specific logic.
 * Validates and processes emergency fund withdrawal requests.
 *
 * Business rules:
 *  1. Group must be EMERGENCY_FUND type.
 *  2. Only ACTIVE members may withdraw.
 *  3. Requested amount must not exceed the current group balance.
 *  4. Single-transaction limit: max 50% of total fund.
 *
 * On success, the group balance is decremented via [GroupRepository].
 */
class ProcessEmergencyWithdrawalUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {

    sealed class WithdrawalResult {
        data class Success(val amount: Double, val balanceRemaining: Double) : WithdrawalResult()
        data class Failure(val reason: String) : WithdrawalResult()
    }

    suspend operator fun invoke(
        group: Group,
        member: Member,
        amount: Double,
        purpose: String
    ): WithdrawalResult {
        if (group.type != GroupType.EMERGENCY_FUND) {
            return WithdrawalResult.Failure("Group is not an Emergency Fund.")
        }

        // 1. Check Member Standing
        if (member.status != MemberStatus.ACTIVE) {
            return WithdrawalResult.Failure("Only active members can request emergency withdrawals.")
        }

        // 2. Liquidity Check
        if (amount > group.balance) {
            return WithdrawalResult.Failure(
                "Insufficient funds in group account. Requested: R$amount, Available: R${group.balance}."
            )
        }

        // 3. Single-transaction limit: max 50% of fund
        val maxWithdrawal = group.balance * 0.5
        if (amount > maxWithdrawal) {
            return WithdrawalResult.Failure(
                "Withdrawal exceeds single-transaction limit of 50% of the total fund (R%.2f).".format(maxWithdrawal)
            )
        }

        // 4. Persist the balance change (Atomic with Ledger)
        val updateResult = groupRepository.recordDisbursement(
            groupId = group.id ?: return WithdrawalResult.Failure("Group ID missing."),
            amount = amount,
            description = "Emergency Withdrawal: $purpose",
            category = "emergency_withdrawal"
        )
        if (updateResult.isFailure) {
            return WithdrawalResult.Failure("Failed to update group balance. Please try again.")
        }
        val newBalance = updateResult.getOrThrow()

        return WithdrawalResult.Success(amount = amount, balanceRemaining = newBalance)
    }
}
