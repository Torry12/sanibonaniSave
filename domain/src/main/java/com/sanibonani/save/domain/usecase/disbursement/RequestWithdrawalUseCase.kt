package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.model.PayoutType
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.service.PayoutGuard
import com.sanibonani.save.domain.validation.InputValidator
import com.sanibonani.save.domain.validation.ValidationResult
import javax.inject.Inject

/**
 * Queues a member withdrawal after the same bank-detail and treasury guards the
 * admin payout flow applies.
 */
class RequestWithdrawalUseCase @Inject constructor(
    private val payoutRepository: PayoutRepository,
    private val payoutGuard: PayoutGuard
) {
    data class Params(
        val group: Group,
        val member: Member,
        val amount: Money,
        val type: PayoutType
    )

    suspend operator fun invoke(params: Params): Result<PayoutRequest> {
        val groupId = params.group.id
            ?: return Result.failure(IllegalStateException("No server id for group"))
        val memberId = params.member.id
            ?: return Result.failure(IllegalStateException("No server id for member"))

        val bankName = params.member.bankName.orEmpty().trim()
        val accountNo = params.member.accountNo.orEmpty().trim()
        val branchCode = params.member.branchCode.orEmpty().trim()

        // Checked before the request is queued: the processor rejects blank or malformed details
        // after the treasury has already reserved the amount.
        val detailsError = when {
            bankName.isEmpty() -> "Add your bank name before requesting a withdrawal."
            InputValidator.validateAccountNumber(accountNo, bankName) !is ValidationResult.Valid ->
                "Add a valid account number before requesting a withdrawal."
            branchCode.isEmpty() || InputValidator.validateBranchCode(branchCode) !is ValidationResult.Valid ->
                "Add a valid 6-digit branch code before requesting a withdrawal."
            else -> null
        }
        if (detailsError != null) return Result.failure(IllegalStateException(detailsError))

        payoutGuard.validateRequest(params.group, params.member, params.amount, params.type)
            .getOrElse { return Result.failure(it) }

        val request = PayoutRequest(
            groupId = groupId,
            memberId = memberId,
            amount = params.amount,
            type = params.type,
            status = PayoutStatus.PENDING,
            bankName = bankName,
            accountNo = accountNo,
            branchCode = branchCode
        )

        return payoutRepository.requestPayout(request).map { request }
    }
}
