package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.PlatformConfigRepository
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authoritative service for platform-level financial rules and fee calculations.
 */
@Singleton
class PlatformFeeService @Inject constructor(
    private val configRepo: PlatformConfigRepository
) {
    /**
     * Calculates the total monthly fee the group owes to the platform.
     * Rule: [Monthly Member Fee] * [Current Active Members]
     */
    fun calculateMonthlyGroupTotal(group: Group): Money {
        val config = configRepo.current()
        return config.monthlyMemberFee * group.currentMembers
    }

    /**
     * Calculates the portion of a member's monthly payment that belongs to the platform.
     */
    fun calculateMonthlyMemberIndividual(group: Group, member: Member): Money {
        val config = configRepo.current()
        // Standard rule: every member pays the same base platform fee.
        return config.monthlyMemberFee
    }

    /**
     * Determines the next fee status for a group based on how many days/months are unpaid.
     */
    fun evaluateGroupStanding(currentStatus: AdminFeeState, daysOverdue: Int): AdminFeeState {
        val config = configRepo.current()
        return when {
            daysOverdue >= config.autoSuspensionDays -> AdminFeeState.SUSPENDED
            daysOverdue >= 1 -> AdminFeeState.WARNING
            daysOverdue == 0 -> AdminFeeState.DUE
            else -> AdminFeeState.PAID
        }
    }

    /**
     * Business Rule: High-value disbursements require enhanced security.
     */
    fun isMfaRequiredForDisbursement(amount: Money): Boolean {
        // Hardcoded threshold of R10,000.00 for security hardening
        return amount >= Money(10000.0)
    }

    /**
     * Returns the registration fee for a new group.
     */
    fun getRegistrationFee(): Money {
        return configRepo.current().registrationFee
    }

    /**
     * Returns the shortfall recovery fee applied when a member catches up on arrears.
     */
    fun getShortfallRecoveryFee(): Money {
        return configRepo.current().shortfallRecoveryFee
    }
}
