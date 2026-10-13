package com.sanibonani.save.viewmodel.state.admin

import com.sanibonani.save.domain.model.GroupSettings

data class AdminSettingsState(
    val settings: GroupSettings = GroupSettings(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)
