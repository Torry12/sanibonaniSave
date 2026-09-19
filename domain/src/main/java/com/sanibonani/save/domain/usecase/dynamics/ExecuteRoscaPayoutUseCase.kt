package com.sanibonani.save.domain.usecase.dynamics

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.*
import javax.inject.Inject

class ExecuteRoscaPayoutUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val contributionRepository: ContributionRepository,
    private val payoutRepository: PayoutRepository,
    private val loanRepository: LoanRepository,
    private val behaviorTrackingRepository: BehaviorTrackingRepository,
    private val auditLogRepository: AuditLogRepository,
    private val roscaRotationUseCase: CalculateRoscaRotationUseCase
) {
    suspend operator fun invoke(groupId: String): Result<String> = runCatching {
        val group = groupRepository.getGroupById(groupId).getOrNull() ?: throw Exception("Group not found")
        if (group.type != GroupType.ROSCA) throw Exception("Not a ROSCA")

        val members = memberRepository.syncGroupMembers(groupId).getOrNull() ?: throw Exception("No members")
        val allContributions = contributionRepository.observeGroupContributions(groupId).firstOrNull()?.getOrNull() ?: emptyList()

        val schedule = roscaRotationUseCase(group, members).getOrThrow()
        val currentItems = schedule.subgroups.flatMap { sub -> sub.items.filter { it.isCurrent } }

        if (currentItems.isEmpty()) throw Exception("No current members")

        val results = mutableListOf<String>()

        for (currentItem in currentItems) {
            val currentMember = members.find { it.id == currentItem.memberId } ?: continue

            if (currentMember.status != MemberStatus.ACTIVE) {
                results.add("❌ Skipped ${currentMember.fullName}: Not active")
                continue
            }

            if (currentMember.bankName.isNullOrBlank() || currentMember.accountNo.isNullOrBlank()) {
                results.add("❌ Skipped ${currentMember.fullName}: No banking info")
                continue
            }

            val behaviorTrack = behaviorTrackingRepository.getMemberBehavior(currentMember.id ?: "").getOrNull()
            if (behaviorTrack?.totalOutstandingAmount?.isPositive == true) {
                results.add("❌ Skipped ${currentMember.fullName}: Has arrears")
                continue
            }

            val currentMonth = currentItem.payoutDate.substring(0, 7)
            val activeLoans = loanRepository.getMemberLoans(currentMember.id ?: "").firstOrNull()?.getOrNull()?.filter {
                it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID
            } ?: emptyList()

            val subgroup = schedule.subgroups.find { it.memberIds.contains(currentMember.id) } ?: continue
            val actualCollectedPot = allContributions.filter { it.memberId in subgroup.memberIds && it.dueDate.startsWith(currentMonth) && it.status == ContributionStatus.PAID }.fold(Money.ZERO) { acc, c -> acc + c.amount }

            if (!actualCollectedPot.isPositive) {
                results.add("❌ Skipped ${currentMember.fullName}: No contributions collected")
                continue
            }

            var remainingPot = actualCollectedPot
            val deductions = buildJsonArray {
                for (loan in activeLoans) {
                    if (!remainingPot.isPositive) break
                    val payAmount = loan.balanceRemaining.min(remainingPot)
                    addJsonObject {
                        put("loan_id", loan.id)
                        put("amount", payAmount.toDouble())
                    }
                    remainingPot -= payAmount
                }
            }

            payoutRepository.processRoscaPayout(
                groupId = groupId,
                memberId = currentMember.id ?: "",
                totalPot = actualCollectedPot,
                loanDeductions = deductions,
                payoutAmount = remainingPot,
                payoutData = buildJsonObject {
                    put("bank_name", currentMember.bankName)
                    put("account_no", currentMember.accountNo)
                    put("branch_code", currentMember.branchCode)
                }
            ).onSuccess { payoutId ->
                auditLogRepository.logEvent(AuditLog(actorId = "system_rosca", targetGroupId = groupId, targetMemberId = currentMember.id, action = "ROSCA_PAYOUT_EXECUTED", details = buildJsonObject { put("payoutId", payoutId); put("totalPot", actualCollectedPot.toDouble()); put("period", currentMonth) }))
            }.getOrThrow()

            results.add("✅ Created payout for ${currentMember.fullName} (Net: ${remainingPot.format()})")
        }

        results.joinToString("\n")
    }
}
