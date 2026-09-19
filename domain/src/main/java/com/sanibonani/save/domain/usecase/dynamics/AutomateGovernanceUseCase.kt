package com.sanibonani.save.domain.usecase.dynamics

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.usecase.disbursement.ProcessBurialClaimUseCase
import com.sanibonani.save.domain.usecase.disbursement.RequestPayoutUseCase
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import kotlinx.serialization.json.put
import java.time.LocalDate
import javax.inject.Inject

class AutomateGovernanceUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val loanRepository: LoanRepository,
    private val claimRepository: BeneficiaryClaimRepository,
    private val validateLoanEligibilityUseCase: ValidateLoanEligibilityUseCase,
    private val processBurialClaimUseCase: ProcessBurialClaimUseCase,
    private val executeRoscaPayoutUseCase: ExecuteRoscaPayoutUseCase,
    private val requestPayoutUseCase: RequestPayoutUseCase,
    private val auditLogRepository: AuditLogRepository
) {
    suspend operator fun invoke(): Result<Unit> = runCatching {
        val groups = groupRepository.getAllGroups().getOrThrow()
        val today = LocalDate.now()

        for (group in groups) {
            val groupId = group.id ?: continue
            if (group.type == GroupType.ROSCA) {
                executeRoscaPayoutUseCase(groupId)
            }
        }
    }
}
