package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Money

/**
 * Service for enforcing regulatory limits and compliance constraints.
 */
interface ComplianceService {
    /**
     * Validates if a group can accept new members based on capacity limits.
     */
    fun validateGroupCapacity(group: Group, newMemberCount: Int = 1): Result<Unit>

    /**
     * Validates payout exposure limit for a given transaction amount.
     */
    fun validatePayoutExposure(amount: Money): Result<Unit>

    /**
     * Evaluates a transaction against regulatory and platform-wide compliance rules.
     */
    fun validateTransaction(transaction: com.sanibonani.save.domain.model.Transaction): Result<Unit>
}
