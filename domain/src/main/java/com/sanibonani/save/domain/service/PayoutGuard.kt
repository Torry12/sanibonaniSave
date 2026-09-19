package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

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
    private val treasuryService: TreasuryService
) : PayoutGuard {

    override suspend fun validateRequest(
        group: Group,
        member: Member?,
        amount: Money,
        type: PayoutType
    ): Result<Unit> = runCatching {
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
            if (m.status == MemberStatus.PROBATION && isProbationRestricted(group.type, type)) {
                val joinedAt = m.joinedAt ?: m.createdAt ?: LocalDate.now().toString()
                val joinedDate = LocalDate.parse(joinedAt.substringBefore("T"))
                val monthsIn = ChronoUnit.MONTHS.between(joinedDate, LocalDate.now())

                if (monthsIn < group.probationMonths) {
                    throw MemberNotEligibleException(
                        m.id ?: "",
                        "Member is still in probation (${group.probationMonths} months required, $monthsIn months completed)"
                    )
                }
            }
        }

        // 3. Solvency Check (Liquidity)
        val balance = treasuryService.getTreasuryBalance(group.id ?: "").getOrThrow()
        if (amount > balance) {
            throw InsufficientFundsException(amount, balance)
        }

        // 4. Reserve Adequacy Check
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

    private fun isProbationRestricted(groupType: GroupType, payoutType: PayoutType): Boolean {
        // ROSCA payouts are scheduled, usually not probation-restricted once cycle starts
        // Burial claims often have 6-month probation
        // Stokvel annual payouts happen at end of cycle
        return when (groupType) {
            GroupType.BURIAL_SOCIETY -> payoutType == PayoutType.CLAIM
            GroupType.STOKVEL -> payoutType == PayoutType.STANDARD || payoutType == PayoutType.EMERGENCY
            else -> false
        }
    }
}
