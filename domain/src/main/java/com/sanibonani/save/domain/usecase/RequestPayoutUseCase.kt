package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.repository.PayoutRepository
import javax.inject.Inject

/**
 * Handles the logic for a Group Admin requesting a disbursement from the platform.
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
        if (amount <= 0) return Result.failure(Exception("Amount must be greater than zero"))
        
        val request = PayoutRequest(
            groupId = groupId,
            amount = amount,
            bankName = bankName,
            accountNo = accountNo,
            branchCode = branchCode,
            status = PayoutStatus.PENDING
        )
        
        return payoutRepository.requestPayout(request)
    }
}
