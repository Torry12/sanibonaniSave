package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.repository.member.MemberRepository
import javax.inject.Inject

/**
 * Writes a member's payout account — bank, account number and branch code — as one change.
 */
class UpdateMemberBankingDetailsUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(
        memberId: String,
        bankName: String,
        accountNo: String,
        branchCode: String
    ): Result<Unit> = memberRepository.updateMemberBankingDetails(memberId, bankName, accountNo, branchCode)
}
