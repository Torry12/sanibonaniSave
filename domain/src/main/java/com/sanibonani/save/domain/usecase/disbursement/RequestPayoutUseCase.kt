package com.sanibonani.save.domain.usecase.disbursement
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.service.PayoutGuard
import com.sanibonani.save.domain.service.GroupFinancialPolicy
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
    private val policy: GroupFinancialPolicy,
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

        val (member, roster) = if (memberId == null) {
            null to emptyList()
        } else {
            // One roster read serves both the bank details and the STOKVEL pool. Syncing twice in
            // one request doubles the round-trips and lets the two reads disagree about who is in
            // the group, so the amount could be calculated against a different roster than the
            // member being paid.
            val roster = memberRepository.syncGroupMembers(groupId).getOrNull() ?: emptyList()
            val member = roster.find { it.id == memberId } ?: throw Exception("Member not found.")
            member to roster
        }

        submitPayout(
            groupId = groupId,
            group = group,
            member = member,
            roster = roster,
            requestedAmount = requestedAmount,
            bankNameOverride = bankNameOverride,
            accountNoOverride = accountNoOverride,
            branchCodeOverride = branchCodeOverride
        )
    }

    // Guards and submits one payout request from context the caller already holds, so the roster
    // read once here is never re-fetched while the request is being valued.
    private suspend fun submitPayout(
        groupId: String,
        group: Group,
        member: Member?,
        roster: List<Member>,
        requestedAmount: Money?,
        bankNameOverride: String?,
        accountNoOverride: String?,
        branchCodeOverride: String?
    ): String {
        val bankName = bankNameOverride.ifBlankOrNull {
            if (member != null) member.bankName else group.bankName
        }
        val accountNo = accountNoOverride.ifBlankOrNull {
            if (member != null) member.accountNo else group.accountNumber
        }
        val branchCode = branchCodeOverride.ifBlankOrNull {
            if (member != null) member.branchCode else group.branchCode
        }
        val amountToPay = resolveAmount(group, member, roster, requestedAmount)
            ?.takeIf { it.isPositive }
            ?: throw Exception("Amount required.")

        val payoutType = policy.payoutTypeFor(group)

        // Apply Guards
        payoutGuard.validateRequest(group, member, amountToPay, payoutType).getOrThrow()

        val bankDetails = when (val check = validateBankDetails(bankName, accountNo, branchCode)) {
            is BankDetailsResult.Valid -> check.details
            is BankDetailsResult.Invalid -> throw Exception(
                when {
                    check.field != BankDetailsField.BANK_NAME -> check.reason
                    member != null -> "Member banking details are required for a member payout."
                    else -> "Bank name required."
                }
            )
        }

        val request = PayoutRequest(
            groupId = groupId,
            memberId = member?.id,
            amount = amountToPay,
            bankName = bankDetails.bankName,
            accountNo = bankDetails.accountNo,
            branchCode = bankDetails.branchCode,
            status = PayoutStatus.PENDING,
            type = payoutType,
            metadata = null
        )
        return payoutRepository.requestPayout(request).getOrThrow()
    }

    private suspend fun resolveAmount(
        group: Group,
        member: Member?,
        roster: List<Member>,
        requestedAmount: Money?
    ): Money? {
        if (requestedAmount?.isPositive == true) return requestedAmount
        return when {
            member == null -> requestedAmount
            group.type == GroupType.STOKVEL -> calculateStokvelPayoutsUseCase(group, roster, member)
            group.type == GroupType.INVESTMENT_CLUB -> calculateInvestmentClubValuationUseCase(
                group,
                roster
            ).getOrNull()?.memberValuations?.firstOrNull { it.memberId == member.id }?.marketValue
                ?: Money.ZERO
            else -> requestedAmount
        }
    }

    private inline fun String?.ifBlankOrNull(default: () -> String?): String? =
        if (isNullOrBlank()) default() else this
}
