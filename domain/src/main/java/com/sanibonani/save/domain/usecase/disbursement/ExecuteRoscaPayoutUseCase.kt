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
import com.sanibonani.save.domain.validation.InputValidator
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
    private val platformRepository: PlatformRepository,
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
        // The rotation runs on a daily schedule and again from the admin screen, so the queue of
        // unsettled payouts is what stops one period being paid out several times over.
        val payoutQueue = payoutRepository.getPendingPayouts()
        // An unreadable ledger puts every payoutee on hold: a missing row cannot be told apart
        // from a member who genuinely has no loans.
        val loanLedgerUnreadable = loanLedger.isFailure
        if (loanLedgerUnreadable) {
            AppLogger.e(TAG, "Loan ledger unreadable for group $groupId, no payout executed this rotation. cause=${loanLedger.exceptionOrNull()?.message}")
        }
        val payoutQueueUnreadable = payoutQueue.isFailure
        if (payoutQueueUnreadable) {
            AppLogger.e(TAG, "Payout queue unreadable for group $groupId, no payout executed this rotation. cause=${payoutQueue.exceptionOrNull()?.message}")
        }
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
            val bankName = currentMember.bankName
            val accountNo = currentMember.accountNo
            if (bankName.isNullOrBlank() || accountNo.isNullOrBlank()) {
                results.add("❌ Skipped ${currentMember.fullName}: No banking info")
                continue
            }
            val currentMonth = currentItem.payoutDate.substring(0, 7)
            // Money addressed to a malformed account never arrives, and the rotation would still
            // count the period as paid. InputValidator is the single source of truth for that shape.
            val accountCheck = InputValidator.validateAccountNumber(accountNo, bankName)
            if (!accountCheck.isValid()) {
                results.add("❌ Skipped ${currentMember.fullName}: ${accountCheck.getErrorMessage()}")
                continue
            }
            val branchCheck = InputValidator.validateBranchCode(currentMember.branchCode.orEmpty())
            if (!branchCheck.isValid()) {
                results.add("❌ Skipped ${currentMember.fullName}: ${branchCheck.getErrorMessage()}")
                continue
            }
            // Paying the gross pot would hand over money already earmarked for loans this
            // rotation could not read. The next rotation retries with a readable ledger.
            if (loanLedgerUnreadable) {
                results.add("❌ Skipped ${currentMember.fullName}: Outstanding loans could not be read")
                continue
            }
            if (payoutQueueUnreadable) {
                results.add("❌ Skipped ${currentMember.fullName}: Outstanding payouts could not be read")
                continue
            }
            // Deliberately not narrowed to the rotation types: any unsettled request addressed to
            // this member in this period holds the rotation. A held rotation retries on the next
            // run; a second payout is money out of the treasury.
            val alreadyRequested = payoutQueue.getOrNull()?.any { request ->
                request.groupId == groupId && request.memberId == currentMember.id &&
                    (request.createdAt == null || request.createdAt.startsWith(currentMonth))
            } == true
            if (alreadyRequested) {
                results.add("❌ Skipped ${currentMember.fullName}: A payout for $currentMonth is already outstanding")
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
                },
                period = currentMonth
            ).onSuccess { payoutId ->
                // The server answers with no payout id when it created no payouts row: the offsets
                // absorbed the whole pot, or this period was already executed. Announce and audit
                // only a request that exists - a COMPLETED event tells the member funds are on
                // their way, and an initiation line without an id cannot be reconciled.
                if (payoutId != null) {
                    platformRepository.logAuditEvent(AuditLog(actorId = "system_rosca", targetGroupId = groupId, targetMemberId = currentMember.id, action = "ROSCA_PAYOUT_INITIATED", details = buildJsonObject { put("payoutId", payoutId); put("totalPot", actualCollectedPot.amount.toPlainString()); put("period", currentMonth) }))
                    EventBus.emit(PayoutProcessedEvent(
                        payout = PayoutRequest(
                            id = payoutId,
                            groupId = groupId,
                            memberId = currentMember.id ?: "",
                            amount = remainingPot,
                            status = PayoutStatus.PENDING,
                            type = PayoutType.ROTATION,
                            bankName = bankName,
                            accountNo = accountNo,
                            branchCode = currentMember.branchCode ?: ""
                        ),
                        status = PayoutStatus.PENDING
                    ))
                    results.add("✅ Created payout for ${currentMember.fullName} (Net: ${remainingPot.format()})")
                } else {
                    results.add("ℹ️ No payout for ${currentMember.fullName}: this period was already executed or the pot was fully offset.")
                }
            }.getOrThrow()
        }
        results.joinToString("\n")
    }
}
