package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.service.TreasuryService
import com.sanibonani.save.domain.usecase.rosca.ExecuteRoscaPayoutUseCase
import com.sanibonani.save.domain.usecase.stokvel.CalculateStokvelPayoutsUseCase
import com.sanibonani.save.domain.usecase.dynamics.CalculateInvestmentClubValuationUseCase
import javax.inject.Inject

/**
 * Handles the logic for a Group Admin requesting a disbursement from the platform.
 */
class RequestPayoutUseCase @Inject constructor(
    private val payoutRepository: PayoutRepository,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val treasuryService: TreasuryService,
    private val executeRoscaPayoutUseCase: ExecuteRoscaPayoutUseCase,
    private val calculateStokvelPayoutsUseCase: CalculateStokvelPayoutsUseCase,
    private val calculateInvestmentClubValuationUseCase: CalculateInvestmentClubValuationUseCase
) {
    suspend operator fun invoke(
        groupId: String,
        memberId: String? = null,
        requestedAmount: Money? = null,
        bankNameOverride: String? = null,
        accountNoOverride: String? = null,
        branchCodeOverride: String? = null
    ): Result<String> {
        val group = groupRepository.getGroupById(groupId).getOrNull()
            ?: return Result.failure(Exception("Group not found."))

        if (group.type == GroupType.ROSCA) {
            return executeRoscaPayoutUseCase(groupId)
        }

        var bankName: String? = bankNameOverride
        var accountNo: String? = accountNoOverride
        var branchCode: String? = branchCodeOverride
        var amountToPay = requestedAmount

        if (memberId != null) {
            val member = memberRepository.syncGroupMembers(groupId).getOrNull()?.find { it.id == memberId }
                ?: return Result.failure(Exception("Member not found."))

            if (bankName.isNullOrBlank()) bankName = member.bankName
            if (accountNo.isNullOrBlank()) accountNo = member.accountNo
            if (branchCode.isNullOrBlank()) branchCode = member.branchCode

            if (amountToPay == null || !amountToPay.isPositive) {
                if (group.type == GroupType.STOKVEL) {
                    val members = memberRepository.syncGroupMembers(groupId).getOrNull() ?: emptyList()
                    amountToPay = calculateStokvelPayoutsUseCase(group, members)
                } else if (group.type == GroupType.INVESTMENT_CLUB) {
                    amountToPay = calculateInvestmentClubValuationUseCase(group, member)
                }
            }
        } else {
            if (bankName.isNullOrBlank()) bankName = group.bankName
            if (accountNo.isNullOrBlank()) accountNo = group.accountNumber
            if (branchCode.isNullOrBlank()) branchCode = group.branchCode
        }

        if (amountToPay == null || !amountToPay.isPositive) {
            return Result.failure(Exception("Amount required."))
        }

        treasuryService.schedulePayout(groupId, amountToPay).onFailure { return Result.failure(it) }

        if (bankName.isNullOrBlank()) return Result.failure(Exception("Bank name required."))
        if (accountNo.isNullOrBlank() || !ACCOUNT_NO_REGEX.matches(accountNo.trim())) {
            return Result.failure(Exception("Invalid account number."))
        }
        if (branchCode.isNullOrBlank() || !BRANCH_CODE_REGEX.matches(branchCode.trim())) {
            return Result.failure(Exception("Invalid branch code."))
        }

        val request = PayoutRequest(
            groupId = groupId,
            memberId = memberId,
            amount = amountToPay,
            bankName = bankName.trim(),
            accountNo = accountNo.trim(),
            branchCode = branchCode.trim(),
            status = PayoutStatus.PENDING
        )

        return payoutRepository.requestPayout(request)
    }

    companion object {
        private val ACCOUNT_NO_REGEX = Regex("^\\d{7,13}$")
        private val BRANCH_CODE_REGEX = Regex("^\\d{6}$")
    }
}
