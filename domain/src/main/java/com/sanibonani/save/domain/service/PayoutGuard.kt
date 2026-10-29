package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.sanibonani.save.domain.utils.safeRunCatching

/**
 * Centralized service to enforce high-integrity guards on payouts.
 */
interface PayoutGuard {
    suspend fun validateRequest(
        group: Group,
        member: Member?,
        amount: Money,
        type: PayoutType
    ): Result<Unit>
}

@Singleton
class PayoutGuardImpl @Inject constructor(
    private val treasuryService: TreasuryService,
    private val policy: GroupFinancialPolicy
) : PayoutGuard {

    override suspend fun validateRequest(
        group: Group,
        member: Member?,
        amount: Money,
        type: PayoutType
    ): Result<Unit> = safeRunCatching {
        // 1. Group Status Check
        if (group.isPlatformSuspended) {
            throw GroupSuspendedException(group.id ?: "unknown")
        }

        // 2. Member Eligibility Check (if applicable)
        member?.let { m ->
            if (m.status == MemberStatus.SUSPENDED) {
                throw MemberNotEligibleException(m.id ?: "", "Member is suspended")
            }

            // Probation Check for specific group types and payout types
            if (m.status == MemberStatus.PROBATION && policy.isProbationRestricted(group.type, type)) {
                val joinedAt = m.joinedAt ?: m.createdAt ?: LocalDate.now().toString()
                val joinedDate = try {
                    LocalDate.parse(joinedAt.substringBefore("T").substringBefore(" "))
                } catch (_: Exception) {
                    LocalDate.now()
                }

                val monthsIn = ChronoUnit.MONTHS.between(joinedDate, LocalDate.now())

                if (monthsIn < group.probationMonths) {
                    val monthsRemaining = group.probationMonths - monthsIn
                    val message = if (group.type == GroupType.INVESTMENT_CLUB) {
                        "Investment Club lock-in period active. ${group.probationMonths} months total required ($monthsRemaining remaining)."
                    } else {
                        "Member is still in probation (${group.probationMonths} months required, $monthsIn months completed)"
                    }
                    throw MemberNotEligibleException(m.id ?: "", message)
                }
            }
        }


        // 3. Solvency Check (Liquidity)
        val balance = treasuryService.getTreasuryBalance(group.id ?: "").getOrThrow()
        if (amount > balance) {
            throw InsufficientFundsException(amount, balance)
        }

        // 4. Emergency Single-Withdrawal Cap
        // Half the live balance is the most one request may release, so an emergency withdrawal
        // cannot drain the fund even though the balance covers it. This is the "50% liquidity cap"
        // the member screen advertises; the balance check above only stops overdrafts.
        if (type == PayoutType.EMERGENCY) {
            val cap = balance.percent(50)
            if (amount > cap) {
                throw WithdrawalLimitExceededException(amount, cap)
            }
        }

        // 5. Reserve Adequacy Check
        if (type != PayoutType.EMERGENCY && type != PayoutType.CLAIM) {
            val reserve = treasuryService.calculateRequiredReserve(group.id ?: "").getOrThrow()
            if (balance - amount < reserve) {
                throw InsufficientFundsException(
                    amount,
                    balance - reserve // effectively available for non-emergency
                )
            }
        }
    }
}
