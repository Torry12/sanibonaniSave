package com.sanibonani.save.domain.usecase.disbursement
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.service.PayoutGuard
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.PayoutProcessedEvent
import kotlinx.serialization.json.*
import javax.inject.Inject
import com.sanibonani.save.domain.usecase.financialResult
import com.sanibonani.save.domain.usecase.firstValueOrNull
class ExecuteRoscaPayoutUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val contributionRepository: ContributionRepository,
    private val payoutRepository: PayoutRepository,
    private val loanRepository: LoanRepository,
    private val behaviorTrackingRepository: BehaviorTrackingRepository,
    private val platformRepository: PlatformRepository,
    private val roscaRotationUseCase: CalculateRoscaRotationUseCase,
    private val payoutGuard: PayoutGuard
) {
    suspend operator fun invoke(groupId: String): Result<String> = financialResult {
        val group = groupRepository.getGroupById(groupId).getOrNull() ?: throw Exception("Group not found")
        if (group.type != GroupType.ROSCA) throw Exception("Not a ROSCA")

        // Basic Group-Level Guard
        payoutGuard.validateRequest(group, null, Money.ZERO, PayoutType.ROTATION).getOrThrow()

        val members = memberRepository.syncGroupMembers(groupId).getOrNull() ?: throw Exception("No members")

        val allContributions = contributionRepository.observeGroupContributions(groupId).firstValueOrNull() ?: emptyList()
        // One loan fetch for the whole rotation instead of one per payoutee. Each per-member fetch
        // is a network round-trip that re-syncs the cache, so a 30-person ROSCA used to issue thirty
        // of them and could deduct against a balance that changed partway through the loop.
        val activeLoansByMember = loanRepository.getGroupLoansSync(groupId).getOrNull()
            ?.filter { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID }
            ?.groupBy { it.memberId }
            ?: emptyMap()
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

            // 1. Initial Member Eligibility Guard
            val individualGuard = payoutGuard.validateRequest(group, currentMember, Money.ZERO, PayoutType.ROTATION)
            if (individualGuard.isFailure) {
                results.add("❌ Skipped ${currentMember.fullName}: ${individualGuard.exceptionOrNull()?.message}")
                continue
            }

            val behaviorTrack = behaviorTrackingRepository.getMemberBehavior(currentMember.id ?: "").getOrNull()


            if (behaviorTrack?.totalOutstandingAmount?.isPositive == true) {
                results.add("❌ Skipped ${currentMember.fullName}: Has arrears")
                continue
            }
            val currentMonth = currentItem.payoutDate.substring(0, 7)
            val activeLoans = activeLoansByMember[currentMember.id ?: ""] ?: emptyList()
            val subgroup = schedule.subgroups.find { it.memberIds.contains(currentMember.id) } ?: continue
            val actualCollectedPot = allContributions
                .filter { it.memberId in subgroup.memberIds && it.dueDate.startsWith(currentMonth) && it.status == ContributionStatus.PAID }
                .fold(Money.ZERO) { acc, c -> acc + c.amount }

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
                        put("amount", payAmount.amount.toPlainString())
                    }
                    remainingPot -= payAmount
                }
            }

            // Final High-Integrity Guard Check
            val finalGuard = payoutGuard.validateRequest(group, currentMember, remainingPot, PayoutType.ROTATION)
            if (finalGuard.isFailure) {
                results.add("❌ Skipped ${currentMember.fullName}: ${finalGuard.exceptionOrNull()?.message}")
                continue
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
                platformRepository.logAuditEvent(AuditLog(actorId = "system_rosca", targetGroupId = groupId, targetMemberId = currentMember.id, action = "ROSCA_PAYOUT_EXECUTED", details = buildJsonObject { put("payoutId", payoutId); put("totalPot", actualCollectedPot.amount.toPlainString()); put("period", currentMonth) }))

                // AI Note: Notify all members of the successful ROSCA payout.
                EventBus.emit(PayoutProcessedEvent(
                    payout = PayoutRequest(
                        id = payoutId,
                        groupId = groupId,
                        memberId = currentMember.id ?: "",
                        amount = remainingPot,
                        status = PayoutStatus.COMPLETED,
                        bankName = currentMember.bankName ?: "",
                        accountNo = currentMember.accountNo ?: "",
                        branchCode = currentMember.branchCode ?: ""
                    ),
                    status = PayoutStatus.COMPLETED
                ))
            }.getOrThrow()
            results.add("✅ Created payout for ${currentMember.fullName} (Net: ${remainingPot.format()})")
        }
        results.joinToString("\n")
    }
}
