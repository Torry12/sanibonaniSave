package com.sanibonani.save.domain.service

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
        /** High-value disbursement threshold requiring AAL2 verification (R10,000.00). */
        private val MFA_THRESHOLD = Money(10000.00)
    }

    /**
     * Asserts that Multi-Factor Authentication (AAL2) is active if the amount exceeds [MFA_THRESHOLD].
     */
    suspend fun assertMfaRequirement(amount: Money): Result<Unit> {
        if (amount >= MFA_THRESHOLD) {
            if (!supabaseRepository.isMfaVerified) {
                return Result.failure(Exception("MFA_REQUIRED: High-value disbursement of ${amount.format()} requires AAL2 verification."))
            }
        }
        return Result.success(Unit)
    }

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
