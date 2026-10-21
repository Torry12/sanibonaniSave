package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.DualControlViolationException
import com.sanibonani.save.domain.model.MfaRequiredException
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.equity.GroupFinancialRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain service for centralizing high-integrity financial guards.
 */
@Singleton
class FinancialSecurityService @Inject constructor(
    private val supabaseRepository: SupabaseRepository,
    private val financialRepository: GroupFinancialRepository
) {
    companion object {
        /**
         * Single source of truth for the high-value AAL2 threshold (R10,000.00).
         * Callers and UI must read this rather than hardcoding an amount.
         */
        val MFA_THRESHOLD = Money("10000.00")

        /**
         * Single source of truth for the maker-checker floor (R5,000.00): above this a payout must
         * be completed by someone other than the administrator who put it into processing.
         */
        val DUAL_CONTROL_THRESHOLD = Money("5000.00")
    }

    /**
     * Asserts that Multi-Factor Authentication (AAL2) is active if the amount exceeds [MFA_THRESHOLD].
     */
    suspend fun assertMfaRequirement(amount: Money): Result<Unit> {
        if (amount >= MFA_THRESHOLD) {
            if (!supabaseRepository.isMfaVerified) {
                return Result.failure(MfaRequiredException(amount))
            }
        }
        return Result.success(Unit)
    }

    /**
     * Blocks [actingAdminId] from completing a payout they already handled. A payout nobody has
     * handled yet ([processorId] null) has no maker to check against, so it passes.
     */
    fun assertDualControl(amount: Money, actingAdminId: String, processorId: String?): Result<Unit> =
        if (amount >= DUAL_CONTROL_THRESHOLD && processorId != null && processorId == actingAdminId)
            Result.failure(DualControlViolationException(amount))
        else
            Result.success(Unit)

    /**
     * Validates that a group has sufficient liquid balance to cover a pending disbursement.
     */
    suspend fun validateLiquidity(groupId: String, requiredAmount: Money): Result<Unit> {
        val currentBalance = financialRepository.getVerifiedBalance(groupId).getOrElse { return Result.failure(it) }
        if (currentBalance < requiredAmount) {
            return Result.failure(Exception("INSUFFICIENT_LIQUIDITY: Group balance (${currentBalance.format()}) is below the required amount (${requiredAmount.format()})."))
        }
        return Result.success(Unit)
    }
}
