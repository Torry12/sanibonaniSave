package com.sanibonani.save.domain.usecase.disbursement
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.service.PayoutGuard
import javax.inject.Inject
import com.sanibonani.save.domain.usecase.financialResult
/**
 * Handles the logic for a Group Admin requesting a disbursement from the platform.
 */
class RequestPayoutUseCase @Inject constructor(
    private val payoutRepository: PayoutRepository,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val payoutGuard: PayoutGuard,
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
    ): Result<String> = financialResult {
        val group = groupRepository.getGroupById(groupId).getOrNull()
            ?: throw Exception("Group not found.")

        if (group.type == GroupType.ROSCA) {
            return@financialResult executeRoscaPayoutUseCase(groupId).getOrThrow()
        }

        var bankName: String? = bankNameOverride
        var accountNo: String? = accountNoOverride
        var branchCode: String? = branchCodeOverride
        var amountToPay = requestedAmount
        var member: Member? = null

        if (memberId != null) {
            member = memberRepository.syncGroupMembers(groupId).getOrNull()?.find { it.id == memberId }
                ?: throw Exception("Member not found.")

            if (bankName.isNullOrBlank()) bankName = member.bankName
            if (accountNo.isNullOrBlank()) accountNo = member.accountNo
            if (branchCode.isNullOrBlank()) branchCode = member.branchCode

            if (amountToPay == null || !amountToPay.isPositive) {
                if (group.type == GroupType.STOKVEL) {
                    val members = memberRepository.syncGroupMembers(groupId).getOrNull() ?: emptyList()
                    amountToPay = calculateStokvelPayoutsUseCase(group, members, member)
                } else if (group.type == GroupType.INVESTMENT_CLUB) {
                    val valuation = calculateInvestmentClubValuationUseCase(group, listOf(member)).getOrNull()
                    amountToPay = valuation?.memberValuations?.firstOrNull()?.marketValue ?: Money.ZERO
                }
            }
        } else {
            if (bankName.isNullOrBlank()) bankName = group.bankName
            if (accountNo.isNullOrBlank()) accountNo = group.accountNumber
            if (branchCode.isNullOrBlank()) branchCode = group.branchCode
        }

        if (amountToPay == null || !amountToPay.isPositive) {
            throw Exception("Amount required.")
        }

        val payoutType = when (group.type) {
            GroupType.STOKVEL -> PayoutType.ANNUAL
            GroupType.EMERGENCY_FUND -> PayoutType.EMERGENCY
            GroupType.INVESTMENT_CLUB -> PayoutType.DISINVESTMENT
            else -> PayoutType.STANDARD
        }

        // Apply Guards
        payoutGuard.validateRequest(group, member, amountToPay, payoutType).getOrThrow()

        if (bankName.isNullOrBlank()) throw Exception("Bank name required.")
        if (accountNo.isNullOrBlank() || !ACCOUNT_NO_REGEX.matches(accountNo.trim())) {
            throw Exception("Invalid account number.")
        }
        if (branchCode.isNullOrBlank() || !BRANCH_CODE_REGEX.matches(branchCode.trim())) {
            throw Exception("Invalid branch code.")
        }

        val request = PayoutRequest(
            groupId = groupId,
            memberId = memberId,
            amount = amountToPay,
            bankName = bankName.trim(),
            accountNo = accountNo.trim(),
            branchCode = branchCode.trim(),
            status = PayoutStatus.PENDING,
            type = payoutType,
            metadata = null
        )
        payoutRepository.requestPayout(request).getOrThrow()
    }

    companion object {
        private val ACCOUNT_NO_REGEX = Regex("^\\d{7,13}$")
        private val BRANCH_CODE_REGEX = Regex("^\\d{6}$")
    }
}
