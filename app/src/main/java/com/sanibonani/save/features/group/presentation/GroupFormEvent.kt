package com.sanibonani.save.features.group.presentation

import com.sanibonani.save.domain.model.Money

sealed class GroupFormEvent {
    data class NameChanged(val name: String) : GroupFormEvent()
    data class TypeChanged(val type: com.sanibonani.save.domain.model.GroupType) : GroupFormEvent()
    data class ContributionChanged(val amount: String) : GroupFormEvent()
    data class JoiningFeeChanged(val amount: String) : GroupFormEvent()
    data class MaxMembersChanged(val count: String) : GroupFormEvent()
    data class ProvinceChanged(val province: String) : GroupFormEvent()
    data object Submit : GroupFormEvent()
}
