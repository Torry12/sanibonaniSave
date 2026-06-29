package com.sanibonani.save.domain.usecase.emergency

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.LedgerRepository
import java.time.Instant
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
 * On success, a compensating ledger entry is recorded. Balance is derived from the ledger,
 * never mutated directly.
 */
class ProcessEmergencyWithdrawalUseCase @Inject constructor(
    private val ledgerRepository: LedgerRepository
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

        if (member.status != MemberStatus.ACTIVE) {
            return WithdrawalResult.Failure("Only active members can request emergency withdrawals.")
        }

        if (amount > group.balance) {
            return WithdrawalResult.Failure(
                "Insufficient funds in group account. Requested: R$amount, Available: R${group.balance}."
            )
        }

        val maxWithdrawal = group.balance * 0.5
        if (amount > maxWithdrawal) {
            return WithdrawalResult.Failure(
                "Withdrawal exceeds single-transaction limit of 50% of the total fund (R%.2f).".format(maxWithdrawal)
            )
        }

        // Record ledger entry — balance is derived from sum of all entries
        val entry = LedgerEntry(
            groupId = group.id ?: return WithdrawalResult.Failure("Group ID missing."),
            amount = -amount,
            balanceAfter = group.balance - amount,
            description = "Emergency Withdrawal: $purpose",
            category = "emergency_withdrawal",
            transactionId = "emergency_wd_${group.id}_${member.id}_${Instant.now().epochSecond}"
        )
        val result = ledgerRepository.logPlatformEvent(entry)
        if (result.isFailure) {
            return WithdrawalResult.Failure("Failed to record withdrawal. Please try again.")
        }

        return WithdrawalResult.Success(amount = amount, balanceRemaining = group.balance - amount)
    }
}
