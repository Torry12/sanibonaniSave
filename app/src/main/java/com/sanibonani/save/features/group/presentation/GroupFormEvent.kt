package com.sanibonani.save.features.group.presentation

import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.RoscaRotationMethod

sealed class GroupFormEvent {
    data class NameChanged(val name: String) : GroupFormEvent()
    data class TypeSelected(val type: GroupType) : GroupFormEvent()
    data class ProvinceSelected(val province: String) : GroupFormEvent()
    data class CityChanged(val city: String) : GroupFormEvent()
    data class TownshipChanged(val township: String) : GroupFormEvent()
    data class StreetChanged(val street: String) : GroupFormEvent()
    data class DescriptionChanged(val description: String) : GroupFormEvent()
    data class JoiningFeeChanged(val value: String) : GroupFormEvent()
    data class MonthlyContributionChanged(val value: String) : GroupFormEvent()
    data class LateFeeChanged(val value: String) : GroupFormEvent()
    data class MaxMembersChanged(val value: String) : GroupFormEvent()
    data class AdminEmailChanged(val email: String) : GroupFormEvent()
    data class AdminFullNameChanged(val name: String) : GroupFormEvent()
    data class AdminPhoneChanged(val phone: String) : GroupFormEvent()
    data class AdminIdNumberChanged(val id: String) : GroupFormEvent()
    data class AdminPasswordChanged(val password: String) : GroupFormEvent()
    data class BankNameSelected(val bank: String) : GroupFormEvent()
    data class AccountNumberChanged(val value: String) : GroupFormEvent()
    data class BranchCodeChanged(val value: String) : GroupFormEvent()
    data class RoscaRotationMethodSelected(val method: RoscaRotationMethod) : GroupFormEvent()
    data class TermsAcceptedToggled(val accepted: Boolean) : GroupFormEvent()
    data class AddressQueryChanged(val query: String) : GroupFormEvent()
    data class AddressSelected(val address: com.sanibonani.save.domain.service.LocationAddress) : GroupFormEvent()
    data class CoordinatesPicked(val lat: Double, val lon: Double) : GroupFormEvent()
    /**
     * Device location resolved by the GPS button. Unlike [CoordinatesPicked] followed by
     * [CityChanged], this carries the resolved place name without discarding the
     * coordinates that [CityChanged] intentionally clears.
     */
    data class GpsLocationPicked(val lat: Double, val lon: Double, val placeName: String?) : GroupFormEvent()
    data class RoscaCycleChanged(val months: Int) : GroupFormEvent()
    data class LateFeeGraceDaysChanged(val value: String) : GroupFormEvent()
    data class ProbationMonthsChanged(val value: String) : GroupFormEvent()
    data class PaymentDueDayChanged(val value: String) : GroupFormEvent()
    data class GoalAmountChanged(val value: String) : GroupFormEvent()
    data class PeriodMonthsChanged(val value: String) : GroupFormEvent()
    data class MaxBeneficiariesChanged(val value: String) : GroupFormEvent()
    data class BeneficiaryIncreasePctChanged(val value: String) : GroupFormEvent()
    data class AllowPartialPaymentToggled(val allow: Boolean) : GroupFormEvent()
    data class UseStandardConstitutionToggled(val use: Boolean) : GroupFormEvent()
    data class LogoEmojiSelected(val emoji: String) : GroupFormEvent()
    data object NextStep : GroupFormEvent()
    data object PrevStep : GroupFormEvent()
    data object Submit : GroupFormEvent()
    data object DismissPayment : GroupFormEvent()
}
