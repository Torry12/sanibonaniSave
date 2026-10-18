package com.sanibonani.save.data.service

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.Transaction
import com.sanibonani.save.domain.model.TransactionType
import com.sanibonani.save.domain.service.ComplianceService
import javax.inject.Inject

class ComplianceServiceImpl @Inject constructor() : ComplianceService {

    override fun validateGroupCapacity(group: Group, newMemberCount: Int): Result<Unit> {
        if (group.maxMembers > 0 && group.currentMembers + newMemberCount > group.maxMembers) {
            return Result.failure(Exception("Group capacity limit reached (${group.maxMembers} members max)."))
        }
        return Result.success(Unit)
    }

    override fun validatePayoutExposure(amount: Money): Result<Unit> {
        val maxExposure = Money("100000.00")
        if (amount > maxExposure) {
            return Result.failure(Exception("Payout exceeds maximum allowed exposure limit."))
        }
        return Result.success(Unit)
    }

    override fun validateTransaction(transaction: Transaction): Result<Unit> {
        // High Integrity: Enforce max contribution limits (e.g. R50k single contribution)
        val maxSingleContributionCents = 5000000L
        if (transaction.type == TransactionType.CONTRIBUTION && transaction.amount > maxSingleContributionCents) {
            return Result.failure(Exception("COMPLIANCE_ERROR: Single contribution exceeds regulatory limit (${Money("50000.0").format()})."))
        }
        return Result.success(Unit)
    }
}
