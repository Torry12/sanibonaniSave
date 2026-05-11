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
        val updates = mutableMapOf<String, Any>(
            "joining_fee" to (settings.joiningFee.toDoubleOrNull() ?: 0.0),
            "monthly_contribution" to (settings.monthlyContribution.toDoubleOrNull() ?: 0.0),
            "late_fee" to (settings.lateFee.toDoubleOrNull() ?: 0.0),
            "late_fee_grace_days" to (settings.lateFeeGraceDays.toIntOrNull() ?: 0),
            "probation_months" to (settings.probationMonths.toIntOrNull() ?: 3),
            "payment_due_day" to (settings.paymentDueDay.toIntOrNull() ?: 28),
            "max_members" to (settings.maxMembers.toIntOrNull() ?: 10),
            "allow_partial_payment" to settings.allowPartialPayment,
            "auto_suspend_after" to (settings.autoSuspendAfter.toIntOrNull() ?: 2),
            "bank_name" to settings.bankName,
            "account_number" to settings.accountNumber,
            "branch_code" to settings.branchCode,
            "account_type" to settings.accountType,
            "max_beneficiaries" to (settings.maxBeneficiaries.toIntOrNull() ?: 0),
            "beneficiary_increase_pct" to (settings.beneficiaryIncreasePct.toDoubleOrNull() ?: 0.0),
            "goal_amount" to (settings.goalAmount.toDoubleOrNull() ?: 10000.0),
            "period_months" to (settings.periodMonths.toIntOrNull() ?: 12),
            "loan_interest_rate" to (settings.loanInterestRate.toDoubleOrNull() ?: 0.0),
            "loan_max_amount" to (settings.loanMaxAmount.toDoubleOrNull() ?: 0.0),
            "loan_max_months" to (settings.loanMaxMonths.toIntOrNull() ?: 0)
        )
        
        return groupRepo.updateGroupSettings(groupId, updates)
    }
}
