package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.GroupSettings
import com.sanibonani.save.domain.repository.GroupRepository
import javax.inject.Inject

/**
 * Encapsulates the logic for updating group configuration settings.
 * Handles mapping from the UI [GroupSettings] model to the repository's data map.
 */
class UpdateGroupSettingsUseCase @Inject constructor(
    private val groupRepo: GroupRepository
) {
    suspend operator fun invoke(groupId: String, settings: GroupSettings): Result<Unit> {
        // ── Business rule validation ───────────────────────────────────────────
        val paymentDueDay = settings.paymentDueDay.toIntOrNull() ?: 28
        if (paymentDueDay !in 1..28) {
            return Result.failure(IllegalArgumentException("Payment due day must be between 1 and 28."))
        }

        val monthlyContrib = settings.monthlyContribution.toDoubleOrNull() ?: 0.0
        if (monthlyContrib < 0) {
            return Result.failure(IllegalArgumentException("Monthly contribution cannot be negative."))
        }

        val probationMonths = settings.probationMonths.toIntOrNull() ?: 3
        if (probationMonths < 0) {
            return Result.failure(IllegalArgumentException("Probation period cannot be negative."))
        }

        val autoSuspendAfter = settings.autoSuspendAfter.toIntOrNull() ?: 2
        if (autoSuspendAfter < 1) {
            return Result.failure(IllegalArgumentException("Auto-suspend threshold must be at least 1 missed payment."))
        }

        val maxMembers = settings.maxMembers.toIntOrNull() ?: 10
        if (maxMembers < 1) {
            return Result.failure(IllegalArgumentException("Maximum members must be at least 1."))
        }

        val loanInterestRate = settings.loanInterestRate.toDoubleOrNull() ?: 0.0
        if (loanInterestRate < 0 || loanInterestRate > 100) {
            return Result.failure(IllegalArgumentException("Loan interest rate must be between 0% and 100%."))
        }

        val updates = mutableMapOf<String, Any>(
            "joining_fee" to (settings.joiningFee.toDoubleOrNull() ?: 0.0),
            "monthly_contribution" to monthlyContrib,
            "late_fee" to (settings.lateFee.toDoubleOrNull() ?: 0.0),
            "late_fee_grace_days" to (settings.lateFeeGraceDays.toIntOrNull() ?: 0),
            "probation_months" to probationMonths,
            "payment_due_day" to paymentDueDay,
            "max_members" to maxMembers,
            "allow_partial_payment" to settings.allowPartialPayment,
            "auto_suspend_after" to autoSuspendAfter,
            "bank_name" to settings.bankName,
            "account_number" to settings.accountNumber,
            "branch_code" to settings.branchCode,
            "account_type" to settings.accountType,
            "max_beneficiaries" to (settings.maxBeneficiaries.toIntOrNull() ?: 0),
            "beneficiary_increase_pct" to (settings.beneficiaryIncreasePct.toDoubleOrNull() ?: 0.0),
            "goal_amount" to (settings.goalAmount.toDoubleOrNull() ?: 10000.0),
            "period_months" to (settings.periodMonths.toIntOrNull() ?: 12),
            "loan_interest_rate" to loanInterestRate,
            "loan_max_amount" to (settings.loanMaxAmount.toDoubleOrNull() ?: 0.0),
            "loan_max_months" to (settings.loanMaxMonths.toIntOrNull() ?: 0)
        )

        return groupRepo.updateGroupSettings(groupId, updates)
    }
}
