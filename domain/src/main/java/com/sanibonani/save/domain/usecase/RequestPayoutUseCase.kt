package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.repository.PayoutRepository
import javax.inject.Inject

/**
 * Handles the logic for a Group Admin requesting a disbursement from the platform.
 *
 * Validates:
 *  - Amount > 0
 *  - SA bank account number (7–11 numeric digits)
 *  - SA branch code (6 numeric digits)
 *  - Bank name is not blank
 */
class RequestPayoutUseCase @Inject constructor(
    private val payoutRepository: PayoutRepository
) {
    suspend operator fun invoke(
        groupId: String,
        amount: Double,
        bankName: String,
        accountNo: String,
        branchCode: String
    ): Result<String> {
        if (amount <= 0) return Result.failure(Exception("Amount must be greater than zero."))
        if (bankName.isBlank()) return Result.failure(Exception("Bank name is required."))
        if (!ACCOUNT_NO_REGEX.matches(accountNo.trim())) {
            return Result.failure(Exception("Invalid SA bank account number. Must be 7–11 numeric digits."))
        }
        if (!BRANCH_CODE_REGEX.matches(branchCode.trim())) {
            return Result.failure(Exception("Invalid SA branch code. Must be exactly 6 numeric digits."))
        }

        val request = PayoutRequest(
            groupId = groupId,
            amount = amount,
            bankName = bankName.trim(),
            accountNo = accountNo.trim(),
            branchCode = branchCode.trim(),
            status = PayoutStatus.PENDING
        )

        return payoutRepository.requestPayout(request)
    }

    companion object {
        /** SA bank account numbers are 7–11 numeric digits. */
        private val ACCOUNT_NO_REGEX = Regex("^\\d{7,11}$")
        /** SA branch codes are exactly 6 numeric digits. */
        private val BRANCH_CODE_REGEX = Regex("^\\d{6}$")
    }
}
