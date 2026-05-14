package com.sanibonani.save.viewmodel

import com.sanibonani.save.domain.model.DocumentStatus
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.RoscaRotationMethod

/**
 * Type-safe, compile-verified events for the group registration form.
 *
 * Design rationale:  Replaces the stringly-typed [GroupViewModel.updateField]
 * dispatcher so that mis-spelled field names and wrong value types are caught at
 * compile time rather than at runtime.
 *
 * Usage in Composables:
 * ```kotlin
 * vm.onEvent(GroupFormEvent.NameChanged("Ubuntu Stokvel"))
 * vm.onEvent(GroupFormEvent.TypeSelected(GroupType.STOKVEL))
 * ```
 */
sealed class GroupFormEvent {

    // ── Step 1 – Basic Info ───────────────────────────────────────────────
    data class NameChanged(val name: String)                   : GroupFormEvent()
    data class AdminEmailChanged(val email: String)            : GroupFormEvent()
    data class AdminPhoneChanged(val phone: String)            : GroupFormEvent()
    data class TypeSelected(val type: GroupType)               : GroupFormEvent()
    data class RoscaRotationMethodSelected(val method: RoscaRotationMethod) : GroupFormEvent()
    data class LogoEmojiSelected(val emoji: String)            : GroupFormEvent()

    // ── Step 2 – Location ─────────────────────────────────────────────────
    data class CityChanged(val city: String)                   : GroupFormEvent()
    data class TownshipChanged(val township: String)           : GroupFormEvent()
    data class ProvinceSelected(val province: String)          : GroupFormEvent()
    data class DescriptionChanged(val description: String)     : GroupFormEvent()

    // ── Step 3 – Rules & Fees ─────────────────────────────────────────────
    data class JoiningFeeChanged(val value: String)            : GroupFormEvent()
    data class MonthlyContributionChanged(val value: String)   : GroupFormEvent()
    data class LateFeeChanged(val value: String)               : GroupFormEvent()
    data class LateFeeGraceDaysChanged(val value: String)      : GroupFormEvent()
    data class ProbationMonthsChanged(val value: String)       : GroupFormEvent()
    data class PaymentDueDayChanged(val value: String)         : GroupFormEvent()
    data class MaxMembersChanged(val value: String)            : GroupFormEvent()
    data class GoalAmountChanged(val value: String)            : GroupFormEvent()
    data class PeriodMonthsChanged(val value: String)          : GroupFormEvent()
    data class MaxBeneficiariesChanged(val value: String)      : GroupFormEvent()
    data class BeneficiaryIncreasePctChanged(val value: String): GroupFormEvent()
    data class AllowPartialPaymentToggled(val allow: Boolean)  : GroupFormEvent()
    data class TermsAcceptedToggled(val accepted: Boolean)     : GroupFormEvent()

    // ── Step 4 – Banking ──────────────────────────────────────────────────
    data class BankNameSelected(val bank: String)              : GroupFormEvent()
    data class AccountNumberChanged(val value: String)         : GroupFormEvent()
    data class BranchCodeChanged(val value: String)            : GroupFormEvent()

    // ── Step 5 – Constitution ─────────────────────────────────────────────
    data class UseStandardConstitutionToggled(val use: Boolean): GroupFormEvent()
    data class ConstitutionStatusChanged(
        val url: String?,
        val status: DocumentStatus
    ) : GroupFormEvent()

    // ── Admin credentials (Step 6 / backward-compat) ─────────────────────
    data class AdminFullNameChanged(val name: String)          : GroupFormEvent()
    data class AdminIdNumberChanged(val id: String)            : GroupFormEvent()
    data class AdminPasswordChanged(val password: String)      : GroupFormEvent()

    // ── Navigation helpers ────────────────────────────────────────────────
    /** Dismiss the payment screen if the user backs out. */
    data object DismissPayment : GroupFormEvent()
}

