package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.usecase.rosca.ExecuteRoscaPayoutUseCase
import com.sanibonani.save.domain.logging.AppLogger
import java.time.LocalDate
import javax.inject.Inject

/**
 * Central orchestrator for automated financial governance.
 * Handles auto-approval of eligible loans, automated burial claim payouts,
 * and ROSCA rotation executions.
 */
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
    private val tag = "AutomateGovernance"
    private val SYSTEM_ACTOR = "SYSTEM_AUTOMATION"

    suspend operator fun invoke(): Result<Unit> = runCatching {
        AppLogger.i(tag, "🚀 Starting automated governance cycle...")

        val groups = groupRepository.getAllGroups().getOrThrow()
        val today = LocalDate.now()

        // 1. Process Escalated Burial Claims
        processAutomatedBurialClaims()

        // 2. Process Pending Loan Requests
        processAutomatedLoanApprovals(groups)

        // 3. Process Group-Specific Payouts (ROSCA, Investment, Stokvel)
        for (group in groups) {
            val groupId = group.id ?: continue

            when (group.type) {
                GroupType.ROSCA -> {
                    // ExecuteRoscaPayoutUseCase handles its own internal timing and eligibility
                    executeRoscaPayoutUseCase(groupId).onFailure { e ->
                        AppLogger.w(tag, "ROSCA auto-payout skipped for $groupId: ${e.message}")
                    }
                }
                GroupType.INVESTMENT_CLUB -> {
                    if (group.investmentPayoutDate == today.toString()) {
                        automateScheduledPayouts(group)
                    }
                }
                GroupType.STOKVEL -> {
                    // Typically year-end, but we check if today is a scheduled payout day if configured
                    // (Assuming standard 12-month cycle if no date set)
                    if (group.investmentPayoutDate == today.toString()) {
                        automateScheduledPayouts(group)
                    }
                }
                else -> { /* No automated periodic payouts for other types yet */ }
            }
        }

        AppLogger.i(tag, "✅ Automated governance cycle completed.")
    }

    private suspend fun processAutomatedBurialClaims() {
        val escalatedClaims = claimRepository.getEscalatedClaimsSync().getOrDefault(emptyList())
        for (claim in escalatedClaims) {
            val claimId = claim.id ?: continue
            runCatching {
                val group = groupRepository.getGroupById(claim.groupId).getOrThrow()
                val allowedAmount = group.goalAmount.takeIf { it.isPositive } ?: Money(30000.0) // Default SA benchmark

                // Auto-pay if amount is within standard group benefit
                if (claim.claimAmount <= allowedAmount) {
                    AppLogger.i(tag, "🤖 Auto-paying burial claim $claimId for ${claim.beneficiaryName}")
                    processBurialClaimUseCase(
                        claimId = claimId,
                        status = BeneficiaryClaimStatus.PAID,
                        reviewedBy = SYSTEM_ACTOR,
                        adminNotes = "Automated payout: amount within group benefit threshold."
                    ).getOrThrow()
                }
            }.onFailure { e ->
                AppLogger.e(tag, "Failed to auto-process burial claim $claimId", e)
            }
        }
    }

    private suspend fun processAutomatedLoanApprovals(groups: List<Group>) {
        val pendingLoans = loanRepository.getAllPendingLoansSync().getOrDefault(emptyList())
        for (loan in pendingLoans) {
            val loanId = loan.id ?: continue
            runCatching {
                val group = groups.find { it.id == loan.groupId } ?: groupRepository.getGroupById(loan.groupId).getOrThrow()
                val member = memberRepository.getMemberById(loan.memberId).getOrThrow()

                val eligibility = validateLoanEligibilityUseCase(
                    member = member,
                    group = group,
                    requestedAmount = loan.amount,
                    termMonths = 3 // Max standard term
                )

                if (eligibility is ValidateLoanEligibilityUseCase.EligibilityResult.Eligible) {
                    AppLogger.i(tag, "🤖 Auto-approving loan $loanId for ${member.fullName}")
                    loanRepository.approveLoan(loanId).getOrThrow()

                    auditLogRepository.logEvent(AuditLog(
                        actorId = SYSTEM_ACTOR,
                        targetMemberId = loan.memberId,
                        targetGroupId = loan.groupId,
                        action = "LOAN_AUTO_APPROVED",
                        details = kotlinx.serialization.json.buildJsonObject {
                            put("loan_id", loanId)
                            put("amount", loan.amount.amount.toPlainString())
                            put("reason", "Passed all compliance and eligibility checks.")
                        }
                    ))
                }
            }.onFailure { e ->
                AppLogger.e(tag, "Failed to auto-approve loan $loanId", e)
            }
        }
    }

    private suspend fun automateScheduledPayouts(group: Group) {
        val groupId = group.id ?: return
        val members = memberRepository.syncGroupMembers(groupId).getOrDefault(emptyList())
        val activeMembers = members.filter { it.status == MemberStatus.ACTIVE }

        AppLogger.i(tag, "🤖 Processing scheduled payouts for group ${group.name} (${activeMembers.size} members)")

        for (member in activeMembers) {
            runCatching {
                requestPayoutUseCase(
                    groupId = groupId,
                    memberId = member.id
                ).getOrThrow()
            }.onFailure { e ->
                AppLogger.e(tag, "Scheduled payout failed for member ${member.id} in group $groupId: ${e.message}")
            }
        }
    }
}
