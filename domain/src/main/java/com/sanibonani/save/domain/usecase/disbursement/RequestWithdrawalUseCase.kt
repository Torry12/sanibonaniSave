package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PayoutStatus
import com.sanibonani.save.domain.model.PayoutType
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.service.PayoutGuard
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

        // Checked before the request is queued: the processor rejects blank or malformed details
        // after the treasury has already reserved the amount.
        val bankDetails = when (
            val check = validateBankDetails(
                params.member.bankName,
                params.member.accountNo,
                params.member.branchCode
            )
        ) {
            is BankDetailsResult.Valid -> check.details
            is BankDetailsResult.Invalid -> return Result.failure(
                IllegalStateException(
                    when (check.field) {
                        BankDetailsField.BANK_NAME ->
                            "Add your bank name before requesting a withdrawal."
                        BankDetailsField.ACCOUNT_NUMBER ->
                            "Add a valid account number before requesting a withdrawal."
                        BankDetailsField.BRANCH_CODE ->
                            "Add a valid 6-digit branch code before requesting a withdrawal."
                    }
                )
            )
        }

        payoutGuard.validateRequest(params.group, params.member, params.amount, params.type)
            .getOrElse { return Result.failure(it) }

        val request = PayoutRequest(
            groupId = groupId,
            memberId = memberId,
            amount = params.amount,
            type = params.type,
            status = PayoutStatus.PENDING,
            bankName = bankDetails.bankName,
            accountNo = bankDetails.accountNo,
            branchCode = bankDetails.branchCode
        )

        return payoutRepository.requestPayout(request).map { request }
    }
}
