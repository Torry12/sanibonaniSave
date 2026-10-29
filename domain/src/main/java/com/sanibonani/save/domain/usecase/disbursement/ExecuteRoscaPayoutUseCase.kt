package com.sanibonani.save.domain.usecase.disbursement
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.repository.member.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.service.PayoutGuard
import com.sanibonani.save.domain.logging.AppLogger
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
    private val auditRepository: AuditRepository,
    private val roscaRotationUseCase: CalculateRoscaRotationUseCase,
    private val payoutGuard: PayoutGuard
) {
    private val TAG = "ExecuteRoscaPayout"

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
        val loanLedger = loanRepository.getGroupLoansSync(groupId)
        val activeLoansByMember = loanLedger.getOrNull()
            ?.filter { it.status == LoanStatus.ACTIVE || it.status == LoanStatus.PARTIALLY_PAID }
            ?.groupBy { it.memberId }
            ?: emptyMap()
        // An unreadable ledger puts every payoutee on hold: a missing row cannot be told apart
        // from a member who genuinely has no loans.
        val loanLedgerUnreadable = loanLedger.isFailure
        if (loanLedgerUnreadable) {
            AppLogger.e(TAG, "Loan ledger unreadable for group $groupId, no payout executed this rotation. cause=${loanLedger.exceptionOrNull()?.message}")
        }
        // The rotation runs on a daily schedule and again from the admin screen, so the queue of
        // unsettled payouts is what stops one period being paid out several times over. A failed
        // read is not an empty queue, so it is passed through as null and holds every payoutee.
        val payoutQueue = payoutRepository.getPendingPayouts().getOrNull()
        if (payoutQueue == null) {
            AppLogger.e(TAG, "Payout queue unreadable for group $groupId, no payout executed this rotation.")
        }
        val schedule = roscaRotationUseCase(group, members).getOrThrow()
        val currentItems = schedule.subgroups.flatMap { sub -> sub.items.filter { it.isCurrent } }
        if (currentItems.isEmpty()) throw Exception("No current members")
        val results = mutableListOf<String>()
        for (currentItem in currentItems) {
            val currentMember = members.find { it.id == currentItem.memberId } ?: continue
            val period = currentItem.payoutDate.take(7)
            val skipReason = RoscaPayoutPlanner.preflight(
                groupId = groupId,
                period = period,
                member = currentMember,
                loanLedgerUnreadable = loanLedgerUnreadable,
                payoutQueue = payoutQueue
            )
            if (skipReason != null) {
                results.add("❌ Skipped ${currentMember.fullName}: $skipReason")
                continue
            }
            // Preflight has already established that these are present and valid.
            val bankName = currentMember.bankName.orEmpty()
            val accountNo = currentMember.accountNo.orEmpty()
            val branchCode = currentMember.branchCode.orEmpty()

            // 1. Initial Member Eligibility Guard
            val individualGuard = payoutGuard.validateRequest(group, currentMember, Money.ZERO, PayoutType.ROTATION)
            if (individualGuard.isFailure) {
                results.add("❌ Skipped ${currentMember.fullName}: ${individualGuard.exceptionOrNull()?.message}")
                continue
            }

            val decision = RoscaPayoutPlanner.plan(
                period = period,
                member = currentMember,
                subgroup = schedule.subgroups.find { it.memberIds.contains(currentMember.id) },
                contributions = allContributions,
                activeLoans = activeLoansByMember[currentMember.id ?: ""] ?: emptyList(),
                behavior = behaviorTrackingRepository.getMemberBehavior(currentMember.id ?: "").getOrNull()
            )
            if (decision is RoscaPayoutDecision.Skip) {
                results.add("❌ Skipped ${currentMember.fullName}: ${decision.reason}")
                continue
            }
            val plan = (decision as RoscaPayoutDecision.Pay).plan

            // Final High-Integrity Guard Check
            val finalGuard = payoutGuard.validateRequest(group, currentMember, plan.netPayout, PayoutType.ROTATION)
            if (finalGuard.isFailure) {
                results.add("❌ Skipped ${currentMember.fullName}: ${finalGuard.exceptionOrNull()?.message}")
                continue
            }

            payoutRepository.processRoscaPayout(
                groupId = groupId,
                memberId = currentMember.id ?: "",
                totalPot = plan.totalPot,
                loanDeductions = buildJsonArray {
                    for (deduction in plan.deductions) {
                        addJsonObject {
                            put("loan_id", deduction.loanId)
                            put("amount", deduction.amount.amount.toPlainString())
                        }
                    }
                },
                payoutAmount = plan.netPayout,
                payoutData = buildJsonObject {
                    put("bank_name", bankName)
                    put("account_no", accountNo)
                    put("branch_code", branchCode)
                },
                period = plan.period
            ).onSuccess { payoutId ->
                // The server answers with no payout id when it created no payouts row: the offsets
                // absorbed the whole pot, or this period was already executed. Announce and audit
                // only a request that exists - a COMPLETED event tells the member funds are on
                // their way, and an initiation line without an id cannot be reconciled.
                if (payoutId != null) {
                    auditRepository.logAuditEvent(AuditLog(actorId = "system_rosca", targetGroupId = groupId, targetMemberId = currentMember.id, action = "ROSCA_PAYOUT_INITIATED", details = buildJsonObject { put("payoutId", payoutId); put("totalPot", plan.totalPot.amount.toPlainString()); put("period", plan.period) }))
                    EventBus.emit(PayoutProcessedEvent(
                        payout = PayoutRequest(
                            id = payoutId,
                            groupId = groupId,
                            memberId = currentMember.id ?: "",
                            amount = plan.netPayout,
                            status = PayoutStatus.PENDING,
                            type = PayoutType.ROTATION,
                            bankName = bankName,
                            accountNo = accountNo,
                            branchCode = branchCode
                        ),
                        status = PayoutStatus.PENDING
                    ))
                    results.add("✅ Created payout for ${currentMember.fullName} (Net: ${plan.netPayout.format()})")
                } else {
                    results.add("ℹ️ No payout for ${currentMember.fullName}: this period was already executed or the pot was fully offset.")
                }
            }.getOrThrow()
        }
        results.joinToString("\n")
    }
}
