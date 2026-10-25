package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.PlatformConfigRepository
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
     * Calculates the total monthly platform fee the group owes to the platform.
     */
    fun calculateMonthlyGroupTotal(group: Group): Money {
        val config = configRepo.current()
        return config.monthlyMemberFee * group.currentMembers
    }

    /**
     * Calculates the portion of a member's monthly payment that belongs to the platform (individual member platform fee).
     */
    fun calculateMonthlyMemberIndividual(group: Group, member: Member): Money {
        val config = configRepo.current()
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
     * Returns the registration fee for a group, prioritizing group-specific custom fees over global config.
     */
    fun getRegistrationFee(group: Group? = null): Money {
        if (group != null && group.joiningFee.isPositive) {
            return group.joiningFee
        }
        return configRepo.current().registrationFee
    }

    /**
     * Returns the shortfall recovery fee applied when a member catches up on arrears.
     */
    fun getShortfallRecoveryFee(group: Group? = null): Money {
        return configRepo.current().shortfallRecoveryFee
    }
}
