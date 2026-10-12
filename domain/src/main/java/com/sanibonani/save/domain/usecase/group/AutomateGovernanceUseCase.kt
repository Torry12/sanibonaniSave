package com.sanibonani.save.domain.usecase.group
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.usecase.credit.ValidateLoanEligibilityUseCase
import com.sanibonani.save.domain.usecase.member.ProcessBurialClaimUseCase
import com.sanibonani.save.domain.usecase.disbursement.ExecuteRoscaPayoutUseCase
import com.sanibonani.save.domain.usecase.disbursement.RequestPayoutUseCase
import java.time.LocalDate
import javax.inject.Inject
class AutomateGovernanceUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val loanRepository: LoanRepository,
    private val platformRepository: PlatformRepository,
    private val claimRepository: BeneficiaryClaimRepository,
    private val validateLoanEligibilityUseCase: ValidateLoanEligibilityUseCase,
    private val processBurialClaimUseCase: ProcessBurialClaimUseCase,
    private val executeRoscaPayoutUseCase: ExecuteRoscaPayoutUseCase,
    private val requestPayoutUseCase: RequestPayoutUseCase,
    private val auditLogRepository: AuditLogRepository
) {
    suspend operator fun invoke(): Result<Unit> = runCatching {
        val groups = platformRepository.getAllGroups().getOrThrow()
        val today = LocalDate.now()
        for (group in groups) {
            val groupId = group.id ?: continue
            if (group.type == GroupType.ROSCA) {
                executeRoscaPayoutUseCase(groupId)
            }
        }
    }
}
