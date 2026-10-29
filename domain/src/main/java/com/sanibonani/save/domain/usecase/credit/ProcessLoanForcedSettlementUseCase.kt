package com.sanibonani.save.domain.usecase.credit
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanStatus
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.repository.system.NotificationRepository
import com.sanibonani.save.domain.utils.PaymentCalculator
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.utils.safeRunCatching
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import com.sanibonani.save.domain.usecase.financialResult
import com.sanibonani.save.domain.usecase.firstValueOrNull

/**
 * Handles the automatic extraction of remaining loan balances after the 3-month maximum term.
 */
class ProcessLoanForcedSettlementUseCase @Inject constructor(
    private val loanRepository: LoanRepository,
    private val memberRepository: MemberRepository,
    private val contributionRepository: ContributionRepository,
    private val platformRepository: PlatformRepository,
    private val notificationRepository: NotificationRepository,
    private val paymentCalculator: PaymentCalculator
) {
    suspend operator fun invoke(): Result<Unit> = financialResult {
        val groups = platformRepository.getAllGroups().getOrThrow()
        for (group in groups) {
            val loans = loanRepository.getGroupLoans(group.id ?: "").firstValueOrNull() ?: continue
            val expiredLoans = loans.filter { loan ->
                (loan.status == LoanStatus.ACTIVE || loan.status == LoanStatus.PARTIALLY_PAID) &&
                isLoanTermExceeded(loan)
            }
            for (loan in expiredLoans) {
                // This sweep runs across every group and is not resumable, so a single rejected
                // RPC must not strand the settlements that come after it.
                safeRunCatching("$TAG.settleLoan") {
                    settleLoanFromContributions(loan, group)
                }.onFailure { e ->
                    AppLogger.w(TAG, "Forced settlement failed for loan ${loan.id}; continuing sweep.", e)
                }
            }
        }
    }
    private fun isLoanTermExceeded(loan: Loan): Boolean {
        val startDate = loan.startDate?.let { LocalDate.parse(it.substringBefore("T")) } ?: return false
        val monthsElapsed = ChronoUnit.MONTHS.between(startDate, LocalDate.now())
        return monthsElapsed >= 3
    }
    private suspend fun settleLoanFromContributions(loan: Loan, group: Group) {
        val loanId = requireNotNull(loan.id) { "Cannot settle a loan without a persisted ID." }
        val settlement = loanRepository.settleExpiredLoan(loanId).getOrThrow()
        val settledAmount = settlement.repayment.amount

        if (group.type == GroupType.BURIAL_SOCIETY) {
            handleBurialSocietyProbationReset(loan.memberId, group)
        }
        notificationRepository.sendNotification(
            AppNotification(
                groupId = loan.groupId,
                memberId = loan.memberId,
                message = "Loan term exceeded. Balance ${settledAmount.format()} settled from contributions.",
                channel = NotifChannel.BOTH,
                triggerEvent = NotifEvent.LOAN_DEFAULTED
            )
        )
    }
    private suspend fun handleBurialSocietyProbationReset(memberId: String, group: Group) {
        val updatedMember = memberRepository.getMemberById(memberId).getOrNull() ?: return
        // A history that never arrived contains no payments, and scoring that as arrears would put a
        // member who is up to date on probation. The sanction waits for a readable ledger instead.
        val contributions = contributionRepository
            .observeMemberContributions(memberId, group.id ?: "").firstValueOrNull() ?: return
        val loans = loanRepository.getMemberLoans(memberId).firstValueOrNull() ?: return
        val status = paymentCalculator.calculateStatus(group, updatedMember, contributions, loans, LocalDate.now())
        if (status.shortfall > (group.monthlyContribution * PROBATION_SHORTFALL_MONTHS)) {
            memberRepository.updateMemberStatus(memberId, MemberStatus.PROBATION)
        }
    }

    private companion object {
        const val TAG = "ForcedLoanSettlement"

        /** A burial-society member is returned to probation once they fall this many months behind. */
        const val PROBATION_SHORTFALL_MONTHS = 2
    }
}
