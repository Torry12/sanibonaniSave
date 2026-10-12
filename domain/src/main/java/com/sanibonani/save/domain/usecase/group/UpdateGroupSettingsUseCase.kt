package com.sanibonani.save.domain.usecase.group
import com.sanibonani.save.domain.model.GroupSettings
import com.sanibonani.save.domain.model.UserRole
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import javax.inject.Inject
/**
 * Encapsulates the logic for updating group configuration settings.
 * Handles mapping from the UI [GroupSettings] model to the repository's data map.
 */
class UpdateGroupSettingsUseCase @Inject constructor(
    private val groupRepo: GroupRepository,
    private val supabaseRepo: SupabaseRepository
) {
    suspend operator fun invoke(groupId: String, settings: GroupSettings): Result<Unit> {
        // 1. Fetch current group to check existing banking details
        val currentGroup = groupRepo.getGroupById(groupId).getOrElse {
            return Result.failure(it)
        }

        // 2. Fraud Prevention Rule:
        // A group can set bank details ONCE. Subsequent updates require Platform Admin.
        val hasExistingBankDetails = !currentGroup.bankName.isNullOrBlank() &&
                                     !currentGroup.accountNumber.isNullOrBlank()

        val isChangingBankDetails = currentGroup.bankName != settings.bankName ||
                                     currentGroup.accountNumber != settings.accountNumber ||
                                     currentGroup.branchCode != settings.branchCode

        if (hasExistingBankDetails && isChangingBankDetails) {
            val userRole = runCatching { supabaseRepo.getUserRole() }.getOrDefault(UserRole.MEMBER)
            if (userRole != UserRole.PLATFORM_ADMIN) {
                return Result.failure(
                    SecurityException("FRAUD_PREVENTION: Bank account details are locked. Please contact Sanibonani Support to change your group's banking information.")
                )
            }
        }

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
            "burial_claim_amount" to (settings.burialClaimAmount.toDoubleOrNull() ?: 10000.0),
            "min_members_for_claims" to (settings.minMembersForClaims.toIntOrNull() ?: 10),
            "min_reserve_percentage" to (settings.minReservePercentage.toDoubleOrNull() ?: 20.0),
            "goal_amount" to (settings.goalAmount.toDoubleOrNull() ?: 10000.0),
            "period_months" to (settings.periodMonths.toIntOrNull() ?: 12),
            "loan_interest_rate" to (settings.loanInterestRate.toDoubleOrNull() ?: 5.0),
            "loan_max_amount" to (settings.loanMaxAmount.toDoubleOrNull() ?: 5000.0),
            "loan_max_months" to (settings.loanMaxMonths.toIntOrNull() ?: 6),
            // ROSCA — stored as lowercase snake_case to satisfy DB CHECK constraint
            "rosca_rotation_method" to settings.rotationMethod.name.lowercase()
        )
        return groupRepo.updateGroupSettings(groupId, updates)
    }
}
