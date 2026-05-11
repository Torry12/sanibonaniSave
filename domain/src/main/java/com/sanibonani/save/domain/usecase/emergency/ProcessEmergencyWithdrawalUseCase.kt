package com.sanibonani.save.domain.usecase.emergency

import com.sanibonani.save.domain.model.*
import javax.inject.Inject

/**
 * Emergency Fund specific logic.
 * Validates and processes emergency fund withdrawal requests.
 */
class ProcessEmergencyWithdrawalUseCase @Inject constructor() {

    sealed class WithdrawalResult {
        data class Success(val amount: Double, val balanceRemaining: Double) : WithdrawalResult()
        data class Failure(val reason: String) : WithdrawalResult()
    }

    operator fun invoke(
        group: Group,
        member: Member,
        amount: Double,
        purpose: String
    ): WithdrawalResult {
        if (group.type != GroupType.EMERGENCY_FUND) {
            return WithdrawalResult.Failure("Group is not an Emergency Fund")
        }

        // 1. Check Member Standing
        if (member.status != MemberStatus.ACTIVE) {
            return WithdrawalResult.Failure("Only active members can request emergency withdrawals.")
        }

        // 2. Liquidity Check
        if (amount > group.balance) {
            return WithdrawalResult.Failure("Insufficient funds in group account. Requested: R$amount, Available: R${group.balance}")
        }

        // 3. Limit Check (Optional - e.g. cannot withdraw more than 50% of fund)
        val maxWithdrawal = group.balance * 0.5
        if (amount > maxWithdrawal) {
            return WithdrawalResult.Failure("Withdrawal exceeds single-transaction limit of 50% of the total fund (R$maxWithdrawal).")
        }

        // In a real implementation, we would call a repository to update the balance.
        // For this domain logic module, we just return the calculation.
        return WithdrawalResult.Success(
            amount = amount,
            balanceRemaining = group.balance - amount
        )
    }
}
